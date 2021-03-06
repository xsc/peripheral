(ns peripheral.component
  (:require [com.stuartsierra.component :as component]
            [peripheral.component
             [analysis :refer [analyze-component]]
             [attach :as attach]
             [lifecycle :as lifecycle]
             [state :as state]]
            [potemkin :refer [unify-gensyms def-map-type]]))

;; ## Analysis

(defn- wrap-field-access
  "Enable access to component fields and the component itself
   using the given symbols."
  [all-fields this params form]
  `(fn [{:keys [~@all-fields] :as ~this} ~@params]
     ~form))

(defn- prepare-fields
  "Wrap start/stop functions to have access to the current
   field values during startup."
  [fields all-fields this]
  (unify-gensyms
    (vec
      (for [[field {:keys [start stop] :as spec}] fields]
        (->> {:start (wrap-field-access all-fields this [] start)
              :stop  (when stop
                       (wrap-field-access
                         all-fields this `[v##]
                         `(~stop v##)))}
             (into spec)
             (vector field))))))

(defn- prepare-lifecycle
  "Wrap lifecycle function to have access to the field values at
   time of calling."
  [lifecycle all-fields this]
  (unify-gensyms
    (->> (for [[k form] lifecycle]
           [k (wrap-field-access all-fields this [] `(~form ~this))])
         (into {}))))

(defn- analyze
  "Run analysis on component and prepare results for code generation."
  [dependencies component-logic]
  (let [{:keys [fields this]
         :or {this (gensym "this")}
         :as logic} (analyze-component component-logic)
        record-fields (->> fields
                           (filter (comp :record? second))
                           (map (comp symbol name first))
                           (concat dependencies)
                           (distinct))]
    (-> logic
        (update-in [:fields] prepare-fields record-fields this)
        (update-in [:lifecycle] prepare-lifecycle record-fields this)
        (assoc :this this)
        (assoc :record-fields record-fields))))

;; ## Startup Logic

(defn- generate-start
  "Generate startup logic."
  [{:keys [fields lifecycle]} component]
  `(if-not (state/running? ~component)
     (let [lifecycles# ~(select-keys lifecycle [:start :started])]
       (-> ~component
           (lifecycle/apply-lifecycle lifecycles# :start)
           (lifecycle/start-fields    ~fields)
           (attach/start-attached-components)
           (lifecycle/apply-lifecycle lifecycles# :started)
           (state/set-started)))
     ~component))

;; ## Shutdown Logic

(defn- generate-stop
  "Generate shutdown logic."
  [{:keys [fields this lifecycle]} component]
  `(if (state/running? ~component)
     (let [lifecycles# ~(select-keys lifecycle [:stop :stopped])]
       (-> ~component
           (lifecycle/apply-lifecycle lifecycles# :stop)
           (attach/stop-attached-components)
           (lifecycle/stop-fields     ~fields)
           (lifecycle/apply-lifecycle lifecycles# :stopped)
           (state/set-stopped)))
     ~component))

;; ## `defcomponent`

(defmacro defcomponent
  "Create new component type from a vector of `dependencies` (the components/fields that should
   be filled before the component is started, as well as a series of keyword/start/stop forms.

   Each keyword has to be followed by a form that will be executed at startup and optionally a
   (non-keyword) function to be run at shutdown.

   ```
   (defcomponent LoopRunner [f interval]
     :data   (fetch-some-data!)
     :thread (doto (Thread. #(while (not (.isInterrupted ...)) ...))
               (.start))
             #(.interrupt ^Thread %))
   ```

   The results of the form/function will be assoc'd into the component. Please use the `map->...`
   function to create an instance of the record. Using fields with the namespace `peripheral` you
   can manipulate the component record directly:

   ```
   (defcomponent TestComponent [...]
     :peripheral/start   #(...)      ;; called before fields are initialized
     :peripheral/started #(...)      ;; called after fields are initialized
     :peripheral/stop    #(...)      ;; called before fields are cleaned up
     :peripheral/stopped #(...))     ;; called after fields are cleaned up
   ```

   Note that these take a function, not a form, and only allow for one value!

   If you don't need to alter the component record, the 'on' prefix can be used to directly
   execute forms:

   ```
   (defcomponent TestComponent [...]
     :on/start (println \"starting\"))
   ```

   Finally, if you need access to the whole component, you can bind it to a symbol using
   ':this/as':

   ```
   (defcomponent TestComponent [x]
     :this/as *this*
     :y (+ (:x *this*) 10)
     :z (- (:y *this*) 5))
   ```
  "
  [id dependencies & component-logic]
  (let [logic (analyze dependencies component-logic)
        {:keys [record-fields specifics this]} logic]
    (unify-gensyms
      `(defrecord ~id [~@record-fields]
         component/Lifecycle
         (start [this##]
           ~(generate-start logic `this##))
         (stop [this##]
           ~(generate-stop logic `this##))
         ~@specifics))))

;; ## Reification

(def-map-type ReifiedComponent [data start stop]
  (assoc [_ k v]   (ReifiedComponent. (assoc data k v) start stop))
  (dissoc [_ k]    (ReifiedComponent.(dissoc data k) start stop))
  (get [_ k d]     (get data k d))
  (keys [_]        (keys data))
  (meta [_]        (meta data))
  (with-meta [_ m] (ReifiedComponent. (with-meta data m) start stop))

  component/Lifecycle
  (start [_] (ReifiedComponent. (start data) start stop))
  (stop  [_] (ReifiedComponent. (stop data) start stop)))

(alter-meta! #'->ReifiedComponent assoc :private true)

(-> (defmacro reify-component
      "Create a one-off component (i.e. without creating an explicit component
       record). The syntax is identical to [[defcomponent]] without the class name.

       ```
       (let [db-config {...}]
         (reify-component
           :db (connect! db-config) disconnect!
           ...))
       ```

       The resulting value will implement all map interfaces, as well as the
       `com.stuartsierra.component/Lifecycle` protocol."
      [& impl]
      (let [[dependencies component-logic] (if (-> impl first vector?)
                                             [(first impl) (rest impl)]
                                             [nil impl])
            logic (analyze dependencies component-logic)
            {:keys [record-fields specifics this]} logic]
        (unify-gensyms
          `(let [start# (fn [this##]
                          (let [{:keys [~@record-fields]} this##]
                            ~(generate-start logic `this##)))
                 stop# (fn [this##]
                         (let [{:keys [~@record-fields]} this##]
                           ~(generate-stop logic `this##)))]
             (->ReifiedComponent {} start# stop#)))))
    (alter-meta!
      assoc :arglists '([dependencies & component-logic]
                        [& component-logic])))

;; ## Utilities

(defn restart
  "Restart the given component by calling `stop` and `start`."
  [component]
  (-> component
      (component/stop)
      (component/start)))
