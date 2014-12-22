(defproject peripheral "0.4.2-SNAPSHOT"
  :description "System Creation for `stuartsierra/component`."
  :url "https://github.com/xsc/peripheral"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.stuartsierra/component "0.2.2"]
                 [potemkin "0.3.11"]]
  :profiles {:dev {:dependencies [[midje "1.6.3" :exclusions [joda-time]]
                                  [joda-time "2.6"]]
                   :plugins [[lein-midje "3.1.3"]]}}
  :aliases {"test" "midje"}
  :pedantic? :abort)
