(ns peripheral.component
  (:require [com.stuartsierra.component :as component]
            [peripheral.utils :refer [is-class-name?]]))

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
                                     (assoc this# ~field ~start)))
                                fields)])
        component-init-form (if start
                              `(let [~this (or (~start ~this) ~this)]
                                 ~field-init-form)
                              field-init-form)
        component-start-form (if start
                               `(let [c# ~component-init-form]
                                  (or (~started c#) c#))
                               component-init-form)]
    component-start-form))

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
    component-done-form))

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
