(defproject peripheral "0.5.1"
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
                   :plugins [[lein-midje "3.1.3"]
                             [lein-codox "0.9.4"]]
                   :codox {:project {:name "peripheral"}
                           :metadata {:doc/format :markdown}
                           :source-uri "https://github.com/xsc/peripheral/blob/clojars-{version}/{filepath}#L{line}"
                           :output-path "doc"
                           :namespaces [peripheral.core]}
                   :exclusions [org.clojure/clojure]}}
  :aliases {"test" "midje"
            "all"  ["with-profile" "+dev"]}
  :pedantic? :abort)
