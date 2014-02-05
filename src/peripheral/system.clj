(ns peripheral.system
  (:require [com.stuartsierra.component :as component]
            [com.stuartsierra.dependency :as dep]
            [peripheral.component :refer [defcomponent running?]]
            [peripheral.utils :refer [is-class-name?]]))

;; ## `defsystem`

;; ### Relations

(defn connect
  "Let the component identified by `src-id` be using the component identified
   by `dst-id` as a dependency to be assoc'd in at the key `src-key`."
  ([m src-id dst-id] (connect m src-id dst-id dst-id))
  ([m src-id src-key dst-id]
   (if-not (= src-id dst-id)
     (assoc-in m [src-id src-key] dst-id)
     m)))

;; ### Initial Analysis

(defn- merge-to
  "Merge two sets contained in the keys `a` and `b` into key `k` in the same map."
  [k a b m]
  (->> (concat (m a) (m b))
       (set)
       (assoc m k)))

(defn- create-field-map
  "Create map with the keys `:global-components`, `:global-data`, `:data` and
   `:components` outlining the shape of the system."
  [components]
  (->> components
       (group-by (comp #(mapv boolean %) (juxt :global :data) meta))
       (map
         (fn [[k v]]
           (vector
             (get
               {[true false]  :global-components
                [true true]   :global-data
                [false true]  :local-data
                [false false] :local-components}
               k)
             (set (map (comp keyword name) v)))))
       (into {})
       (merge-to :data       :global-data       :local-data)
       (merge-to :components :global-components :local-components)))

(defn- create-initial-dependencies
  "Create initial dependency map based on global/local components."
  [{:keys [global-components global-data local-components]}]
  (merge
    (->> (for [global global-components
               data   global-data]
           [global data])
         (reduce #(apply connect % %2) {}))
    (->> (for [global (concat global-components global-data)
               local  local-components]
           [local global])
         (reduce #(apply connect % %2) {}))))

(defn- create-dependencies
  "Create form that produces the dependency map (attaching the symbol map as metadata)."
  [components relations]
  (let [field-map (create-field-map components)]
    `(-> ~(create-initial-dependencies field-map)
         (with-meta ~field-map)
         ~@relations)))

;; ## Dependencies

(defn- map->graph
  "Convert dependency map to `com.stuartsierra.dependency/graph`."
  [dependencies]
  (reduce
    (fn [g [source dest-map]]
      (let [dests (vals dest-map)]
        (reduce #(dep/depend %1 source %2) g dests)))
    (dep/graph) dependencies))

(defn- field-dependencies-from-graph
  "Create transitive dependencies for the given fields in the given graph. Will include the original
   fields in any case."
  [fields graph]
  (->> fields
       (mapcat #(dep/transitive-dependencies graph %))
       (concat fields)
       (set)))

(defn- field-dependencies
  "Create a seq of field keywords that depend on the given ones."
  [fields dependencies]
  (->> dependencies
       (map->graph)
       (field-dependencies-from-graph fields)))

(defn- component-dependencies
  "Based on a seq of component keywords and a dependency map, create a map of
   `:components` (i.e. the actual component dependencies) and `:data` (the data
   dependencies)."
  [components dependencies]
  (let [component? (-> dependencies meta :components set)]
    (->> dependencies
         (field-dependencies components)
         (filter component?)
         (map #(vector % (get dependencies % {})))
         (into {}))))

;; ### Startup/Shutdown

(defn- add-active-components
  "Add components to be activated to the system's metadata."
  [system components]
  (vary-meta system update-in [::active] (comp set concat) components))

(defn- active-components
  "Decide on what components are active/to-be-activated by examining the system's metadata
   and the given dependency map."
  [system dependencies]
  (or (-> system meta ::active)
      (-> dependencies meta :components)))

(defn start-components
  "Start to-be-activated components and their dependencies."
  [system dependencies]
  (let [component-dependencies (-> system
                                   (active-components dependencies)
                                   (component-dependencies dependencies))
        components (keys component-dependencies)]
    (-> system
        (add-active-components components)
        (component/system-using component-dependencies)
        (component/start-system components))))

(defn- clean-components
  [system components]
  (component/update-system-reverse
    system
    (filter #(get system %) components)
    identity))

(defn stop-components
  "Stop all currently active components."
  [system]
  (if-let [components (-> system meta ::active seq)]
    (-> system
        (component/stop-system components)
        (clean-components components))
    system))

;; ### Macro

(defmacro defsystem
  [id components & logic]
  (assert (vector? components) "defsystem expects a vector as 2nd parameter.")
  (assert (every? symbol? components) "defsystem expects a vector of symbols as 2nd parameter.")
  (let [[relations specifics] (split-with (complement is-class-name?) logic)]
    `(defcomponent ~id [~@components]
       :peripheral/start (let [deps# ~(create-dependencies components relations)]
                           #(start-components % deps#))
       :peripheral/stop  stop-components

       ~@specifics)))

;; ## Subsystem

(defn subsystem
  "Create subsystem. The resulting system will only start the given components and
   their dependencies."
  [system components]
  (add-active-components system components))
