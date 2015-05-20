(ns peripheral.core-test
  (:require [midje.sweet :refer :all]
            [peripheral.core :refer :all]))

(defcomponent Test [data-atom value]
  :peripheral/started (fn [this]
                        (reset! data-atom [:started value])
                        this)
  :peripheral/stopped (fn [this]
                        (reset! data-atom [:stopped value])
                        this))

(fact "about 'with-start'"
      (let [x (atom nil)]
        (with-start [started (Test. x nil)]
          @x => [:started nil])
        @x => [:stopped nil])
      (let [parent-atom (atom nil)
            child-atom (atom nil)]
        (with-start [parent (Test. parent-atom nil)
                     child (Test. child-atom parent)]
          @parent-atom => [:started nil]
          @child-atom  => [:started parent])))
