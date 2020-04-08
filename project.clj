(defproject peripheral "0.5.4"
  :description "Component & System Creation for Stuart Sierra's Component Library."
  :url "https://github.com/xsc/peripheral"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :author "Yannick Scherer"
            :year 2014
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [com.stuartsierra/component "0.3.1"]
                 [potemkin "0.4.3"]]
  :profiles {:dev {:dependencies [[midje "1.8.3"]
                                  [org.clojure/tools.namespace "0.2.11"]]
                   :plugins [[lein-midje "3.1.3"]]
                   :exclusions [org.clojure/clojure]
                   :global-vars {*warn-on-reflection* true}}
             :codox {:dependencies [[org.clojure/tools.reader "1.0.0-beta2"]
                                    [codox-theme-rdash "0.1.1"]]
                     :plugins [[lein-codox "0.10.2"]]
                     :codox {:project {:name "peripheral"}
                             :metadata {:doc/format :markdown}
                             :themes [:rdash]
                             :source-paths ["src"]
                             :source-uri "https://github.com/xsc/peripheral/blob/master/{filepath}#L{line}"
                             :namespaces [peripheral.core]}}}
  :aliases {"test"  "midje"
            "all"   ["with-profile" "+dev"]
            "codox" ["with-profile" "codox" "codox"]}
  :pedantic? :abort)
