(ns peripheral.component
  (:require [com.stuartsierra.component :as component]
            [peripheral.utils :refer [is-class-name?]]))

;; ## Component Metadata

(defn set-started
  "Set started flag in metadata."
  [component]
  (vary-meta component assoc ::running true))

(defn set-stopped
  "Set stopped flag in metadata."
  [component]
  (vary-meta component dissoc ::running))

(defn running?
  "Check whether the component is running (per its metadata)."
  [component]
  (boolean (-> component meta ::running)))

;; ## Attach/Detach
;;
;; If a component A is attached to another component B that means it will be started
;; after B has been and stopped before B.

(defn- add-attach-key
  "Add attach dependencies to the given component's metadata."
  [component k dependencies]
  (vary-meta component assoc-in [::attach k]
             (if (map? dependencies)
               dependencies
               (into {} (map #(vector % %) dependencies)))))

(defn- remove-attach-key
  "Remove attach dependencies from the given component's metadata."
  [component k]
  (vary-meta component update-in [::attach] dissoc k))

(defn start-attached-components
  "Start all attached components."
  [component]
  (->> (for [[component-key dependencies] (-> component meta ::attach)]
         (when-let [c (get component component-key)]
           (let [component-with-dependencies (reduce
                                               (fn [c [assoc-key deps-key]]
                                                 (assoc c assoc-key (get component deps-key)))
                                               c dependencies)]
             (vector
               component-key
               (component/start component-with-dependencies)))))
       (into {})
       (merge component)))

(defn stop-attached-components
  "Stop all attached components."
  [component]
  (->> (for [[component-key dependencies] (-> component meta ::attach)]
         (when-let [c (get component component-key)]
           (let [stopped (component/stop c)]
             (vector
               component-key
               (reduce dissoc stopped (keys dependencies))))))
       (into {})
       (merge component)))

(defn attach
  "Attach the given component to this one (associng it using `k` and having it depend on `dependencies`)."
  ([component k attach-component]
   (attach component k attach-component nil))
  ([component k attach-component dependencies]
   (assert (not (contains? component k)) "there is already a component with that key attached.")
   (-> component
       (assoc k attach-component)
       (add-attach-key k dependencies))))

(defn detach
  "Detach the given component key from this component."
  [component k]
  (-> component
      (dissoc k)
      (remove-attach-key k)))

;; ## Helpers

(defmacro with-field-exception
  "Wrap field initialization/cleanup to produce more expressive exception."
  [field & body]
  `(try
     (do ~@body)
     (catch Throwable ex#
       (let [msg# (format ~(format "could not update field '%s' (%%s)" field) (.getMessage ex#))]
         (throw (Exception. msg# ex#))))))

(defn call-on-component
  "Call the given function on the given component iff `f` really is a function. Will return
   the unaltered component if `f` returns nil."
  [component f]
  (or
    (when (fn? f)
      (f component))
    component))

;; ## `defcomponent`

(defn- add-field
  "Add field to analysis map."
  [result-map field start stop]
  (update-in result-map [:fields]
             (comp vec conj)
             [field {:start start :stop stop}]))

(defn- finalize-analysis
  [result-map rest-seq]
  "Process everything that is left and add data to analysis map."
  (assoc result-map :specifics rest-seq))

(def ^:private namespace-dispatch
  "Allow for special keywords that are handled by their namespace."
  {"peripheral" #(assoc-in % [:lifecycle %2] %3)
   "component"  #(add-field % %2 `(component/start ~%3) `component/stop)})

(defn- analyze-component-logic
  "Create pair of a seq of fields (as a pair, associated with a map of start/stop logic), as well
   as a seq of component specifics."
  [logic-seq]
  (loop [sq logic-seq
         m {}]
    (if (empty? sq)
      (finalize-analysis m sq)
      (let [[[k a b] rst] (split-at 3 sq)]
        (if (keyword? k)
          (if-let [dispatch (namespace-dispatch (namespace k))]
            (recur (drop 2 sq) (dispatch m (-> k name keyword) a))
            (if (or (keyword? b) (is-class-name? b))
              (recur (drop 2 sq) (add-field m k a nil))
              (recur rst (add-field m k a b))))
          (finalize-analysis m sq))))))

(defn- create-init-form
  "Create component initialization form that sequentially sets the values of the component fields."
  [fields field-syms this]
  (let [fn-forms (map-indexed
                   (fn [i [field {:keys [start]}]]
                     `(fn [{:keys [~@(take i field-syms)] :as ~this}]
                        (with-field-exception ~field
                          (assoc ~this ~field ~start))))
                   fields)]
    `(reduce #(%2 %1) ~this [~@fn-forms])))

(defn- create-start-form
  "Create component startup form initializing the fields in order of definition."
  [fields {:keys [start started]} field-syms this]
  `(let [~this (call-on-component ~this ~start)]
     (-> ~(create-init-form fields field-syms this)
         (call-on-component ~started)
         (start-attached-components)
         (set-started))))

(defn- create-cleanup-form
  "Create component cleanup form that sequentially resets the values of the component fields to
   either nil or the result of a supplied cleanup function."
  [fields this]
  `(-> ~this
       ~@(for [[field {:keys [stop]}] (reverse fields)]
           (if stop
             `(update-in [~field] #(with-field-exception ~field (~stop %)))
             `(assoc ~field nil)))))

(defn- create-stop-form
  "Take a map of fields with start/stop logic and create the map to be used for
   cleanup."
  [fields {:keys [stop stopped]} this]
  `(let [~this (call-on-component ~this ~stop)]
     (-> ~(create-cleanup-form fields this)
         (call-on-component ~stopped)
         (stop-attached-components)
         (set-stopped))))

(defmacro defcomponent
  "Create new component type from a vector of `dependencies` (the components/fields that should
   be filled before the component is started, as well as a series of keyword/start/stop forms.

   Each keyword has to be followed by a form that will be executed at startup and optionally a
   (non-keyword) function to be run at shutdown.

     (defcomponent LoopRunner [f interval]
       :data   (fetch-some-data!)
       :thread (doto (Thread. #(while (not (.isInterrupted ...)) ...))
                 (.start))
               #(.interrupt ^Thread %))

   The results of the form/function will be assoc'd into the component. Please use the `map->...`
   function to create an instance of the record. Using fields with the namespace `peripheral` you
   can manipulate the component record directly:

     (defcomponent TestComponent [...]
       :peripheral/start   #(...)      ;; called before fields are initialized
       :peripheral/started #(...)      ;; called after fields are initialized
       :peripheral/stop    #(...)      ;; called before fields are cleaned up
       :peripheral/stopped #(...))     ;; called after fields are cleaned up

   Note that these take a function, not a form, and only allow for one value!"
  [id dependencies & component-logic]
  (let [{:keys [fields specifics lifecycle]} (analyze-component-logic component-logic)
        field-syms (map (comp symbol name first) fields)
        record-fields (set (concat field-syms dependencies))
        this (gensym "this")]
    `(defrecord ~id [~@record-fields]
       component/Lifecycle
       (start [~this]
         (if (running? ~this)
           ~this
           ~(create-start-form fields lifecycle field-syms this)))
       (stop [~this]
         (or
           (when (running? ~this)
             ~(create-stop-form fields lifecycle this))
           ~this))
       ~@specifics)))

;; ## Restart

(defn restart
  "Restart the given component by calling `stop` and `start`."
  [component]
  (-> component
      (component/stop)
      (component/start)))
