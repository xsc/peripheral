# peripheral

__peripheral__ is a small library that aims to facilitate the creation of component systems using
Stuart Sierra's [component](https://github.com/stuartsierra/component) library.

[![Build Status](https://travis-ci.org/xsc/peripheral.png)](https://travis-ci.org/xsc/peripheral)
[![endorse](https://api.coderwall.com/xsc/endorsecount.png)](https://coderwall.com/xsc)

## Usage

__Leiningen (via [Clojars](https://clojars.org/peripheral))__

```clojure
[peripheral "0.1.1"]
```

### `defsystem`

The `defsystem` macro helps with declarativley building up your system:

```clojure
(require '[peripheral.core :as peripheral :refer [defsystem connect]])

(defsystem Sys [^:config config
                ^:global thread-pool
                a b c]
  (connect :a :source :c))
```

This creates a simple Clojure record type `Sys` with the fields given in the system vector. Note the metadata
attached to the single fields: `:global` marks this as a component that should be accessible by all other ones,
while `:config` designated a field as configuration data; everything else are simple components that can be
further specified using the body of `defsystem`:

- `(peripheral.core/connect src k dst)`: make the component identified by `dst` a dependency of `src`, i.e. on startup `dst` will
  be assoc'd into `src` using the key `k`;
- `(peripheral.core/connect src dst)`: same as before but `dst` will be used as the key in `src`;
- `(peripheral.core/configure src k dst)`: make the configuration identified by `dst` a dependency of `src`, i.e. on startup the
  configuration will be assoc'd into `src` using the key `k`;
- `(peripheral.core/configure src dst)`: same as before but `dst` will be used as the key in `src`.

By default, every component marked as `:global` will be connected to every normal one, and every configuration marked by `:config`
will be used for every component (including global ones).

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
(alter-var-root #'system peripheral/start)
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

### `start-subsystem`

Sometimes you do not want to start all components in your system. For example, a system might consist of a producer,
a queue and a consumer. The queue might be implemented in a persistent way (using the filesystem, RabbitMQ, Redis, ...),
meaning that producer and consumer do not have to reside inside the same process or on the same node. So, a producer
system only needs the producer and queue components, whilst the consumer system only relies on queue and consumer
components.

Instead of creating three systems (`producer-consumer`, `producer` and `consumer`) you can use peripheral's subsystem
functionality that will only start the components you want (and their dependencies). Using the var `system` from the above
example this might look like the following:

```clojure
(alter-var-root #'system peripheral/start-subsystem [:c])
;; starting :thread-pool using configuration: {:config-key config-value}
;; starting :c using configuration: {:config-key config-value}
;; => #user.Sys{:config {:config-key "config-value"}, ...}
```

(You can achieve the same by creating a system record that contains the keys of the components to be started, of course,
but peripheral offers you automatic dependency resolution which is nice, I guess.)

### Configurations

Fields marked as `:config` have to be filled with values implementing `peripheral.configuration/Configuration`, more specifically
the `load-configuration!` function. As the name says this is supposed to retrieve configuration information on startup (and is
already implemented for maps, which return themselves, and functions, which call themselves without arguments).

```clojure
(defn config! []
  (println "loading configuration ...")
  (Thread/sleep 1000)
  {:config-key "config-value"})

(alter-var-root #'system assoc :config config!)
(alter-var-root #'system peripheral/start)
;; loading configuration ...
;; starting :thread-pool using configuration: {:config-key config-value}
;; starting :b using configuration: {:config-key config-value}
;; starting :c using configuration: {:config-key config-value}
;; starting :a using configuration: {:config-key config-value}
;; => #user.Sys{:config #<user$config_BANG_ user$config_BANG_@5565c037>, ...}
```

### `defcomponent`

In cases where a component consists of one or multiple independent entities, the `defcomponent` macro can provide a concise way
of defining it. By providing dependencies (i.e. components or pieces of data that have to be initialized in advance) and
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
@(:data my-consumer)  ;; => {}
```

Component data flows top-to-bottom, meaning that fields that come later in the list can rely on those preceding them (and refer
to them by their symbol).

### Systems/Components + Protocols

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

## License

Copyright &copy; 2014 Yannick Scherer

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
