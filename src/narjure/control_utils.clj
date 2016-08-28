(ns narjure.control-utils
  (:require
    [narjure.control.bag :as b]
    [narjure.defaults :refer :all]
    [narjure.global-atoms :refer :all]
    [clojure.math.numeric-tower :as math]))

(defn make-ev-helper
  "For merging evidence trails."
  [e2 e1 sofar]
  (let [r1 (first e1)
        r2 (first e2)]
    (case [(= nil r1) (= nil r2)]
      [true true] sofar
      [true false] (make-ev-helper [] (rest e2) (concat [r2] sofar))
      [false true] (make-ev-helper (rest e1) [] (concat [r1] sofar))
      [false false] (make-ev-helper (rest e1) (rest e2) (concat [r1] [r2] sofar)))))

(defn make-evidence
  "Merge evidence trails."
  [e1 e2]
  (take max-evidence (reverse (make-ev-helper e1 e2 []))))

(defn non-overlapping-evidence?
  "Check whether the evidental bases have an overlap."
  [e1 e2]
  (empty? (clojure.set/intersection (set e1) (set e2))))

(defn sufficient-priority?
  "Is the priority above min. threshold?"
  [selected]
  (> (:priority selected) priority-threshold))

(defn belief?
  "Is the task a belief?"
  [task]
  (= (:task-type task) :belief))

(defn goal?
  "Is the task a goal?"
  [task]
  (= (:task-type task)) :goal)