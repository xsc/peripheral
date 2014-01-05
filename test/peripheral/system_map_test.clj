(ns peripheral.system-map-test
  (:require [midje.sweet :refer :all]
            [peripheral.system-map :refer :all]))

(fact "about system map manipulation functions"
      (connect {} :a :b :c) => {:dependencies {:a {:b :c}}}
      (connect {} :a :c) => {:dependencies {:a {:c :c}}}
      (configure {} :a :b :c) => {:configurations {:a {:b :c}}}
      (configure {} :a :c) => {:configurations {:a {:c :c}}})

(tabular
  (fact "about initial system map creation"
        (let [m (initial-system-map ?fields)]
          (:dependencies m) => ?dependencies
          (:configurations m) => ?configurations
          (:components m) => ?components))
  ?fields                    ?dependencies            ?configurations          ?components
  '[a b c]                   nil                      nil                      [:a :b :c]
  '[^:global a b c]          {:b {:a :a} :c {:a :a}}  nil                      [:a :b :c]
  '[^:config a b c]          nil                      {:b {:a :a} :c {:a :a}}  [:b :c]
  '[^:global a ^:config b c] {:c {:a :a}}             {:a {:b :b} :c {:b :b}}  [:a :c])
