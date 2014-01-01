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

(defrecord ConfigComponent [data configuration]
  component/Lifecycle
  (start [this]
    (assoc this :data (load-configuration! configuration)))
  (stop [this]
    this))

;; ## Logic

(defn connect
  "Let the component identified by `src-id` be using the component identified
   by `dst-id` as a dependency to be assoc'd in at the key `src-key`."
  ([m src-id dst-id] (connect m src-id dst-id dst-id))
  ([m src-id src-key dst-id]
   (if-not (= src-id dst-id)
     (assoc-in m [:dependencies src-id src-key] dst-id)
     m)))

(defmacro on
  "Register handlers."
  [m k & fn-body]
  `(assoc-in ~m [:on ~k] (fn ~@fn-body)))

;; ## System

(defn- global-component?
  "Is the given symbol representing a global component?"
  [sym]
  (let [m (meta sym)]
    (some #(get m %) [:global :config])))

(defn- config-component?
  "Is the given symbol representing a configuration component?"
  [sym]
  (-> sym meta :config))

(defn initial-dependencies
  "Create initial dependency map."
  [components]
  (let [{:keys [local global]} (reduce
                                 (fn [m sym]
                                   (let [k (if (global-component? sym) :global :local)]
                                     (update-in m [k] conj (keyword sym))))
                                 {} components)]
    (->> (for [a local b global] [a b])
         (reduce #(apply connect %1 %2) {}))))

(defn attach-system-meta
  "Attach system metadata to component record."
  [system data]
  (vary-meta system assoc ::data data))

(defn start-system-with-meta
  "Start the given component using previously stored metadata."
  [system component-keys]
  (let [{:keys [dependencies on]} (-> system meta ::data)
        init (:start on identity)
        stop (:stop on identity)]
    (-> system
        init
        (component/system-using dependencies)
        (component/start-system component-keys))))

(defn stop-system-with-meta
  "Stop the given component using previously stored metadata."
  [system component-keys]
  (component/stop-system system component-keys)
  (when-let [stop (-> system meta ::data :on :stop)]
    (stop system)))

(defn create-configuration-components
  "Convert a map of component-ID/`peripheral.Config` pairs to the respective
   component map."
  [configurations]
  (->> (for [[k v] configurations]
         [k (map->ConfigComponent {:configuration v})])
       (into {})))

(defmacro defsystem
  "Create new system component record."
  [id components & impl]
  (let [T (symbol (str "system_" (name id)))
        ->system (symbol (str "map->" (name T)))
        component-keys (map keyword components)
        configurations (filter config-component? components)
        [system-logic specifics] (split-with (complement symbol?) impl)]
    `(do
       (defrecord ~T [~@components]
         component/Lifecycle
         (start [this#]
           (-> this#
               (attach-system-meta
                 (-> ~(initial-dependencies components)
                     ~@system-logic))
               (start-system-with-meta [~@component-keys])))
         (stop [this#]
           (stop-system-with-meta this# [~@component-keys]))
         ~@specifics)

       (defn ~id
         ([components#] (~id components# nil))
         ([components# configurations#]
          (-> (create-configuration-components configurations#)
              (merge components#)
              (select-keys [~@component-keys])
              ~->system))))))
