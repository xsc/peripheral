# peripheral

__peripheral__ is a small library that aims to facilitate the creation of components and component systems using
Stuart Sierra's [component](https://github.com/stuartsierra/component) library.

[![Build Status](https://travis-ci.org/xsc/peripheral.png)](https://travis-ci.org/xsc/peripheral)
[![endorse](https://api.coderwall.com/xsc/endorsecount.png)](https://coderwall.com/xsc)

## Usage

__Leiningen (via [Clojars](https://clojars.org/peripheral))__

```clojure
[peripheral "0.2.1"]
```

### `defcomponent`

The `defcomponent` macro can provide a concise way of defining components. By providing
dependencies (i.e. components or pieces of data that have to be initialized in advance) and
stateful fields separately, startup and shutdown functions can be generated automatically.

```clojure
(require '[peripheral.core :as peripheral :refer [defcomponent]])

(defcomponent Consumer [input-queue]
  :data   (atom [])
  :thread (doto (Thread. #(consumer-loop input-queue data))
            (.start))
          #(.interrupt ^Thread %))

(def my-consumer (map->Consumer {:input-queue ...}))
(alter-var-root #'my-consumer peripheral/start)

(:thread my-consumer) ;; => #<Thread ...>
@(:data my-consumer)  ;; => []
```

Component data flows top-to-bottom, meaning that fields that come later in the list can rely on those preceding them (and refer
to them by their symbol).

### Component Startup/Shutdown

Sometimes it is necessary to modify a component as a whole to achieve a certain lifecycle. This
can be done by using the following special keywords in the body of `defcomponent`:

- `:peripheral/start`: called at the beginning of the `start` operation (before any fields are initialized);
- `:peripheral/started`: called at the end of the `start` operation (after all fields are initialized);
- `:peripheral/stop`: called at the beginning of the `stop` operation (before any fields are cleaned up);
- `:peripheral/stopped`: called at the end of the `stop` operation (after all fields are cleaned up).

### Components + Protocols

`defsystem` and `defcomponent` allow for a series of `defrecord`-like protocol implementations following
their description, e.g.:

```clojure
(defcomponent DerefComponent [initial-value]
  :data (atom initial-value)

  clojure.lang.IDeref
  (deref [_] @data))

(def my-component (map->DerefComponent {:initial-value 123}))
(alter-var-root #'my-component peripheral/start)

@(:data my-component) ;; => 123
@my-component         ;; => 123
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
(defcomponent DataComponent []
  :data   (atom 0)
  :thread (future
           (dotimes [n 100]
             (Thread/sleep 1000)
             (swap! data inc))))

(defcomponent AtomObserver [observed-atom tries]
  :thread (future
           (dotimes [n tries]
             (Thread/sleep 2000)
             (println "observed value:" @observed-atom))))

(def observer (map->AtomObserver {:tries 10}))
(def data-component
  (-> (map->DataComponent {})
      (peripheral/attach :observer observer {:observed-atom :data})))
```

`attach` takes the component to attach to, the key to use and the component to be attached as parameters, as well as a
map or vector of dependencies (see `com.stuartsierra.component/using`) and uses `assoc` to update the parent component.
When this ad-hoc couple is started `DataComponent`'s atom will be injected as `AtomObserver`'s `:observed-atom` and you'll
see a printout of the current value every 2 seconds:

```clojure
(peripheral/start data-component)
;; observed value: 2
;; observed value: 3
;; observed value: 5
;; observed value: 8
;; observed value: 9
;; observed value: 11
;; ...
```

### `defsystem`

The `defsystem` macro helps with declarativley building up your system:

```clojure
(require '[peripheral.core :as peripheral :refer [defsystem connect]])

(defsystem Sys [^:global ^:data config
                ^:global thread-pool
                a b c]
  (connect :a :source :c))
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
(peripheral/start system)
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
(def c-only (peripheral/subsystem system [:c]))
(peripheral/start c-only)
;; starting :thread-pool using configuration: {:config-key config-value}
;; starting :c using configuration: {:config-key config-value}
;; => #user.Sys{:config {:config-key "config-value"}, ...}
```

(You can achieve the same by creating a system record that contains the keys of the components to be started, of course,
but peripheral offers you automatic dependency resolution which is nice, I guess.)

## License

Copyright &copy; 2014 Yannick Scherer

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
