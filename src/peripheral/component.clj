(ns peripheral.component
  (:require [com.stuartsierra.component :as component]))

(defn- analyze-component-logic
  "Create pair of a seq of fields (as a pair, associated with a map of start/stop logic), as well
   as a seq of component specifics."
  [logic-seq]
  (loop [sq logic-seq
         fields []]
    (if (empty? sq)
      [fields nil]
      (let [[[k start stop] rst] (split-at 3 sq)]
        (if (keyword? k)
          (if (keyword? stop)
            (recur (cons stop rst) (conj fields [k {:start start}]))
            (recur rst (conj fields [k {:start start :stop stop}])))
          [fields sq])))))

(defn- create-start-form
  "Create component startup form initializing the fields in order of definition."
  [fields field-syms this]
  `(reduce
     #(%2 %1)
     ~this
     [~@(map-indexed
          (fn [i [field {:keys [start]}]]
            `(fn [{:keys [~@(take i field-syms)] :as this#}]
               (assoc this# ~field ~start)))
          fields)]))

(defn- create-stop-form
  [fields this]
  "Take a map of fields with start/stop logic and create the map to be used for
   cleanup."
  (->> (for [[field m] fields]
         [field (when-let [f (:stop m)]
                  `(try
                     (~f (get ~this ~field))
                     (catch Throwable ex#
                       (throw (Exception. (str ~(str "Could not cleanup field: " field " (") (.getMessage ex#) ")") ex#)))))])
       (into {})
       (list `merge this)))

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
   function to create an instance of the record."
  [id dependencies & component-logic]
  (let [[fields specifics] (analyze-component-logic component-logic)
        field-syms (map (comp symbol name first) fields)
        record-fields (set (concat field-syms dependencies))
        this (gensym "this")]
    `(defrecord ~id [~@record-fields]
       component/Lifecycle
       (start [~this]
         ~(create-start-form fields field-syms this))
       (stop [~this]
         ~(create-stop-form fields this))
       ~@specifics)))
