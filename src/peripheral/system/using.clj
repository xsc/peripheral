(ns peripheral.system.using)

(defprotocol SystemUsable
  (to-system-using [value]))

(extend-protocol SystemUsable
  clojure.lang.Sequential
  (to-system-using [sq]
    (into {} (map to-system-using) sq))

  clojure.lang.IPersistentMap
  (to-system-using [m]
    {:pre [(every? keyword? (keys m))
           (every? keyword? (vals m))]}
    m)

  clojure.lang.Keyword
  (to-system-using [k]
    {k k})

  clojure.lang.Symbol
  (to-system-using [s]
    (let [k (-> s name keyword)]
      {k k}))

  nil
  (to-system-using [_]
    {}))
