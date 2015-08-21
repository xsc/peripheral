(ns peripheral.component-test
  (:require [midje.sweet :refer :all]
            [peripheral.component
             [attach :refer [attach]]
             [state :refer [running?]]]
            [peripheral.component :refer [defcomponent]]
            [com.stuartsierra.component :refer [start stop]]))

;; ## Basic Functionality

(defcomponent Test [n]
  :n-inc (inc n) dec
  :n-twice (* n-inc 2))

(fact "about 'defcomponent'"
      (let [t (map->Test {:n 0})
            started (start t)
            stopped (stop started)]
        (instance? Test t) => truthy
        (keys t) => (contains #{:n :n-inc :n-twice})
        t =not=> running?
        (:n t) => 0
        (:n-inc t) => nil
        (:n-twice t) => nil

        started => running?
        (:n started) => 0
        (:n-inc started) => 1
        (:n-twice started) => 2

        stopped =not=> running?
        (:n stopped) => 0
        (:n-inc stopped) => 0
        (:n-twice stopped) => nil))

;; ## Field in Dependency Vector + Body

(defcomponent TestOverride [n]
  :n (or n 1))

(fact "about 'defcomponent' + dependency override."
      (let [t (map->TestOverride {:n 0})
            started (start t)]
        (:n started) => 0)
      (let [t (map->TestOverride {})
            started (start t)]
        (:n started) => 1))

;; ## Protocol Implementation

(defprotocol Proto
  (x [this]))

(defcomponent Test [n]
  :a n
  :b (inc a)
  Proto
  (x [_] b))

(fact "about 'defcomponent' + protocol"
      (let [t (map->Test {:n 0})
            started (start t)
            stopped (stop started)]
        (instance? Test t) => truthy
        (satisfies? Proto t) => truthy
        (keys t) => (contains #{:n :a :b})
        (:n t) => 0
        (:a t) => nil
        (:b t) => nil
        (:n started) => 0
        (:a started) => 0
        (:b started) => 1
        (x started) => 1
        (:n stopped) => 0
        (:a stopped) => nil
        (:b stopped) => nil
        (x stopped) => nil))

;; ## Lifecycle

(defcomponent TestActiveLifecycle [state-atom n]
  :peripheral/start (fn [_] (swap! state-atom conj :init) _)
  :peripheral/started (fn [_] (swap! state-atom conj :start) _)
  :peripheral/stop (fn [_] (swap! state-atom conj :stop) _)
  :peripheral/stopped (fn [_] (swap! state-atom conj :done) _)

  :a (do (swap! state-atom conj :init-a) (inc n))
     #(do % (swap! state-atom conj :cleanup-a) nil))

(fact "about 'defcomponent' active lifecycle functions"
      (let [a (atom [])
            t (map->TestActiveLifecycle {:state-atom a :n 0})]
        @a => empty?
        (:n t) => 0
        (:a t) => nil
        (let [started (start t)]
          @a => [:init :init-a :start]
          (:n started) => 0
          (:a started) => 1
          (let [stopped (stop started)]
            @a => [:init :init-a :start :stop :cleanup-a :done]
            (:n stopped) => 0
            (:a stopped) => nil))))

(defcomponent TestPassiveLifecycle [state-atom n]
  :on/start (swap! state-atom conj :init)
  :on/started (swap! state-atom conj :start)
  :on/stop (swap! state-atom conj :stop)
  :on/stopped (swap! state-atom conj :done)

  :a (do (swap! state-atom conj :init-a) (inc n))
     #(do % (swap! state-atom conj :cleanup-a) nil))

(fact "about 'defcomponent' passive lifecycle functions"
      (let [a (atom [])
            t (map->TestPassiveLifecycle {:state-atom a :n 0})]
        @a => empty?
        (:n t) => 0
        (:a t) => nil
        (let [started (start t)]
          @a => [:init :init-a :start]
          (:n started) => 0
          (:a started) => 1
          (let [stopped (stop started)]
            @a => [:init :init-a :start :stop :cleanup-a :done]
            (:n stopped) => 0
            (:a stopped) => nil))))

(defcomponent TestActivePassiveLifecycle [state-atom n]
  :on/start (swap! state-atom conj :init)
  :on/started (swap! state-atom conj :start)
  :on/stop (swap! state-atom conj :stop)
  :on/stopped (swap! state-atom conj :done)

  :peripheral/start (fn [_] (swap! state-atom conj :init-active) _)
  :peripheral/started (fn [_] (swap! state-atom conj :start-active) _)
  :peripheral/stop (fn [_] (swap! state-atom conj :stop-active) _)
  :peripheral/stopped (fn [_] (swap! state-atom conj :done-active) _)

  :on/stopped (swap! state-atom conj :done-final)

  :a (do (swap! state-atom conj :init-a) (inc n))
     #(do % (swap! state-atom conj :cleanup-a) nil))

(fact "about 'defcomponent' active/passive lifecycle function mixing"
      (let [a (atom [])
            t (map->TestActivePassiveLifecycle {:state-atom a :n 0})]
        @a => empty?
        (:n t) => 0
        (:a t) => nil
        (let [started (start t)]
          @a => [:init :init-active :init-a :start :start-active]
          (:n started) => 0
          (:a started) => 1
          (let [stopped (stop started)]
            @a => [:init :init-active :init-a :start :start-active
                   :stop :stop-active :cleanup-a :done :done-active
                   :done-final]
            (:n stopped) => 0
            (:a stopped) => nil))))

(defcomponent TestLifecycleFields [state-atom n]
  :a 1
  :on/start (swap! state-atom conj [:init n a])
  :on/started (swap! state-atom conj [:start n a])
  :on/stop (swap! state-atom conj [:stop n a])
  :on/stopped (swap! state-atom conj [:done n a]))

(fact "about 'defcomponent' field access in lifecycle functions."
      (let [a (atom [])
            t (map->TestLifecycleFields {:state-atom a :n 0})]
        @a => empty?
        (:n t) => 0
        (:a t) => nil
        (let [started (start t)]
          @a => [[:init 0 nil] [:start 0 1]]
          (:n started) => 0
          (:a started) => 1
          (let [stopped (stop started)]
            (drop 2 @a) => [[:stop 0 1] [:done 0 nil]]
            (:n stopped) => 0
            (:a stopped) => nil))))

;; ## Attach/Detach

(defcomponent Test [state-atom]
  :state :started
  :on/start   (swap! state-atom conj :start)
  :on/stop    (swap! state-atom conj :stop)
  :on/started (swap! state-atom conj :started)
  :on/stopped (swap! state-atom conj :stopped))

(defcomponent Attach [parent-state state-atom]
  :on/started (swap! state-atom conj :attach-started)
  :on/stopped (swap! state-atom conj :attach-stopped))

(facts "about 'defcomponent' attach and detach."
       (fact "about dependency map"
         (let [state-atom (atom [])
               t (-> (map->Test {:state-atom state-atom})
                     (attach :child
                             (map->Attach {})
                             {:parent-state :state
                              :state-atom :state-atom }))
               started (start t)
               stopped (stop started)]
           @state-atom => [:start :attach-started :started
                           :stop  :attach-stopped :stopped]

           t =not=> running?
           (-> t :child) =not=> running?
           (-> t :state) => nil
           (-> t :state-atom) => truthy
           (-> t :child) => truthy
           (-> t :child :parent-state) => nil
           (-> t :child :state-atom) => nil

           started => running?
           (-> started :child) => running?
           (-> started :state) => :started
           (-> started :state-atom) => truthy
           (-> started :child :parent-state) => :started
           (-> started :child :state-atom) => truthy

           stopped =not=> running?
           (-> stopped :child) =not=> running?
           (-> stopped :child :parent-state) => nil
           (-> stopped :child :state-atom) => nil))
       (fact "about dependency vector"
         (let [state-atom (atom [])
               t (-> (map->Test {:state-atom state-atom})
                     (attach :child (map->Attach {}) [:state-atom]))
               started (start t)
               stopped (stop started)]
           @state-atom => [:start :attach-started :started
                           :stop  :attach-stopped :stopped]
           (-> started :child :parent-state) => nil
           (-> started :child :state-atom) => truthy)))

;; ## Cleanup

(defcomponent Test [state-atom]
  :f (fn [_ k] (swap! state-atom (fnil conj []) k) _)
  :state0 (f nil :start0) #(f % :stop0)
  :state1 (f nil :start1) #(f % :stop1)
  :state2 (f nil :start2) #(f % :stop2)
  :error (do
           (f nil :before-error)
           (throw (Exception.))))

(fact "about 'defcomponent' initialization errors"
      (let [s (atom [])
            t (map->Test {:state-atom s})]
        (start t) => (throws Exception)
        (take 3 @s) => [:start0 :start1 :start2]
        (nth @s 3) => :before-error
        (drop 4 @s) => [:stop2 :stop1 :stop0]))

;; ## Class Check

(defcomponent TestStart []
  :peripheral/start (constantly {}))

(defcomponent TestStarted []
  :peripheral/started (constantly {}))

(defcomponent TestStop []
  :peripheral/stop (constantly {}))

(defcomponent TestStopped []
  :peripheral/stopped (constantly {}))

(tabular
  (fact "about 'defcomponent' class change detection in startup and shutdown."
        (?f (?constructor {})) => (throws Exception #"component class changed"))
  ?constructor           ?f
  map->TestStart         start
  map->TestStarted       start
  map->TestStop          (comp stop start)
  map->TestStopped       (comp stop start))

;; ## This

(defcomponent ThisTest [x]
  :this/as *this*
  :y (+ (:x *this*) 10)
  :z (- (:y *this*) 5)
  :v *this*)

(fact "about binding the whole component to a symbol."
      (let [t (map->ThisTest {:x 1})
            started (start t)]
        (:x started) => 1
        (:y started) => 11
        (:z started) => 6
        (class (:v started)) => ThisTest))

;; ## Component Seqs

(defcomponent TestSeqElement [fail? state-atom]
  :on/start
  (when fail?
    (throw (Exception.)))
  :on/started
  (swap! state-atom conj :element-started)
  :on/stopped
  (swap! state-atom conj :element-stopped))

(defcomponent TestSeq [n state-atom fail?]
  :components/children
  (concat
    (repeatedly (dec n) #(map->TestSeqElement {:state-atom state-atom}))
    [(map->TestSeqElement {:fail? fail?, :state-atom state-atom})]))

(facts "about instantiating a variable-length seq of components."
       (fact "about successful startup."
         (let [state-atom (atom [])
               t (map->TestSeq {:n 5, :state-atom state-atom})
               started (start t)
               stopped (stop started)
               started-children (:children started)
               stopped-children (:children stopped)]
           stopped-children => nil?

           (count started-children) => 5
           started-children => (has every? #(instance? TestSeqElement %))
           started-children => (has every? running?)

           (count @state-atom)  => 10
           (set (take 5 @state-atom)) => #{:element-started}
           (set (drop 5 @state-atom)) => #{:element-stopped}))
       (fact "about cleanup after exception."
         (let [state-atom (atom [])
               t (map->TestSeq {:n 5, :fail? true, :state-atom state-atom})]
           (start t) => (throws IllegalStateException #"in 'children'")

           (count @state-atom)  => 8
           (set (take 4 @state-atom)) => #{:element-started}
           (set (drop 4 @state-atom)) => #{:element-stopped})))
