(ns peripheral.core
  (:require [com.stuartsierra.component :as component]))

;; ## Configuration

(defprotocol Config
  "Protocol for System configurations."
  (load-configuration! [this]
    "Load the configuration data."))

(extend-protocol Config
  clojure.lang.AFunction
  (load-configuration! [f]
    (f))
  clojure.lang.IPersistentMap
  (load-configuration! [m]
    m)
  nil
  (load-configuration! [_]
    nil))

;; ## Logic

(defn connect
  "Let the component identified by `src-id` be using the component identified
   by `dst-id` as a dependency to be assoc'd in at the key `src-key`."
  ([m src-id dst-id] (connect m src-id dst-id dst-id))
  ([m src-id src-key dst-id]
   (if-not (= src-id dst-id)
     (assoc-in m [:dependencies src-id src-key] dst-id)
     m)))

(defn with-config
  "Associate a key in the component identified by `src-id` with the configuration stored
   at `cfg-key`."
  ([m src-id cfg-key] (with-config m src-id cfg-key cfg-key))
  ([m src-id src-key cfg-key]
   (assoc-in m [:configurations src-id src-key] cfg-key)))

(defmacro on
  "Register handlers."
  [m k & fn-body]
  `(assoc-in ~m [:on ~k] (fn ~@fn-body)))

;; ## System

(defn- global?
  "Is the given symbol representing a global component?"
  [sym]
  (-> sym meta :global))

(defn- config?
  "Is the given symbol representing a configuration?"
  [sym]
  (-> sym meta :config))

(defn initial-dependencies
  "Create initial dependency map."
  [m components]
  (let [{:keys [local global]} (reduce
                                 (fn [m sym]
                                   (let [k (if (global? sym) :global :local)]
                                     (update-in m [k] conj (keyword sym))))
                                 {} components)]
    (->> (for [a local b global] [a b])
         (reduce #(apply connect %1 %2) m))))

(defn initial-configurations
  "Create initial configuration map."
  [m components configurations]
  (->> (for [a components b configurations]
         [(keyword a) (keyword b)])
       (reduce #(apply with-config %1 %2) m)))

(defn attach-system-meta
  "Attach system metadata to component record."
  [system data]
  (vary-meta system assoc ::data data))

(defn using-configurations
  "Assoc configurations into the given System using the given configuration
   dependency map."
  [system configurations]
  (reduce
    (fn [system [component-key configuration-map]]
      (reduce
        (fn [system [field-key config-key]]
          (assoc-in system [component-key field-key] (get system config-key)))
        system configuration-map))
    system configurations))

(defn start-system-with-meta
  "Start the given component using previously stored metadata."
  [system component-keys]
  (let [{:keys [dependencies configurations on]} (-> system meta ::data)
        init (:start on identity)
        stop (:stop on identity)]
    (-> system
        init
        (using-configurations configurations)
        (component/system-using dependencies)
        (component/start-system component-keys))))

(defn stop-system-with-meta
  "Stop the given component using previously stored metadata."
  [system component-keys]
  (component/stop-system system component-keys)
  (when-let [stop (-> system meta ::data :on :stop)]
    (stop system)))

(defmacro defsystem
  "Create new system component record."
  [id fields & impl]
  (let [components (remove config? fields)
        component-keys (map keyword components)
        configurations (filter config? fields)
        [system-logic specifics] (split-with (complement symbol?) impl)]
    `(defrecord ~id [~@fields]
       component/Lifecycle
       (start [this#]
         (-> this#
             (attach-system-meta
               (-> ~(-> {}
                        (initial-dependencies components)
                        (initial-configurations components configurations))
                   ~@system-logic))
             (start-system-with-meta [~@component-keys])))
       (stop [this#]
         (stop-system-with-meta this# [~@component-keys])) ~@specifics)))
