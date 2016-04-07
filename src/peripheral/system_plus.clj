(ns peripheral.system-plus
  (:require [peripheral
             [component :refer [defcomponent]]
             [utils :refer [is-class-name?]]]
            [peripheral.system
             [analysis :as analysis]
             [subsystem :as subsystem]]
            [com.stuartsierra.dependency :as dep]
            [com.stuartsierra.component :as component]))

;; ## Startup

(defn- assert-valid
  [component system name using]
  (when (nil? component)
    (throw
      (IllegalStateException.
        (format "%s.%s is nil"
                (.getSimpleName (class system))
                name))))
  (when-not (or (empty? using) (map? component))
    (throw
      (IllegalStateException.
        (format "%s.%s has dependencies but is not a map/record: %s"
                (.getSimpleName (class system))
                name
                (pr-str component)))))
  component)

(defn- assert-dependencies-valid
  [system component-name using]
  (doseq [k (vals using)
          :let [component (get system k)]]
    (when (nil? component)
      (let [sn (.getSimpleName (class system))]
        (throw
          (IllegalStateException.
            (format "%s.%s is nil (depended on by %s.%s)"
                    sn
                    (name k)
                    sn
                    component-name))))))
  system)

(defn- prepare-components
  [system components required?]
  (reduce
    (fn [system {:keys [key name default using]}]
      (if (required? key)
        (-> system
            (assert-dependencies-valid name using)
            (update key #(-> %
                             (or default)
                             (assert-valid system name using)
                             (component/using using))))
        system))
    system components))

(defn start-system
  [components system]
  (let [active (or (subsystem/active-components system)
                   (map :key components))
        required? (analysis/required-components components active)]
    (-> system
       (prepare-components components required?)
       (subsystem/set-active-components required?)
       (component/start-system required?))))

;; ## Shutdown

(defn- clean-components
  [system components]
  (component/update-system-reverse
    system
    (filter #(get system %) components)
    identity))

(defn stop-system
  [system]
  (let [active (subsystem/active-components system)]
    (-> system
        (component/stop-system active)
        (clean-components active))))

;; ## Macro

(doto
  (defmacro ^{:added "0.5.0"} defsystem+
    "Create a new system of components. Example:

         (defsystem+ MySystem [^:global configuration, api-options]
           :engine    []
           :monitor   [:scheduler :api]
           :api       [:engine {:options :api-options}]
           :scheduler [:engine])

     On startup, the following injections will happen:

     - `:configuration` into `:engine`, `:api` and `:scheduler` (since it is
       marked as `:global`),
     - `:engine` into `:api` and `:scheduler`,
     - `:api-options` - aliased as `:options` - into `:api`,
     - `:scheduler` and `:api` into `:monitor`.

     It is possible to give default values for each component using the `:default`
     metadata:

         (defsystem+ MySystem [...]
           :engine ^{:default (default-engine)} []
           ...)

     Unless the key `:engine` is present and non-nil on system startup, it'll
     be filled with the result of `(default-engine)`.
     "
    [id & args]
    (let [{:keys [dependencies components specifics]} (analysis/analyze args)
          fields (->> (map (comp symbol :name) components)
                      (concat dependencies)
                      (distinct))]
      `(defcomponent ~id [~@fields]
         :peripheral/start #(start-system [~@components] %)
         :peripheral/stop  #(stop-system %)

         ~@specifics)))
  (alter-meta!
    assoc
    :arglists
    '([id & components]
      [id [dependency ...] & components])))
