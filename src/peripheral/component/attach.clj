(ns peripheral.component.attach
  (:require [com.stuartsierra.component :as component]))

;; ## Attach/Detach
;;
;; If a component A is attached to another component B that means it will be started
;; after B has been and stopped before B.

(defn- add-attach-key
  "Add attach dependencies to the given component's metadata."
  [component k dependencies]
  (vary-meta component assoc-in [::attach k]
             (if (map? dependencies)
               dependencies
               (into {} (map #(vector % %) dependencies)))))

(defn- remove-attach-key
  "Remove attach dependencies from the given component's metadata."
  [component k]
  (vary-meta component update-in [::attach] dissoc k))

(defn start-attached-components
  "Start all attached components."
  [component]
  (loop [component component
         remaining (-> component meta ::attach seq)]
    (if remaining
      (let [[[component-key dependencies] & rst] remaining]
        (when-let [c (get component component-key)]
          (let [component-with-dependencies (reduce
                                              (fn [c [assoc-key deps-key]]
                                                (assoc c assoc-key (get component deps-key)))
                                              c dependencies)
                started    (component/start component-with-dependencies)
                component' (merge component {component-key started})
                ]
            (recur component' rst))))
        component)))

(defn stop-attached-components
  "Stop all attached components."
  [component]
  (->> (for [[component-key dependencies] (-> component meta ::attach)]
         (when-let [c (get component component-key)]
           (let [stopped (component/stop c)]
             (vector
               component-key
               (reduce dissoc stopped (keys dependencies))))))
       (into {})
       (merge component)))

(defn attach
  "Attach the given component to this one (associng it using `k` and having it depend on `dependencies`)."
  ([component k attach-component]
   (attach component k attach-component nil))
  ([component k attach-component dependencies]
   (assert (not (contains? component k)) (str "there is already a component with key " k " attached."))
   (-> component
       (assoc k attach-component)
       (add-attach-key k dependencies))))

(defn detach
  "Detach the given component key from this component."
  [component k]
  (-> component
      (dissoc k)
      (remove-attach-key k)))
