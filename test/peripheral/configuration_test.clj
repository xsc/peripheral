(ns peripheral.configuration-test
  (:require [midje.sweet :refer :all]
            [peripheral.configuration :refer :all]))

(tabular
  (fact "about configuration loading"
        (let [cfg ?cfg]
          (satisfies? Configuration cfg) => truthy
          (load-configuration! cfg) => ?result))
  ?cfg                  ?result
  {:a 0}                {:a 0}
  nil                   nil
  #(hash-map :a 0)      {:a 0})
