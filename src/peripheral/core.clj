(ns peripheral.core
  (:require [peripheral.component]
            [peripheral.system]
            [com.stuartsierra.component :as component]
            [potemkin :refer [import-vars]]))

;; ## API Facade

(import-vars
  [peripheral.system
   defsystem connect subsystem]
  [peripheral.component
   defcomponent attach detach
   running? restart]
  [com.stuartsierra.component
   start stop])

;; ## Utilities

(defn with-start*
  "Start the given component, call the given function with the started component
   as only parameter, then stop the component."
  [component f]
  (let [started-component (component/start component)]
    (try
      (f started-component)
      (finally
        (component/stop started-component)))))

(defmacro with-start
  "Start the given component, binding the started component to the given form,
   ensuring the shutdown of the component after the body has been executed."
  [[form component] & body]
  `(with-start*
     ~component
     (fn [~form] ~@body)))
