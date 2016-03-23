(defproject peripheral "0.5.0-SNAPSHOT"
  :description "System Creation for `stuartsierra/component`."
  :url "https://github.com/xsc/peripheral"
  :license {:name "MIT License"
            :url "https://opensource.org/licenses/MIT"
            :author "Yannick Scherer"
            :year 2014
            :key "mit"}
  :dependencies [[org.clojure/clojure "1.8.0" :scope "provided"]
                 [com.stuartsierra/component "0.3.1"]
                 [potemkin "0.4.3"]]
  :profiles {:dev {:dependencies [[midje "1.8.3"]]
                   :plugins [[lein-midje "3.1.3"]]
                   :exclusions [org.clojure/clojure]}}
  :aliases {"test" "midje"
            "all"  ["with-profile" "+dev"]}
  :pedantic? :abort)
