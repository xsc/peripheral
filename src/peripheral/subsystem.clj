(ns ^{:author "Yannick Scherer"
      :doc "Subsystems for Peripheral."}
  peripheral.subsystem
  (:require [peripheral.system-map :as sys]
            [com.stuartsierra.dependency :as dep]
            [potemkin :refer [defprotocol+]]))

(defprotocol+ Subsystem
  "Protocol for Systems that support the creation of subsystems."
  (start-subsystem [this subsystem-components]
    "Only start the given components (and their dependencies)
     in the given system."))

(defn- map->graph
  "Convert dependency map to `com.stuartsierra.dependency/graph`."
  [dependencies]
  (reduce
    (fn [g [source dest-map]]
      (let [dests (vals dest-map)]
        (reduce #(dep/depend %1 source %2) g dests)))
    (dep/graph) dependencies))

(defn- subsystem-components-from-graph
  "Get all components needed to construct the given subsystem from a
   `com.stuartsierra.dependency/graph`."
  [graph components]
  (->> components
       (mapcat #(dep/transitive-dependencies graph %))
       (concat components)
       (set)))

(defn- subsystem-components
  "Get all component keys needed to construct the given subsystem from
   the given system record."
  [system components]
  (-> system
      (sys/system-meta)
      :dependencies
      (map->graph)
      (subsystem-components-from-graph components)))

(defn initialize-subsystem-meta
  "Initialize the peripheral metadata to represent the given subsystem."
  [system f components]
  (let [system (sys/initialize-system-meta system f)
        components (subsystem-components system components)]
    (-> system
        (sys/vary-system-meta update-in [:dependencies] select-keys components)
        (sys/vary-system-meta assoc :components (vec components)))))
