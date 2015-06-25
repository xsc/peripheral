(ns peripheral.component.analysis
  (:require [com.stuartsierra.component :as component]
            [peripheral.component.lifecycle :as lifecycle]
            [peripheral.utils :refer [is-class-name?]]))

;; ## Plain Field

(defn- add-field
  "Add field to analysis map."
  [result-map field start stop]
  (update-in result-map
             [:fields]
             (comp vec conj)
             [field {:start start :stop stop, :record? true}]))

;; ## Steps

(defn- add-step
  "Add arbitrary logic (implicit fields) to analysis map."
  [result-map field form]
  (update-in result-map
             [:fields]
             (comp vec conj)
             [field {:start form, :record? false}]))

;; ## :this/as

(defn- add-this
  "Add symbol to use for the component itself to the analysis map."
  [result-map k sym]
  (assert (= k :as) "only :this/as is allowed to bind component.")
  (assert (not (:this result-map)) "duplicate ':this/as'  statement.")
  (assert (symbol? sym) (format ":this/as needs symbol; given: %s" (pr-str sym)))
  (assoc result-map :this sym))

;; ## Protocol Implementation

(defn- finalize-analysis
  "Process everything that is left and add data to analysis map."
  [result-map rest-seq]
  (assoc result-map :specifics rest-seq))

;; ## :on/... and :peripheral/...

(defn- update-lifecycle
  [m k wrapper-fn fn-form]
  (update-in m [:lifecycle k] #(list wrapper-fn % fn-form)))

(defn- add-passive-lifecycle
  "Add a passive lifecycle function to the analysis map."
  [m k form]
  (update-lifecycle m k `lifecycle/passive-lifecycle-fn `(fn [] ~form)))

(defn- add-active-lifecycle
  "Add an active lifecycle function to the analysis map."
  [m k fn-form]
  (update-lifecycle m k `lifecycle/active-lifecycle-fn fn-form))

;; ## :component/... and :components/...

(defn- add-component
  "Add component field to analysis map."
  [m k component-form]
  (add-field m k `(component/start ~component-form) `component/stop))

(defn- add-components
  "Add component seq field to analysis map."
  [m k component-seq-form]
  (add-field
    m
    k
    `(lifecycle/start-all ~component-seq-form)
    `lifecycle/stop-all))

;; ## :assert/...

(defn- add-assertion
  "Add assertion to startup logic."
  [m k assertion-form]
  (add-step
    m
    k
    `(assert ~assertion-form)))

;; ## Analysis

(def ^:private namespace-dispatch
  "Allow for special keywords that are handled by their namespace."
  {"peripheral" add-active-lifecycle
   "on"         add-passive-lifecycle
   "this"       add-this
   "assert"     add-assertion
   "component"  add-component
   "components" add-components})

(defn analyze-component
  "Create pair of a seq of fields (as a pair, associated with a map of start/stop logic), as well
   as a seq of component specifics."
  [logic-seq]
  (loop [sq logic-seq
         m {}]
    (if (empty? sq)
      (finalize-analysis m sq)
      (let [[[k a b] rst] (split-at 3 sq)]
        (if (keyword? k)
          (if-let [dispatch (namespace-dispatch (namespace k))]
            (recur (drop 2 sq) (dispatch m (-> k name keyword) a))
            (if (or (keyword? b) (is-class-name? b))
              (recur (drop 2 sq) (add-field m k a nil))
              (recur rst (add-field m k a b))))
          (finalize-analysis m sq))))))
