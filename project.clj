(defproject peripheral "0.4.4"
  :description "System Creation for `stuartsierra/component`."
  :url "https://github.com/xsc/peripheral"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [com.stuartsierra/component "0.2.3"]
                 [potemkin "0.4.1"]]
  :profiles {:dev {:dependencies [[midje "1.7.0"]]
                   :plugins [[lein-midje "3.1.3"]]}
             :1.5 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}}
  :aliases {"test" "midje"
            "all"  ["with-profile" "+1.5:+1.6:+dev"]}
  :pedantic? :abort)
