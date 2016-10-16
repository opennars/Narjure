(ns narjure.memory-management.local-inference.local-inference-utils
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [narjure.bag :as b]
    [narjure.global-atoms :refer :all]
    [clojure.core.unify :refer [unify]]
    [narjure.control-utils :refer [make-evidence round2]]
    [narjure.perception-action.task-creator :refer :all]
    [nal.term_utils :refer :all]
    [nal.deriver :refer [occurrence-type]]
    [nal.deriver.truth :refer [t-and t-or frequency confidence expectation]]
    [nal.deriver.projection-eternalization :refer [project-eternalize-to]]
    [narjure.debug-util :refer :all]
    [narjure.budget-functions :refer [derived-budget]])
  (:refer-clojure :exclude [promise await]))

(defn truth-round
  "Truth rounding to 2 commas"
  [[f c :as truth]]
  (if (= nil truth)
    nil
    [(round2 2 f)
    (round2 2 c)]))

(defn get-task-id
  "The ID defining when elements in the task bag need to be merged."
  [task] ;not using concept-term-transform would demand huge task bag size or less bias in complexity
  [(concept-term-transform (:statement task)) (:task-type task) (occurrence-type (:occurrence task))])

(defn get-anticipation-id
  "The ID defining when elements in the anticipations bag need to be merged."
  [task]
  [(:statement task) (:task-type task) (:occurrence task)])

(defn measure-budget
  "The budget evaluation for task bag element quality." ;TODO revise
  [budg]
  (first budg))                                             ;higher priority means higher evaluation similar as for confidence in truth consideration

(defn max-budget
  "Returns the bigger budget"
  [budg1 budg2]                            ;the one with higher priority determines the budget
  (apply max-key measure-budget [budg1 budg2]))

(defn measure-truth
  "The truth evaluation for task bag belief quality."
  [task]                                    ; higher confidence means higher evaluation
  (second (:truth (project-eternalize-to @nars-time task @nars-time)))) ;prefer current events

(defn make-element
  "Create an element from the "
  [task]
  {:id (get-task-id task) :priority (first (:budget task)) :task task})

