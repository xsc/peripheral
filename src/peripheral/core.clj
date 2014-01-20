(ns peripheral.core
  (:require [peripheral.component]
            [peripheral.system]
            [com.stuartsierra.component :as component]
            [potemkin :refer [import-vars]]))

;; ## API Facade

(import-vars
  [peripheral.system
   defsystem]
  [peripheral.system-map
   connect configure]
  [peripheral.subsystem
   start-subsystem]
  [peripheral.component
   defcomponent]
  [com.stuartsierra.component
   start stop])
