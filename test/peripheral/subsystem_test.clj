(ns peripheral.subsystem-test
  (:require [midje.sweet :refer :all]
            [peripheral.subsystem :refer :all]))

(let [deps {:a {:b :b}
            :b {:d :d}
            :c {:b :b}
            :x {:a :a :c :c}}]
  (tabular
    (fact "about subsystem component collection"
          (subsystem-components-from-dependencies deps ?components) => (set ?result))
    ?components       ?result
    [:d]              [:d]
    [:b]              [:b :d]
    [:a]              [:a :b :d]
    [:c]              [:c :b :d]
    [:a :b]           [:a :b :d]
    [:a :b :c]        [:a :b :c :d]
    [:x]              [:x :a :b :c :d]))
