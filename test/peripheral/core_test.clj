(ns peripheral.core-test
  (:require [midje.sweet :refer :all]
            [peripheral.core :refer :all]))

(defcomponent Test [data-atom]
  :peripheral/started (fn [this]
                        (reset! data-atom "started")
                        this)
  :peripheral/stopped (fn [this]
                        (reset! data-atom "stopped")
                        this))

(fact "about 'with-start'"
      (let [x (atom nil)]
        (with-start [started (Test. x)]
          @x => "started")
        @x => "stopped"))
