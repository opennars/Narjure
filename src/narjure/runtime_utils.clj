(ns narjure.runtime-utils
     (:require
       [co.paralleluniverse.pulsar
        [core :refer :all]
        [actors :refer :all]]
       [narjure.narsese :refer [parse2]]
       [nal.deriver.truth :refer [expectation]]
       [narjure.global-atoms :refer [lense-termlinks lense-taskbags nars-time]])
     (:refer-clojure :exclude [promise await]))

(defn get-lines
  "Get the lines of a text file."
  [file]
  (clojure.string/split-lines (slurp file)))

(defn load-NAL-file
  "Loading a NAL file into the system"
  [file]
  (println "loading file (with delay between inputs) ...")
  (doseq [narsese-str (get-lines file)]
    (println (str narsese-str))
    (cast! (whereis :sentence-parser) [:narsese-string-msg narsese-str])
    (Thread/sleep 1000))
  (println "file loaded.")
  )

(defn test-parser
  "For parser performance test."
  [n]
  (doseq [i (range n)]
    (parse2 "<(*,{SELF}) --> op_down>!")))

(defn test
  "Parser performance test."
  [n]
  (time
    (test-parser n)))

