(ns peripheral.component-test
  (:require [midje.sweet :refer :all]
            [peripheral.component :refer [defcomponent]]
            [com.stuartsierra.component :refer [start stop]]))

;; ## Basic Functionality

(defcomponent Test [n]
  :n-inc (inc n) dec
  :n-twice (* n-inc 2))

(fact "about 'defcomponent'"
      (let [t (map->Test {:n 0})
            started (start t)
            stopped (stop started)]
        (instance? Test t) => truthy
        (keys t) => (contains #{:n :n-inc :n-twice})
        (:n t) => 0
        (:n-inc t) => nil
        (:n-twice t) => nil
        (:n started) => 0
        (:n-inc started) => 1
        (:n-twice started) => 2
        (:n stopped) => 0
        (:n-inc stopped) => 0
        (:n-twice stopped) => nil))

;; ## Protocol Implementation

(defprotocol Proto
  (x [this]))

(defcomponent Test [n]
  :a n
  :b (inc a)
  Proto
  (x [_] b))

(fact "about 'defcomponent' + protocol"
      (let [t (map->Test {:n 0})
            started (start t)
            stopped (stop started)]
        (instance? Test t) => truthy
        (satisfies? Proto t) => truthy
        (keys t) => (contains #{:n :a :b})
        (:n t) => 0
        (:a t) => nil
        (:b t) => nil
        (:n started) => 0
        (:a started) => 0
        (:b started) => 1
        (x started) => 1
        (:n stopped) => 0
        (:a stopped) => nil
        (:b stopped) => nil
        (x stopped) => nil))
