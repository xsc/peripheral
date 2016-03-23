(ns peripheral.system-test
  (:require [midje.sweet :refer :all]
            [com.stuartsierra.component :as component]
            [peripheral.system :refer :all]
            [peripheral.system.subsystem :refer [subsystem]]))

;; ## Fixtures

(defrecord X [started]
  component/Lifecycle
  (start [this]
    (assoc this :started true))
  (stop [this]
    (assoc this :started false)))

(def make-x
  #(X. false))

(defsystem Sys [^:global g a b c]
  (connect :a :c)
  (connect :b :c))

(def sys
  (map->Sys
    (->> (for [k [:g :a :b :c]]
           [k (make-x)])
         (into {}))))

(defsystem SysWithData [^:global ^:data config
                        ^:data   name
                        ^:global g a]
  (connect :a :system :name))

(def sys2
  (map->SysWithData
    (->> (for [k [:g :a]]
           [k (make-x)])
         (into {})
         (merge {:config :config-data
                 :name :sys}))))

;; ## Tests

(fact "about the initial system state"
      (let [{:keys [g a b c]} sys]
        (map :started [g a b c]) => (has every? falsey)
        (map :g [a b c]) => (has every? falsey)
        (map :c [a b c]) => (has every? falsey))
      (let [{:keys [g a config name]} sys2]
        config => :config-data
        name => :sys
        (map :started [g a]) => (has every? falsey)
        (map :config [g a]) => (has every? falsey)
        (:g a) => falsey
        (:system a) => falsey))

(fact "about starting/stopping a simple system"
      (let [{:keys [g a b c] :as started} (component/start sys)]
        (map :started [g a b c]) => (has every? truthy)
        (map :g [a b c]) => (has every? #{g})
        (map :c [a b c]) => [c c nil]
        (let [{:keys [g a b c] :as stopped} (component/stop started)]
          (map :started [g a b c]) => (has every? falsey)
          (map (comp :started :g) [a b c]) => (has every? falsey)
          (map (comp :started :c) [a b]) => (has every? falsey))))

(fact "about starting/stopping a complex system"
      (let [{:keys [g a config name] :as started} (component/start sys2)]
        config => :config-data
        name => :sys
        (map :started [g a]) => (has every? truthy)
        (map :config [g a]) => (has every? #{:config-data})
        (:g a) => g
        (:system a) => :sys
        (let [{:keys [g a config name] :as stopped} (component/stop started)]
          config => :config-data
          name => :sys
          (map :started [g a]) => (has every? falsey)
          (-> a :g :started) => falsey
          (let [{:keys [g a config name] :as restarted} (component/start stopped)]
            config => :config-data
            name => :sys
            (map :started [g a]) => (has every? truthy)
            (map :config [g a]) => (has every? #{:config-data})
            (:g a) => g
            (:system a) => :sys))))

(tabular
  (fact "about starting/stopping subsystems"
        (let [{:keys [g a b c] :as started}
              (-> sys
                  (subsystem ?components)
                  (component/start))]
          (map :started [g a b c]) => [?g ?a ?b ?c]
          (map :g [a b c]) => [(when ?a g) (when ?b g) (when ?c g)]
          (map (comp boolean :started :g) [a b c]) => [?a ?b ?c]
          (map :c [a b c]) => [(when (and ?a ?c) c) (when (and ?b ?c) c) nil]
          (let [{:keys [g a b c] :as stopped} (component/stop started)]
            (map :started [g a b c]) => (has every? falsey)
            (map (comp :started :g) [a b c]) => (has every? falsey))))
  ?components         ?g     ?a    ?b    ?c
  [:g]                true   false false false
  [:a]                true   true  false true
  [:b]                true   false true  true
  [:a :b]             true   true  true  true
  [:c]                true   false false true)
