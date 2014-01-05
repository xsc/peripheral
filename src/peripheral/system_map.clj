(ns ^{:doc "Creation/Manipulation of the underlying Map describing a System."
      :author "Yannick Scherer"}
  peripheral.system-map)

;; ## System Map Creation

(defn connect
  "Let the component identified by `src-id` be using the component identified
   by `dst-id` as a dependency to be assoc'd in at the key `src-key`."
  ([m src-id dst-id] (connect m src-id dst-id dst-id))
  ([m src-id src-key dst-id]
   (if-not (= src-id dst-id)
     (assoc-in m [:dependencies src-id src-key] dst-id)
     m)))

(defn configure
  "Associate a key in the component identified by `src-id` with the configuration stored
   at `cfg-key`."
  ([m src-id cfg-key] (configure m src-id cfg-key cfg-key))
  ([m src-id src-key cfg-key]
   (assoc-in m [:configurations src-id src-key] cfg-key)))

;; ## Initial System Map

(defn- global?
  "Is the given symbol representing a global component?"
  [sym]
  (-> sym meta :global))

(defn- config?
  "Is the given symbol representing a configuration?"
  [sym]
  (-> sym meta :config))

(defn- initial-dependencies
  "Create initial dependency map."
  [m components]
  (let [{:keys [local global]} (reduce
                                 (fn [m sym]
                                   (let [k (if (global? sym) :global :local)]
                                     (update-in m [k] conj (keyword sym))))
                                 {} components)]
    (->> (for [a local b global] [a b])
         (reduce #(apply connect %1 %2) m)
         (merge {:components (mapv keyword components)}))))

(defn- initial-configurations
  "Create initial configuration map."
  [m components configurations]
  (->> (for [a components b configurations]
         [(keyword a) (keyword b)])
       (reduce #(apply configure %1 %2) m)))

(defn initial-system-map
  "Create initial configuration map from seq of component symbols
   (potentially with metadata)."
  [fields]
  (let [components (remove config? fields)
        configurations (filter config? fields)]
    (assert (seq components) "System needs at least a single Component to consist of.")
    (-> {}
        (initial-dependencies components)
        (initial-configurations components configurations))))

;; ## Metadata

(defn vary-system-meta
  "Update the system metadata in the given component record."
  [system f & args]
  (vary-meta system update-in [::system] #(apply f % args)))

(defn set-system-meta
  "Attach system metadata to component record."
  [system data]
  (vary-meta system assoc ::system data))

(defn system-meta
  "Get system metadata from component record."
  [system]
  (-> system meta ::system))

(defn initialize-system-meta
  "Initialize the system metadata using the given system map creation function."
  [system f]
  (set-system-meta system (f system)))

