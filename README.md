# peripheral

__peripheral__ is a small library that aims to facilitate the creation of component systems using
Stuart Sierra's [component](https://github.com/stuartsierra/component) library.

[![Build Status](https://travis-ci.org/xsc/peripheral.png)](https://travis-ci.org/xsc/peripheral)
[![endorse](https://api.coderwall.com/xsc/endorsecount.png)](https://coderwall.com/xsc)

## Usage

__peripheral__ offers ways to specify systems of components and their interconnections.

__Note that some of the things described here are works in progress!__

### Create a System

Systems typically consist of different types of entities:

- __configuration__: datastore access has to be configured, server ports, etc... Configurations can be
  static or be loaded at system startup, and should be accessible by every component in the system,
- __global components__: these should be available to all parts of the system (e.g. thread pool,
  message bus, datastore, ...);
- __other components__: these fulfill specific tasks, interacting with other parts of the system.

Let's say we want to have a system that consists of the components `:a`, `:b` and `:c`, backed by some
kind of thread pool, where `:a` gets data from `:c`. Also, the whole thing is configured by an option map.
Using `stuartsierra/component` directly this might look like the following:

```clojure
(require '[com.stuartsierra.component :as component])

(defrecord Sys [config a b c thread-pool]
  component/Lifecycle
  (start [this]
    (let [system-with-config (reduce
                               (fn [this k]
                                 (update-in this [k] #(assoc % :config config)))
                               this [:a :b :c :thread-pool])
          system (component/system-using system-with-config
                                         {:a {:source :c :thread-pool :thread-pool}
                                          :b {:thread-pool :thread-pool}
                                          :c {:thread-pool :thread-pool}])]
      (component/start-system system [:a :b :c :thread-pool])))
  (stop [this]
    (component/stop-system this [:a :b :c :thread-pool])))
```

There is a fair amount of repetition in here which `peripheral.core/defsystem` tries to address:

```clojure
(require '[peripheral.core :refer [defsystem]])

(defsystem Sys [^:config config
                ^:global thread-pool
                a b c]
  (connect :a :source :c))
```

The configuration will be `assoc`'d automatically, the dependency map is created using the `^:global` metadata
and the `connect` statement.

## License

Copyright &copy; 2014 Yannick Scherer

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
