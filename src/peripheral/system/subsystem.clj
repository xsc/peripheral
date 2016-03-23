(ns peripheral.system.subsystem)

(defn add-active-components
  "Add components to be activated to the system's metadata."
  [system components]
  (vary-meta system update-in [::active] (comp set concat) components))

(defn set-active-components
  "Add components to be activated to the system's metadata."
  [system components]
  (vary-meta system assoc ::active (set components)))

(defn active-components
  "Decide on what components are active/to-be-activated by examining the
   system's metadata."
  [system]
  (-> system meta ::active))

(defn active-components-from-deps
  [system dependencies]
  (or (active-components system)
      (-> dependencies meta :components)))

(defn subsystem
  "Create subsystem. The resulting system will only start the given
   components and their dependencies."
  [system components]
  (set-active-components system components))
