(ns peripheral.component-test
  (:require [midje.sweet :refer :all]
            [peripheral.component :refer [defcomponent]]
            [com.stuartsierra.component :refer [start stop]]))

(defcomponent Test [n]
  :n-inc (inc n) dec
  :n-twice (* n 2))

(fact "about 'defcomponent'"
      (let [t (map->Test {:n 1})
            started (start t)
            stopped (stop started)]
        (instance? Test t) => truthy
        (keys t) => (contains #{:n :n-inc :n-twice})
        (:n t) => 1
        (:n-inc t) => nil
        (:n-twice t) => nil
        (:n started) => 1
        (:n-inc started) => 2
        (:n-twice started) => 2
        (:n stopped) => 1
        (:n-inc stopped) => 1
        (:n-twice stopped) => nil))
