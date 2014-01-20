(ns peripheral.system
  (:require [com.stuartsierra.component :as component]
            [peripheral.configuration :refer [load-configuration!]]
            [peripheral.system-map :as sys]
            [peripheral.subsystem :as sub]
            [peripheral.utils :refer [is-class-name?]]))

;; ## System Map Creation/Processing

(defn system-configurations-using
  "Assoc configurations into the given System using the given configuration
   dependency map."
  [system configurations]
  (let [configuration-keys (->> (vals configurations)
                                (mapcat vals)
                                (set))
        idle-configurations (select-keys system configuration-keys)
        loaded-configurations (->> (for [[k cfg] idle-configurations]
                                     [k (load-configuration! cfg)])
                                   (into {}))]
    (reduce
      (fn [system [component-key configuration-map]]
        (reduce
          (fn [system [field-key config-key]]
            (assoc-in system [component-key field-key] (loaded-configurations config-key)))
          system configuration-map))
      system configurations)))

(defn start-system-with-meta
  "Start the given component using previously stored metadata."
  [system]
  (let [{:keys [dependencies configurations components]} (sys/system-meta system)]
    (-> system
        (system-configurations-using configurations)
        (component/system-using dependencies)
        (component/start-system components))))

(defn stop-system-with-meta
  "Stop the given component using previously stored metadata."
  [system]
  (let [{:keys [components]} (sys/system-meta system)]
    (component/stop-system system components)))

;; ## System Creation

(defmacro defsystem
  "Create new system component record."
  [id fields & impl]
  (let [cfg-fn (symbol (str (name id) "->configuration"))
        [system-logic specifics] (split-with (complement is-class-name?) impl)]
    `(do
       (defn ~cfg-fn
         ~(str "Create peripheral system map for the given `" (name id) "` record.")
         [{:keys [~@fields]}]
         (-> ~(sys/initial-system-map fields)
             ~@system-logic))
       (defrecord ~id [~@fields]
         component/Lifecycle
         (start [this#]
           (-> this#
               (sys/initialize-system-meta ~cfg-fn)
               (start-system-with-meta)))
         (stop [this#]
           (stop-system-with-meta this#))
         sub/Subsystem
         (start-subsystem [this# components#]
           (-> this#
               (sub/initialize-subsystem-meta ~cfg-fn components#)
               (start-system-with-meta)))
         ~@specifics))))