(defn take-better-solution
  "Take the better solution of both when merging bag elements"
  [result t1 t2]
  (let [sol1 (:solution t1)
        sol2  (:solution t2)]
    (if (or (not= nil sol1)
            (not= nil sol2)) ;project both to result time and then use the better solution for our result:
      (let [sol1-proj (project-eternalize-to (:occurrence result) sol1 @nars-time)
            sol2-proj (project-eternalize-to (:occurrence result) sol2 @nars-time)
            best-solution (apply max-key
                                 (fn [a] (second (:truth a)))
                                 (filter #(not= nil %) [sol1-proj sol2-proj]))]
        (assoc result :solution best-solution))
      result)))

(defn better-task
  "Defines which task is better for deciding which one to keep."
  [t1 t2]                                   ;take care here with t1 t2!! ^^
  (if (= t1 nil)                                            ;no previous bag entry exists
    t2                                                      ;so take the new one
    (if (not= nil (:truth t1))
      (take-better-solution (max-key (comp measure-truth) t1 t2) t1 t2)
      (take-better-solution (max-key (comp measure-budget :budget) t1 t2) t1 t2))))

(defn add-to-tasks
  "Add the task to the tasks bag"
  [state task]
  (let [bag (:tasks @state)
        el (make-element task)
        [{t2 :task :as existing-el} _] (b/get-by-id bag (:id el))
        chosen-task (better-task t2 task)                   ;the second one is kept if equal so the order is important here!!
        new-budget (if existing-el
                     (max-budget (:budget task)
                                 (:budget t2))
                     (:budget task))
        new-el (make-element (assoc chosen-task :budget new-budget))
        bag' (b/add-element bag new-el)]
    (set-state! (assoc @state :tasks bag'))))

(defn update-task-in-tasks
  "Update an existing task bag element"
  [state task old-task]
  (let [[element bag] (b/get-by-id (:tasks @state) (get-task-id old-task))]
    (when element
      (set-state! (assoc @state :tasks bag))))
  (set-state! (assoc @state :tasks (b/add-element (:tasks @state) (make-element task)))))

(defn inc-budget ;task was revised, this is further evidence for the quality of the task
  [[p d q]] ;as it seems to take part in summarizing experience instead of describing one-time phenomena
  [(t-or p 0.3) (t-or d 0.1) (t-or q 0.3)])

(defn revise
  "Revision of two tasks."
  [t1 t2]
  (let [revised-truth (nal.deriver.truth/revision (:truth t1) (:truth t2))
        evidence (make-evidence (:evidence t1) (:evidence t2))
        lbudget-left (if-let [budg (:lbudgets t1)] budg {})
        lbudget-right (if-let [budg (:lbudgets t2)] budg {})]
    (let [revised-task (dissoc (assoc t1 :truth revised-truth :source :derived :evidence evidence
                                 :budget (max-budget (:budget t1) (:budget t2))
                                         :lbudgets (merge lbudget-left lbudget-right))
                       :record)]
      (assoc revised-task :budget (inc-budget (:budget revised-task)) #_(derived-budget t1 revised-task)))))

(defn answer-quality
  "The quality of an answer, which is the confidence for y/n questions,
  and the truth expectation, also taking complexity into account for what-questions"
  [question solution]
  (if solution
    (if (some #{'qu-var} (flatten (:statement question)))
      (/ (expectation (:truth solution)) (Math/sqrt (:sc solution)))
      (confidence solution))
    0))

(defn better-solution
  "whether the solution is better than the solution the task holds"
  [solution task]
  (let [projected-solution (project-eternalize-to (:occurrence task) solution @nars-time)
        cur-solution (project-eternalize-to (:occurrence task) (:solution task) @nars-time)]
    (or (= nil cur-solution)
        (>= (answer-quality task projected-solution)
            (answer-quality task cur-solution)))))

(defn reduced-goal-budget-by-belief
  "Reduction of budget due to satisfaction of goal by belief"
  [goal belief]                     ;by belief satisfied goal
  (let [satisfaction (- 1.0 (Math/abs (- (expectation (:truth goal))
                                         (expectation (:truth belief)))))
        budget (:budget goal)
        p (first budget)
        p-new (t-and p (- 1.0 satisfaction))]
    (assoc goal :budget [p-new (second budget) (nth budget 2)])))

(defn reduced-question-budget-by-belief
  "Reduced budget due to answer quality of a belief that answers a question."
  [question belief]
  (let [budget (:budget question)
        p (first budget)
        p-new (t-and p (- 1.0 (answer-quality question belief)))]
    (assoc question :budget [p-new (second budget) (nth budget 2)])))

(defn increased-belief-budget-by-question
  "Increasing the belief budget due to its usefulness of answering the question."
  [belief question]  ;useful belief, answered a question
  (let [budget (:budget belief)
        q (nth budget 2)
        k 0.001
        ;d-new (t-or d (* k (confidence belief)))
        q-new (t-or q (* k (nth (:budget belief) 2)))]
    (assoc belief :budget [(first budget) (second budget) q-new])))                                                 ;1-confidence(solution)

(defn reduced-quest-budget-by-goal
  "Reducing the quest budget because of the answer quality of the goal that answers it."
  [quest goal]                     ;by goal satisfied quest
  (reduced-question-budget-by-belief quest goal))

(defn increased-goal-budget-by-quest
  "Increasing the goal budget because it was useful in answering the quest."
  [goal quest]                     ;useful goal, answered a quest
  (increased-belief-budget-by-question goal quest))

(defn increased-belief-budget-by-goal
  "Increasing the belief budget because it was useful for answering a goal."
  [belief goal]                     ;by belief satisfied goal
  (increased-belief-budget-by-question belief goal))

;increased-belief-budget-by-goal

;TODO handle in question/quest handling:
;increased-goal-budget-by-quest
;increased-belief-budget-by-question

;decrease-quest-question-budget-by-solution:
(defn decrease-question-budget-by-solution
  "Decrease question budget because of the answer quality."
  [question]
  (let [budget (:budget question)
        solution (:solution question)]
    ;todo improve budget function here
    (let [new-budget [(* (- 1.0 (answer-quality question solution)) (first budget))
                      (second budget)
                      (nth budget 2)]] ;TODO dependent on solution confidence
      (assoc question :budget new-budget))))

(defn decrease-quest-budget-by-solution
  "Decrease quest budget because of the answer quality."
  [quest]
  (decrease-question-budget-by-solution quest))

(defn get-tasks
  "Get the tasks in the concept."
  [state]
  (let [tasks (vec (for [x (:elements-map (:tasks @state))] (:task (val x))))]
    ;(println (str "count: "  (count (:elements-map (:tasks @state))) " gt tasks: " tasks))
    tasks))

(defn same-occurrence-type
  "Check whether the occurrence types are equal."
  [t1 t2]
  (or (and (= (:occurrence t1) :eternal) (= (:occurrence t2) :eternal))
      (and (not= (:occurrence t1) :eternal) (not= (:occurrence t2) :eternal))))

(defn qu-var-transform
  "Question variables transformed to a representation core.unify can work with."
  [term]
  (if (coll? term)
    (if (= (first term) 'qu-var)
      (symbol (str "?" (second term)))
      (apply vector (for [x term]
                      (qu-var-transform x))))
    term))

(defn placeholder-transform
  "Placeholders replaced because core.unify would treat them as do not cares."
  [term]
  (if (coll? term)
    (apply vector (for [x term]
                    (placeholder-transform x))))
  (if (= term '_)
    '_IMAGE_PLACEHOLDER_
    term))                                  ;TODO

(defn valid-answer-unifier
  "Check that it did not unify incorrectly by assigning a dependent var to a question var."
  [unifier]
  (not (some #{'dep-var} (flatten (vals unifier)))))

(defn question-unifies
  "Does the solution unify with the question?"
  [question solution]
  (try (let [unifier (unify (placeholder-transform (qu-var-transform question)) (placeholder-transform solution))]
     (when (and unifier
                (valid-answer-unifier unifier))
       true))
       (catch Exception ex (println (str "question unification issue with question "
                                         question
                                         "\nanswer:"
                                         solution
                                         "\n" ex) ))))