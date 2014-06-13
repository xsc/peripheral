(defproject peripheral "0.3.0"
  :description "System Creation for `stuartsierra/component`."
  :url "https://github.com/xsc/peripheral"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.stuartsierra/component "0.2.1"]
                 [potemkin "0.3.4"]]
  :profiles {:dev {:dependencies [[midje "1.6.3" :exclusions [joda-time]]
                                  [joda-time "2.3"]]
                   :plugins [[lein-midje "3.1.3"]]}}
  :aliases {"test" "midje"}
  :pedantic? :abort)
