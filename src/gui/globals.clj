(ns gui.globals)

(def init-size 800)
(def input-string (atom (atom "")))
(def invert-colors (atom true))
(def prio-threshold (atom 0.0))
(def link-labels (atom false))

(defn get-clipboard []
  (.getSystemClipboard (java.awt.Toolkit/getDefaultToolkit)))

(defn slurp-clipboard []
  (try
    (.getTransferData (.getContents (get-clipboard) nil) (java.awt.datatransfer.DataFlavor/stringFlavor))
    (catch java.lang.NullPointerException e nil)))

(defn spit-clipboard [text]
  (.setContents (get-clipboard) (java.awt.datatransfer.StringSelection. text) nil))