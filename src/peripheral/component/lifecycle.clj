(ns peripheral.component.lifecycle
  (:require [com.stuartsierra.component :as component]))

;; ## Component Class Consistency

(defn assert-class-consistency
  "Make sure both components have the same class, otherwise throw
   Exception."
  [k component component']
  (if component'
    (if (= (class component') (class component))
      component'
      (throw
        (IllegalStateException.
          (format "component class changed from %s to %s in %s handler."
                  (class component)
                  (class component')
                  k))))
    component))

;; ## Exceptions

(defn- simple-class-name
  [v]
  (.getSimpleName (class v)))

(defn- extend-lifecycle-exception
  [^Throwable t fqn]
  (let [data (ex-data t)]
    (ex-info
      (format "%s > %s" fqn (.getMessage t))
      (update data ::ex-path conj fqn)
      (.getCause t))))

(defn- create-lifecycle-exception
  [^Throwable t fqn]
  (let [^Throwable root-cause
        (loop [^Throwable t t]
          (if-let [cause (.getCause t)]
            (recur cause)
            t))]
    (ex-info
      (format "%s > %s [%s]"
              fqn
              (.getMessage root-cause)
              (simple-class-name root-cause))
      {::ex-path (list fqn)}
      t)))

(defn- lifecycle-exception
  ([fqn t]
   (if (-> t ex-data ::ex-path)
     (extend-lifecycle-exception t fqn)
     (create-lifecycle-exception t fqn)))
  ([component field ^Throwable t]
   (lifecycle-exception
     (str (simple-class-name component) "." (name field))
     t)))

;; ## Multiple Components

(defn stop-all
  "Stop all components, ignoring exceptions."
  [component-seq]
  (doseq [component component-seq]
    (try
      (component/stop component)
      (catch Throwable _))))

(defn concurrently
  "Tag the seq of components as elligible to be concurrently started.
  The return value is the same sequence of components, but #'start-all
  will start them concurrently."
  [component-seq]
  (vary-meta component-seq assoc ::concurrently true))

(defn- concurrently? [component-seq]
  (::concurrently (meta component-seq)))

(defn- start-all-concurrently [component-seq]
  (let [start-one (fn [index component]
                    (future
                      (try
                        (component/start component)
                        (catch Throwable t
                          ^::error [t index]))))
        {red true, green nil} (->> (map-indexed start-one component-seq)
                                   doall
                                   (map deref)
                                   (group-by (comp ::error meta)))]
    (if (empty? red)
      green
      (let [[throwable index] (first red)]
        (stop-all green)
        (throw
          (lifecycle-exception
            (str "[" index "]")
            throwable))))))

(defn- start-all-sequentially [component-seq]
  (reduce
    (fn [started [index component]]
      (try
        (conj started (component/start component))
        (catch Throwable t
          (stop-all started)
          (throw
            (lifecycle-exception
              (str "[" index "]")
              t)))))
    []
    (map vector (range) component-seq)))

(defn start-all
  "Try to start all components in the given seq; shutting them down again
   if any startup fails."
  [component-seq]
  (if (concurrently? component-seq)
    (start-all-concurrently component-seq)
    (start-all-sequentially component-seq)))

;; ## Fields

(defn- assoc-component
  [component {:keys [record?]} k v]
  (if record?
    (assoc component k v)
    component))

(defn- start-field
  "Given a component, field key, start and stop fns, try to start up
   the field. Returns a tuple of `[new-component field-cleanup-fn]`."
  [component k {:keys [start stop] :as spec}]
  (let [value (start component)
        component' (assoc-component component spec k value)
        cleanup! (if stop
                   #(stop component' value)
                   (constantly nil))]
    [component' cleanup!]))

(defn- silently-call-all
  "Call the given fns, ignoring any exceptions."
  [fns]
  (doseq [f fns]
    (try
      (f)
      (catch Throwable _))))

(defn- start-field-with-cleanup
  "Start field within the given component, calling all cleanup-fns if
   anything went wrong."
  [component field spec cleanup-fns]
  (try
    (start-field component field spec)
    (catch Throwable t
      (silently-call-all cleanup-fns)
      (throw (lifecycle-exception component field t)))))

(defn start-fields
  "Start all fields within the given component. Expects a seq of
   `[field {:start ..., :stop ...}]` tuples."
  [component field->fns]
  (loop [component component
         remaining field->fns
         cleanup-fns []]
    (if (seq remaining)
      (let [[[field spec] & rst] remaining
            [component' cleanup!] (start-field-with-cleanup
                                    component
                                    field
                                    spec
                                    cleanup-fns)]
        (recur component' rst (cons cleanup! cleanup-fns)))
      component)))

(defn- stop-field
  "Stop a single field, returning a tuple of
   `[false new-component]` or `[true exception]`."
  [component field {:keys [stop] :as spec}]
  (let [current-value (get component field)]
    (if stop
      (try
        [false (->> (stop component current-value)
                    (assoc-component component spec field))]
        (catch Throwable t
          [true t]))
      [false (assoc-component component spec field nil)])))

(defn stop-fields
  "Stop all fields within the given component. Expects the same
   input as `start-fields`."
  [component field->fns]
  (loop [component component
         remaining (reverse field->fns)
         failed {}]
    (if (seq remaining)
      (let [[[field spec] & rst] remaining
            [failed? value] (stop-field component field spec)]
        (if failed?
          (recur component rst (assoc failed field value))
          (recur value rst failed)))
      (if (seq failed)
        (throw
          (ex-info
            "parts of the component could not be stopped."
            {:component component
             :failures failed}))
        component))))

;; ## Passive Lifecycle

(defn passive-lifecycle-fn
  "Create passive lifecycle function, returning the input untouched - unless
   `pre-fn`, a preprocessor fn, is given."
  [pre-fn f]
  {:pre [(or (nil? pre-fn) (fn? pre-fn)) (fn? f)]}
  (if pre-fn
    (fn [this]
      (let [this' (or (pre-fn this) this)]
        (f)
        this'))
    (fn [this]
      (f)
      this)))

;; ## Active Lifecycle

(defn active-lifecycle-fn
  "Create active lifecycle function, allowed to modify the input component."
  [pre-fn f]
  {:pre [(or (nil? pre-fn) (fn? pre-fn)) (fn? f)]}
  (if pre-fn
    (fn [this]
      (let [this' (or (pre-fn this) this)]
        (or (f this') this')))
    (fn [this]
      (or (f this) this))))

;; ## Lifecycle Run

(defn- call-lifecycle!
  [k f component]
  (try
    (f component)
    (catch Throwable e
      (throw
        (IllegalStateException.
          (format "in lifecycle function %s > %s"
                  k
                  (.getMessage e))
          e)))))

(defn apply-lifecycle
  "Apply the given lifecycle function."
  [component lifecycles k]
  (if-let [f (get lifecycles k)]
    (->> (call-lifecycle! k f component)
         (assert-class-consistency k component))
    component))
