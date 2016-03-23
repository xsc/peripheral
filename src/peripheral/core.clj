(ns peripheral.core
  (:require [peripheral component system system-plus]
            [peripheral.component attach state]
            [peripheral.system subsystem]
            [com.stuartsierra.component :as component]
            [potemkin :refer [import-vars]]))

;; ## API Facade

(import-vars
  [peripheral.system
   defsystem connect]
  [peripheral.system-plus
   defsystem+]
  [peripheral.system.subsystem
   subsystem]
  [peripheral.component
   defcomponent restart]
  [peripheral.component.attach
   attach detach]
  [peripheral.component.state
   running?]
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
  [[form component & more] & body]
  `(with-start*
     ~component
     (fn [~form]
       ~(if (seq more)
          (list* `with-start more body)
          (list* `do body)))))
