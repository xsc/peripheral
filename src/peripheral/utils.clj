(ns peripheral.utils)

(defn var->class
  "Convert var to class name."
  [v]
  (-> (str v)
      (.substring 2)
      (.replace "/" ".")
      (.replaceAll "-" "_")))

(defn is-class-name?
  "Check whether the given symbol represents a class name."
  [s]
  (when (symbol? s)
    (when-let [v (resolve s)]
      (cond (class? v) true
            (var? v) (let [class-name (var->class v)]
                       (try (Class/forName class-name) (catch Throwable _)))
            :else false))))
