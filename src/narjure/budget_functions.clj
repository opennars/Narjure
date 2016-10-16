(ns narjure.budget-functions
  (:require
    [nal
     [term_utils :refer [precondition-operation-consequent-statement]]]
    [nal.deriver
     [truth :refer [expectation t-or t-and w2c]]]
    [narjure
     [defaults :refer :all]
     [global-atoms :refer :all]
     [control-utils :refer [round2]]
     [debug-util :refer :all]]))

(defn truth-to-quality
  "The task quality judged by its truth."
  [t]
  (let [exp (expectation t)
        positive-truth-bias 0.75]
    exp #_(max exp (* (- 1.0 exp) positive-truth-bias))))

(defn occurrence-penalty-tr
  "Occurrence budget penalty. Currently not used as forgetting seems to suffice."
  [occ]
  (let [k 0.0001]
    (if (= occ :eternal)
      1.0
      (/ 1.0 (+ 1.0 (* k (Math/abs (- @nars-time occ))))))))

(defn highest-desire-in-respect-to-now
  "The highest desire value in respect to current nars-time."
  [concept-term]
  (:truth (:strongest-desire-about-now ((:elements-map @c-bag) concept-term)))) ;also projected to now!!

(defn structural-reward-budget
  "returns a increased budget for statements of structural interest, mainly for experiments."
  [budget derived-task]
  (let [not-matched-or-not-desired-budget [(* (first budget) 0.8) (second budget) (nth budget 2)]
        match (second (precondition-operation-consequent-statement derived-task))]
    (if (and (:truth derived-task)
             match)
      (do
        (let [goal (match '?goal)
              precondition (match '?precondition)
              goal-desire (highest-desire-in-respect-to-now goal)]
          ;(println (str "2: " goal))
          (if (= precondition goal)
            nil
            (if goal-desire
              (let [quality (t-or (t-or (expectation (:truth derived-task))
                                        (nth budget 2))
                                       (t-or (second goal-desire)
                                             (t-or (/ 1.0 (:sc derived-task))
                                                   0.3)))] ;TODO see goal-processor (unify)
                (do
                  (println "INCREASED DERIVED BUDGET")
                  (println (narsese-print (:statement derived-task)) " " (:truth derived-task) " " (:occurrence derived-task))
                  [(max (first budget) quality) #_(max (first budget) quality)
                   (second budget) #_(max (second budget) 0.9)
                   (t-or 0.6 (t-or (nth budget 2) (/ 1.0 (:sc derived-task))))]))
              not-matched-or-not-desired-budget))))
      not-matched-or-not-desired-budget))                   ;tODO too radical
  )


(defn derived-budget
  "The budget of a by general inference derived task."
  [task derived-task]
  (when (< (:sc derived-task) @max-term-complexity)
    (let [durability (/ (second (:budget task)) (:sc derived-task))
          truth-quality (if (:truth derived-task) (truth-to-quality (:truth derived-task))
                                                  0.0)
          priority (/ (* (t-or truth-quality (first (:budget task)))
                         (occurrence-penalty-tr (:occurrence derived-task)))
                      (/ (:sc derived-task) 1.0))
          rescale-factor 0.1 ;should probably not above input belief quality!
          quality (* priority
                     rescale-factor)]
      (structural-reward-budget [priority durability quality] derived-task))))