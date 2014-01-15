(ns peripheral.component
  (:require [com.stuartsierra.component :as component]))

(defn- analyze-component-logic
  "Create pair of a map of fields (associated with a map of start/stop logic), as well
   as a seq of component specifics."
  [logic-seq]
  (loop [sq logic-seq
         fields {}]
    (if (empty? sq)
      [fields nil]
      (let [[[k start stop] rst] (split-at 3 sq)]
        (if (keyword? k)
          (if (keyword? stop)
            (recur (cons stop rst) (assoc fields k {:start start}))
            (recur rst (assoc fields k {:start start :stop stop})))
          [fields sq])))))

(defn- create-start-map
  "Take a map of fields with start/stop logic and create the map to be used for
   initial startup."
  [field-map]
  (->> (for [[field m] field-map]
         [field (when-let [form (:start m)]
                  `(try
                     ~form
                     (catch Throwable ex#
                       (throw (Exception. (str ~(str "Could not initialize field: " field " (") (.getMessage ex#) ")") ex#)))))])
       (into field-map)))

(defn- create-stop-map
  [field-map this]
  "Take a map of fields with start/stop logic and create the map to be used for
   cleanup."
  (->> (for [[field m] field-map]
         [field (when-let [f (:stop m)]
              `(try
                 (~f (get ~this ~field))
                 (catch Throwable ex#
                   (throw (Exception. (str ~(str "Could not cleanup field: " field " (") (.getMessage ex#) ")") ex#)))))])
       (into field-map)))

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
  (let [[field-map specifics] (analyze-component-logic component-logic)
        fields (map (comp symbol name) (keys field-map))
        this (gensym "this")]
    `(defrecord ~id [~@dependencies ~@fields]
       component/Lifecycle
       (start [~this]
         (->> ~(create-start-map field-map)
              (merge ~this)))
       (stop [~this]
         (->> ~(create-stop-map field-map this)
              (merge ~this)))
       ~@specifics)))
