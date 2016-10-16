(ns narjure.control-utils
  (:require
    [narjure.bag :as b]
    [narjure.defaults :refer :all]
    [narjure.global-atoms :refer :all]
    [clojure.math.numeric-tower :as math]))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))

(def concept-selection-parameter 1.5)
(def task-selection-parameter 1)

(defn selection-fn-new
  ""
  [count]
  (Math/abs (- (* (+ (rand) (rand)) count) count)))

(defn selection-fn #_-old
  "An item selection bag curve."
  [count]
  (- (Math/ceil (* (math/expt (rand) concept-selection-parameter) count)) 1))

(defn selection-fn2
  "An item selection bag curve."
  [bag concept-priority]
  (let [count (b/count-elements bag)
        selection-parameter' (+ 1.0 (* concept-priority task-selection-parameter))
        i (- (Math/ceil (* (math/expt (rand) selection-parameter') count)) 1)]
    i))

(defn forget-element [el]
  "Forget an element by its durability."
  (let [budget (:budget (:task el))
        new-priority (* (:priority el) (second budget))
        new-budget  [new-priority (second budget)]]
    (assoc el :priority new-priority
              :task (assoc (:task el) :budget new-budget))))

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

(defn select-concepts-rec
  "select n concepts from c-bag ensuring the same concept is not selected twice"
  [count n bag selected]
  (if (and (pos? n) (pos? count))
    (let [[element bag'] (b/get-by-index bag (selection-fn-new count))
          selected' (conj selected element)]
      (select-concepts-rec (dec count) (dec n) bag' selected'))
    selected))

(defn select-concepts
  "wrapper for: select-concepts-rec
   select n concepts from c-bag ensuring the same concept is not selected twice"
  [n bag]
  (let [count (b/count-elements bag)]
    (select-concepts-rec count n bag [])))

(defn sufficient-priority?
  "Is the priority above min. threshold?"
  [selected]
  (> (:priority selected) priority-threshold))

(defn get-ref-from-term
  "Get the concept reference from a term."
  [term]
  (:ref ((:elements-map @c-bag) term)))

(defn belief?
  "Is the task a belief?"
  [task]
  (= (:task-type task) :belief))

(defn goal?
  "Is the task a goal?"
  [task]
  (= (:task-type task)) :goal)