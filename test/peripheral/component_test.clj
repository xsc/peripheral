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

;; ## Lifecycle

(defcomponent Test [state-atom n]
  :peripheral/init (fn [_] (swap! state-atom conj :init) _)
  :peripheral/start (fn [_] (swap! state-atom conj :start) _)
  :peripheral/stop (fn [_] (swap! state-atom conj :stop) _)
  :peripheral/done (fn [_] (swap! state-atom conj :done) _)

  :a (do (swap! state-atom conj :init-a) (inc n))
     #(do % (swap! state-atom conj :cleanup-a) nil))

(fact "about 'defcomponent' lifecycle functions"
      (let [a (atom [])
            t (map->Test {:state-atom a :n 0})]
        @a => empty?
        (:n t) => 0
        (:a t) => nil
        (let [started (start t)]
          @a => [:init :init-a :start]
          (:n started) => 0
          (:a started) => 1
          (let [stopped (stop started)]
            @a => [:init :init-a :start :stop :cleanup-a :done]
            (:n stopped) => 0
            (:a stopped) => nil))))
