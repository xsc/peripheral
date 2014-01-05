(ns peripheral.core-test
  (:require [midje.sweet :refer :all]
            [peripheral.core :as peripheral :refer [defsystem connect]]))

;; ## Fixutres

(defsystem Sys [^:global g a b c]
  (connect :a :c)
  (connect :b :c))

(defrecord X [started]
  com.stuartsierra.component/Lifecycle
  (start [this]
    (assoc this :started true)))

(def make-x #(X. false))

(def sys (map->Sys
           (->> (for [k [:g :a :b :c]]
                  [k (make-x)])
                (into {}))))

;; ## Tests

(fact "about starting a system"
      (let [{:keys [g a b c]} (peripheral/start sys)]
        (map :started [g a b c]) => #(every? identity %)
        (map (juxt :g :c) [a b c]) => [[g c] [g c] [g nil]]))

(tabular
  (fact "about starting subsystems"
        (let [{:keys [g a b c]} (peripheral/start-subsystem sys ?components)]
          (map :started [g a b c]) => [?g ?a ?b ?c]
          (map (juxt :g :c) [a b c]) => [[(when ?a g) (when (and ?a ?c) c)]
                                         [(when ?b g) (when (and ?b ?c) c)]
                                         [(when ?c g) nil]]))
  ?components         ?g     ?a    ?b    ?c
  [:g]                true   false false false
  [:a]                true   true  false true
  [:b]                true   false true  true
  [:a :b]             true   true  true  true
  [:c]                true   false false true)
