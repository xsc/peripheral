(defproject peripheral "0.4.7-SNAPSHOT"
  :description "System Creation for `stuartsierra/component`."
  :url "https://github.com/xsc/peripheral"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.stuartsierra/component "0.3.1"]
                 [potemkin "0.4.3"]]
  :profiles {:dev {:dependencies [[midje "1.8.3"]]
                   :plugins [[lein-midje "3.1.3"]]
                   :exclusions [org.clojure/clojure]}}
  :aliases {"test" "midje"
            "all"  ["with-profile" "+dev"]}
  :pedantic? :abort)
