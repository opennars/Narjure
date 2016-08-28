(ns gui.gui-utils
  (:require [seesaw.core :refer :all]
            [gui.globals :refer :all]
            [co.paralleluniverse.pulsar.actors :refer [whereis cast!]]
            [clojure.string :as str]
            [narjure.core :refer [shutdown run]]))

(defn invert-color
  "Inverts the color in Lense"
  [[colr colg colb cola]]
  (if (= (deref invert-colors) true)
    [(- 255 colr) (- 255 colg) (- 255 colb) (if cola cola 255)]
    [colr colg colb (if cola cola 255)]))

(defn invert-comp
  "Invert a color component"
  [v]
  (if (= (deref invert-colors) true)
    (- 255 v)
    v))

(defn get-clipboard
  "Extract system clipboard"
  []
  (.getSystemClipboard (java.awt.Toolkit/getDefaultToolkit)))

(defn slurp-clipboard
  "Get the system clipboard content"
  []
  (try
    (.getTransferData (.getContents (get-clipboard) nil) (java.awt.datatransfer.DataFlavor/stringFlavor))
    (catch Exception e nil)))

(defn spit-clipboard
  "Write something into the system clipboard"
  [text]
  (try
    (.setContents (get-clipboard) (java.awt.datatransfer.StringSelection. text) nil)
    (catch Exception e nil)))

(defn input-str
  "Input Narsese by sending the narsese-string to sentence parser"
  [st]
  (println (str "input narsese " st))
  (doseq [x (str/split st #"\n")]
    (cast! (whereis :sentence-parser) [:narsese-string-msg x])))