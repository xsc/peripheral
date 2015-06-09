# peripheral

__peripheral__ is a small library that aims to facilitate the creation of components and component systems using
Stuart Sierra's [component](https://github.com/stuartsierra/component) library.

[![Build Status](https://travis-ci.org/xsc/peripheral.svg?branch=master)](https://travis-ci.org/xsc/peripheral)
[![endorse](https://api.coderwall.com/xsc/endorsecount.png)](https://coderwall.com/xsc)

## Usage

__Leiningen (via [Clojars](https://clojars.org/peripheral))__

[![Clojars Project](http://clojars.org/peripheral/latest-version.svg)](http://clojars.org/peripheral)

__REPL__

```clojure
(require '[peripheral.core :as p])
```

## Components

### Creating a Component

Components can be created using `defcomponent`, a macro operating like `defrecord` but allowing
for a map-like declaration of internal state. Each field is associated with an expression to be
run at startup and an optional _function_ to be called at shutdown:

```clojure
(p/defcomponent InternalQueue [max-value]            ;; (1)
  :queue-data (atom [])                              ;; (2)
  :fill-thread
  (future
    (while true
      (Thread/sleep 100)
      (swap! queue-data conj (rand-int max-value)))) ;; (3)
  #(future-cancel %))                                ;; (4)
```

You can see multiple things here:

- dependencies are given in the record field declaration (1),
- stateful fields are declared using keywords and an initialization value (2),
- dependencies and internal fields can be accessed using their symbols (3),
- cleanup is done by supplying a single-parameter function that takes the field's value (4).

Note that component data flows top-to-bottom, so while `:poll-thread`'s initialization has access
to the already initialized value of `:queue-data`, the reverse wouldn't hold.

You can start/stop a component using `peripheral.core/start` and `peripheral.core/stop` which are
just calls to `com.stuartsierra.component`'s startup/shutdown functions:

```clojure
(def component (map->InternalQueue {:max-value 10}))
;; => #user.InternalQueue{:max-value 10, :fill-thread nil, :queue-data nil}

(alter-var-root #'component p/start)
;; => #user.InternalQueue{:max-value 10,
;;                        :fill-thread #<...>,
;;                        :queue-data #<Atom@51cdf94: []>}

(Thread/sleep 500)
component
;; => #user.InternalQueue{:max-value 10,
;;                        :fill-thread #<...>,
;;                        :queue-data #<Atom@51cdf94: [6 0 3 5 4 3]>}

(alter-var-root #'component p/stop)
;; => #user.InternalQueue{:max-value 10, :fill-thread nil, :queue-data nil}
```

Before startup, both stateful fields are `nil`, afterwards they are initialized with whatever value
was desired, before being cleaned up again (or replaced with the values of the cleanup functions).

### Protocol Implementation

`defsystem` and `defcomponent` allow for a series of `defrecord`-like protocol implementations following
their description, e.g.:

```clojure
(p/defcomponent DerefComponent [initial-value]
  :data (atom initial-value)

  clojure.lang.IDeref
  (deref [_] @data))

(def component (map->DerefComponent {:initial-value 123}))
(alter-var-root #'component p/start)

@(:data component) ;; => 123
@component         ;; => 123
```

### Subcomponents

Sometimes you want to start a component within the context of another one (and a system is to heavy-weight for
your specific use case). By prefixing the field name with `:component/`, peripheral will automatically call
start and stop functions on the given value:

```clojure
(p/defcomponent Parent [data]
  :component/child1 (map->Child (assoc data :name "child-1"))
  :component/child2 (map->Child (assoc data :name "child-2")))
```

This expands to:

```clojure
(p/defcomponent Parent [data]
  :child1
  (p/start (map->Child (assoc data :name "child-1")))
  #(p/stop %)
  :child2
  (p/start (map->Child (assoc data :name "child-2")))
  #(p/stop %))
```

### Lifecycle

#### Active Lifecycle

If you want to modify a component as a whole, you can use the prefix `:peripheral/` with one of the
following keys to run a single-parameter function on the current state of the component:

- `start`: called before any fields are initialized,
- `started`: called after all fields have been initialized,
- `stop`: called before any fields are cleaned up,
- `stopped`: called after all fields have been cleaned up.

You could use these, for example, to trigger a post-initialization action and store the result:

```clojure
(p/defcomponent ActiveLifecycle []
  :run?    (promise) #(deliver % false)
  :results (initialize-results!)
  :runner  (future (when @run? (do-it!)))

  :peripheral/started
  (fn [this]
    (deliver run? true)
    (update-in this [:results] @runner)))
```

As you can see, the function has direct access to the fields using their symbols (but they will be
uninitialized or cleaned up in `:start` and `:stopped`).

Note that most of the time, you don't need this kind of logic since you can achieve the majority
of goals by adjusting initialization order or using a passive lifecycle handler (see below). Also,
since you can modify the component in any way you want you have to be extra careful to not mess up
the automatic cleanup mechanisms.

#### Passive Lifecycle

By using the prefix `:on/` you can run a single form at specific points during initialization and
cleanup (see the above section for values):

```clojure
(p/defcomponent PassiveLifecycle []
  :on/start   (debug "starting up ...")
  :on/stopped (debug "shut down.))
```

#### Intermittent Steps

There is no special syntax for running a piece of code _in-between_ the initialization of two fields
but there are some strategies one could employ. E.g., you could add the logic to the initialization of
a subsequent field:

```clojure
(p/defcomponent Steps []
  :x 0
  :y (do
      (prn x)
      (+ x 10)))
```

Alternatively, you can create a dummy fields and "initialize" it using the piece of code you want to run:

```clojure
(p/defcomponent Steps []
  :x 0
  :_ (prn x)
  :y (+ x 10))
```

If you run into this situation a lot, it might be an indicator that you should split the respective
component into smaller ones.

### This

You can access the current state of the component record by binding it to a symbol using `:this/as`:

```clojure
(defn print-name
  [{:keys [first-name last-name]}]
  (printf "%s, %s%n" last-name first-name))

(p/defcomponent NameComponent [first-name last-name]
  :this/as    *this*
  :reversed   (apply str (reverse (:first-name *this*)))
  :on/started (print-name *this*))

(p/start
  (map->NameComponent
    {:first-name "Some"
     :last-name "One"}))
;; One, Some
;; => #user.NameComponent{:reversed "emoS", :first-name "Some", :last-name "One"}
```

### Attach/Detach (ad-hoc Coupling)

Sometimes you want to reuse parts of a component. For example, there might be an event bus that is created by a component
`S` and you want to attach a reporting component `S` directly to said bus. It would make sense to start up and shut down `R`
 and `S` together and to offer `R` access to the event bus but `S` does not accomodate for the existence of `R` (it does not
contain logic to start up `R` once the event-bus is ready). One could create another component that manually handles dependency
injection and starts the two in the right order but that is more tedious than necessary.

`peripheral.core/attach` can be used with any component defined using `defcomponent` and couples two components in the way
described above: the attached component gets started once the parent component is ready with dependencies already injected:

```clojure
(p/defcomponent DataComponent []
  :data   (atom 0)
  :thread (future
           (dotimes [n 100]
             (Thread/sleep 1000)
             (swap! data inc))))

(p/defcomponent AtomObserver [observed-atom tries]
  :thread (future
           (dotimes [n tries]
             (Thread/sleep 2000)
             (println "observed value:" @observed-atom))))

(def observer (map->AtomObserver {:tries 10}))
(def data-component
  (-> (map->DataComponent {})
      (p/attach :observer observer {:observed-atom :data})))
```

`attach` takes the component to attach to, the key to use and the component to be attached as parameters, as well as a
map or vector of dependencies (see `com.stuartsierra.component/using`) and uses `assoc` to update the parent component.
When this ad-hoc couple is started `DataComponent`'s atom will be injected as `AtomObserver`'s `:observed-atom` and you'll
see a printout of the current value every 2 seconds:

```clojure
(p/start data-component)
;; observed value: 2
;; observed value: 3
;; observed value: 5
;; observed value: 8
;; observed value: 9
;; observed value: 11
;; ...
```

## Systems

### Creating a System

The `defsystem` macro helps with declarativley building up your system:

```clojure
(p/defsystem Sys [^:global ^:data config
                  ^:global thread-pool
                  a b c]
  (p/connect :a :source :c))
```

There are two types of metadata for the system fields: `:data` which marks a plain data field (as opposed to a system component)
and `:global` which enables automatic dependency injection to all components. As you can see, it is possible to combine both kinds
to have e.g. system-wide configurations.

`connect` can be used to create a specific relationship inside the system. `(connect :a :source :c)` means "`:a` depends on `:c` and
expects it to be injected at its key `:source`. There is also a two-argument version of `connect` which just uses the destination
component's key/ID for injection.

Systems, like components, can implement protocols and interfaces by appending them to the relationship specification.

### Example

Let's create an instance of the above system using a dummy component `X` that prints its name and configuration on startup:

```clojure
(defrecord X [name config thread-pool]
  com.stuartsierra.component/Lifecycle
  (start [this]
    (println "starting" name "using configuration:" config)
    this))
(def make-x #(X. %1 nil nil))
```

As `Sys` is a normal record we can use Clojure's constructor functions:

```clojure
(def system
  (map->Sys {:thread-pool (make-x :thread-pool)
             :a           (make-x :a)
             :b           (make-x :b)
             :c           (make-x :c)
             :config      {:config-key "config-value"}}))
```

On startup, the configuration will be distributed to all components and thread pool will be usable by `:a`, `:b` and `:c`
(`peripheral.core/start` is just a reference to `com.stuartsierra.component/start`):

```clojure
(p/start system)
;; starting :thread-pool using configuration: {:config-key config-value}
;; starting :b using configuration: {:config-key config-value}
;; starting :c using configuration: {:config-key config-value}
;; starting :a using configuration: {:config-key config-value}
;; => #user.Sys{:config {:config-key "config-value"}, ...}
```

We can check if all dependencies are correct (although that reponsibility lies by `stuartsierra/component`):

```clojure
(map (comp :name :thread-pool #(% system)) [:a :b :c])
;; => (:thread-pool :thread-pool :thread-pool)
```

### Subsystems

Sometimes you do not want to start all components in your system. For example, a system might consist of a producer,
a queue and a consumer. The queue might be implemented in a persistent way (using the filesystem, RabbitMQ, Redis, ...),
meaning that producer and consumer do not have to reside inside the same process or on the same node. So, a producer
system only needs the producer and queue components, whilst the consumer system only relies on queue and consumer
components.

Instead of creating three systems (`producer-consumer`, `producer` and `consumer`) you can use peripheral's subsystem
functionality that will only start the components you want (and their dependencies). Using the var `system` from the above
example this might look like the following:

```clojure
(def c-only (p/subsystem system [:c]))
(p/start c-only)
;; starting :thread-pool using configuration: {:config-key config-value}
;; starting :c using configuration: {:config-key config-value}
;; => #user.Sys{:config {:config-key "config-value"}, ...}
```

(You can achieve the same by creating a system record that contains the keys of the components to be started, of course,
but peripheral offers you automatic dependency resolution which is nice, I guess.)

## License

Copyright &copy; 2014-2015 Yannick Scherer

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
