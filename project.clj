(defproject peripheral "0.4.3"
  :description "System Creation for `stuartsierra/component`."
  :url "https://github.com/xsc/peripheral"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.stuartsierra/component "0.2.3"]
                 [potemkin "0.3.13"]]
  :profiles {:dev {:dependencies [[midje "1.6.3" :exclusions [joda-time]]
                                  [joda-time "2.7"]]
                   :plugins [[lein-midje "3.1.3"]]}}
  :aliases {"test" "midje"}
  :pedantic? :abort)
