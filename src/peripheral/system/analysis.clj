(ns peripheral.system.analysis
  (:require [peripheral.system.using :refer [to-system-using]]
            [peripheral.utils :refer [is-class-name?]]
            [com.stuartsierra.dependency :as dep]))

;; ## Analysis of System Body

(defn- is-global?
  [value]
  (-> value meta :global))

(defn- collect-globals
  [dependencies components]
  (->> components
       (filter (comp is-global? second))
       (map first)
       (concat (filter is-global? dependencies))
       (map (comp keyword name))
       (distinct)
       (to-system-using)))

(defn- collect-components
  [components globals]
  (for [[k using] components
        :let [n (name k), k (keyword n)]]
    {:name        n
     :key         k
     :default     (-> using meta :default)
     :using       (merge
                    (dissoc globals k)
                    (to-system-using using))}))

(defn analyze
  [args]
  (let [[dependencies rst]
        (if (sequential? (first args))
          [(first args) (next args)]
          [nil args])
        [components' specifics]
        (split-with (complement is-class-name?) rst)
        components (partition 2 components')
        globals    (collect-globals dependencies components)]
    (assert (even? (count components')))
    {:components   (collect-components components globals)
     :dependencies dependencies
     :specifics    specifics}))

;; ## Dependency Analysis

(defn- components->graph
  "Convert component dependencies to `com.stuartsierra.dependency/graph`."
  [components]
  (let [component-key? (set (map :key components))]
    (reduce
      (fn [g {:keys [key using]}]
        (->> (vals using)
             (filter component-key?)
             (reduce #(dep/depend %1 key %2) g)))
      (dep/graph)
      components)))

(defn- required-components-from-graph
  [graph active-components]
  (->> active-components
       (mapcat #(dep/transitive-dependencies graph %))
       (concat active-components)
       (set)))

(defn required-components
  [components active-components]
  (-> (components->graph components)
      (required-components-from-graph active-components)
      (set)))
