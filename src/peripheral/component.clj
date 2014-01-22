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

;; ## `defcomponent`

(defn- analyze-component-logic
  "Create pair of a seq of fields (as a pair, associated with a map of start/stop logic), as well
   as a seq of component specifics."
  [logic-seq]
  (loop [sq logic-seq
         fields []
         lifecycle {}]
    (if (empty? sq)
      [fields nil lifecycle]
      (let [[[k start stop] rst] (split-at 3 sq)]
        (if (keyword? k)
          (cond (= (namespace k) "peripheral") (recur (drop 2 sq) fields (assoc lifecycle (-> k name keyword) start))
                (or (keyword? stop) (is-class-name? stop)) (recur (drop 2 sq) (conj fields [k {:start start}]) lifecycle)
                :else (recur rst (conj fields [k {:start start :stop stop}]) lifecycle))
          [fields sq lifecycle])))))

(defn- create-start-form
  "Create component startup form initializing the fields in order of definition."
  [fields {:keys [start started]} field-syms this]
  (let [field-init-form `(reduce
                           #(%2 %1)
                           ~this
                           [~@(map-indexed
                                (fn [i [field {:keys [start]}]]
                                  `(fn [{:keys [~@(take i field-syms)] :as this#}]
                                     (try
                                       (assoc this# ~field ~start)
                                       (catch Throwable ex#
                                         (throw
                                           (Exception.
                                             (str ~(str "Could not initialize field: " field "(") (.getMessage ex#) ")") ex#))))))
                                fields)])
        component-init-form (if start
                              `(let [~this (or (~start ~this) ~this)]
                                 ~field-init-form)
                              field-init-form)
        component-start-form (if start
                               `(let [c# ~component-init-form]
                                  (or (~started c#) c#))
                               component-init-form)]
    `(-> ~component-start-form
         (start-attached-components)
         (set-started))))

(defn- create-stop-form
  [fields {:keys [stop stopped]} this]
  "Take a map of fields with start/stop logic and create the map to be used for
   cleanup."
  (let [stop-map (->> (for [[field m] fields]
                        [field (when-let [f (:stop m)]
                                 `(try
                                    (~f (get ~this ~field))
                                    (catch Throwable ex#
                                      (throw
                                        (Exception.
                                          (str ~(str "Could not cleanup field: " field " (") (.getMessage ex#) ")") ex#)))))])
                      (into {}))
        component-merge-form `(merge ~this ~stop-map)
        component-stop-form (if stop
                              `(let [~this (or (~stop ~this) ~this)]
                                 ~component-merge-form)
                              component-merge-form)
        component-done-form (if stopped
                              `(let [c# ~component-stop-form]
                                 (or (~stopped c#) c#))
                              component-stop-form)]
    `(let [~this (-> ~this
                     (set-stopped)
                     (stop-attached-components))]
       ~component-done-form)))

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
       :peripheral/init    #(...)      ;; called before fields are initialized
       :peripheral/started #(...)      ;; called after fields are initialized
       :peripheral/stop    #(...)      ;; called before fields are cleaned up
       :peripheral/stopped #(...))     ;; called after fields are cleaned up

   Note that these take a function, not a form, and only allow for one value!"
  [id dependencies & component-logic]
  (let [[fields specifics lifecycle] (analyze-component-logic component-logic)
        field-syms (map (comp symbol name first) fields)
        record-fields (set (concat field-syms dependencies))
        this (gensym "this")]
    `(defrecord ~id [~@record-fields]
       component/Lifecycle
       (start [~this]
         ~(create-start-form fields lifecycle field-syms this))
       (stop [~this]
         ~(create-stop-form fields lifecycle this))
       ~@specifics)))
