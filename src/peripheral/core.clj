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
