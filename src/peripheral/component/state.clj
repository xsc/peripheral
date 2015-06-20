(ns peripheral.component.state)

;; ## Running?

(defn set-started
  "Set started flag in metadata."
  [component]
  (vary-meta component assoc ::running true))

(defn set-stopped
  "Set stopped flag in metadata."
 [component]
  (vary-meta component dissoc ::running))

(defn running?
  "Check whether the component is running (per its metadata)."
  [component]
  (boolean (-> component meta ::running)))
