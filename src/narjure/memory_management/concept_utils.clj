(ns narjure.memory-management.concept-utils
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [narjure
     [global-atoms :refer :all]
     [bag :as b]
     [debug-util :refer :all]
     [control-utils :refer :all]
     [defaults :refer :all]
     [projection-utils :refer [max-statement-confidence-projected-to-now]]]
    [narjure.memory-management.local-inference
     [local-inference-utils :refer [get-task-id get-tasks]]
     [belief-processor :refer [process-belief]]
     [goal-processor :refer [process-goal]]
     [quest-processor :refer [process-quest]]
     [question-processor :refer [process-question]]]
    [nal.term_utils :refer [syntactic-complexity]]
    [nal.deriver
     [truth :refer [w2c t-or t-and confidence frequency expectation revision]]
     [projection-eternalization :refer [project-eternalize-to]]])
  (:refer-clojure :exclude [promise await]))

(defn concept-quality
  "Get the quality of a concept."
  []
  (let [value (:quality ((:elements-map @c-bag) (:id @state)))]
    (if value
      value
      0.0)))

(defn concept-priority
  "Get the priority of a concept."
  [term]
  (let [value (:priority ((:elements-map @c-bag) term))]
    (if value
      value
      0.0)))

(defn concept-observable
  "Check whether the concept is observable."
  [term]
  (:observable ((:elements-map @c-bag) term)))

(defn forget-task
  "Task forgetting applied in the task bags."
  [el last-forgotten n]
  (let [task (:task el)
        el-time (:occurrence task)
        budget (:budget task)
        lambda (/ (- 1.0 (second budget)) inverse-decay-rate)
        temporal-distance (if (= el-time :eternal) 0.0 (Math/abs (- el-time @nars-time)))
        occurrence-decay 1.0 #_(if (= el-time :eternal) 1.0 (/ 1.0 (+ 1.0 (* temporal-distance
                                                                       temporal-distance))))
        k-quality-occurrence-decay 10000.0
        distance-for-quality (/ temporal-distance k-quality-occurrence-decay)
        occurrence-decay-for-quality (if (= el-time :eternal) 0.9 (/ 1.0 (+ 1.0 (* distance-for-quality
                                                                                   distance-for-quality))))
        new-quality (* #_occurrence-decay-for-quality (nth budget 2))
        fr (Math/exp (* -1.0 (* lambda (- @nars-time last-forgotten))))
        new-priority (max (round2 4 (* (:priority el) fr occurrence-decay))
                          new-quality) ;quality of task
        new-budget [new-priority (second budget) new-quality]]
    (let [updated-task (assoc task :budget new-budget)]
      (assoc el :priority new-priority
                :task updated-task))))

(defn forget-tasks
  "Task forgetting applied in the task bags."
  []
  (let [tasks (:elements-map (:tasks @state))
        last-forgotten (:last-forgotten @state)
        n (b/count-elements (:tasks @state))]
    (set-state! (assoc @state :tasks (b/default-bag max-tasks)))
    (doseq [[id el] tasks]                                 ;{ id {:staement :type :occurrence}
      (let [el' (forget-task el last-forgotten n)]
        ;(println (str "forgetting: " (get-in el' [:task :statement])))
        (set-state! (assoc @state :tasks (b/add-element (:tasks @state) el')))))
    (set-state! (assoc @state :last-forgotten @nars-time))))

(defn concept-has-by-task-not-yet-recorded-link [state task]
  (let [recorded-terms (if (:record task) (map first (:record task)) [])
        links (:termlinks state)]
    (some (fn [tl-term] (not (some #{tl-term} recorded-terms)))
          (map first links))))

(defn update-concept-budget [state, self]
  "Update the concept budget"
  (let [els (:elements-map (:tasks state))      ; :priority-index ok here
        n (count els)
        p (/ (/ (round2 3 (reduce + 0 (for [[id {task :task}] els] ;rec
                                        (if (concept-has-by-task-not-yet-recorded-link state task)
                                          (first (:budget task))
                                          0.0 #_(nth (:budget task) 2))
                                        )))
                (syntactic-complexity (:id state)))
             max-tasks)
        q (round2 3 (reduce + 0 (for [[id {task :task}] els] (nth (:budget task) 2))))
        k  0.9999                                             ; long term quality forgetting
        new-q 0.0 #_(if (pos? n) (* k (/ q n)) 0.0) ;already managed by tasks
        el {:id       (:id state)
            :priority (max p new-q)
            :quality new-q
            :ref      self
            :observable (:observable state)
            :strongest-belief-about-now (max-statement-confidence-projected-to-now state :belief nil)
            :strongest-desire-about-now (max-statement-confidence-projected-to-now state :goal nil)
            :strongest-belief-event-about-now (max-statement-confidence-projected-to-now state :belief :event)
            }]
    (swap! c-bag b/add-element el)))
