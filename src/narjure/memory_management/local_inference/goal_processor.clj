(ns narjure.memory-management.local-inference.goal-processor
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [narjure.debug-util :refer [narsese-print]]
    [narjure.bag :as b]
    [narjure.defaults :refer [truth-value]]
    [nal.term_utils :refer [not-statement-and-conceptid-equal
                            statement-and-conceptid-equal]]
    [nal.term_utils :refer [syntactic-complexity termlink-subterms]]
    [nal.deriver.truth :refer [intersection deduction]]
    [narjure.debug-util :refer :all]
    [narjure.control-utils :refer :all]
    [narjure.global-atoms :refer :all]
    [narjure.perception-action.task-creator :refer [get-id]]
    [clojure.core.unify :refer [unify]]
    [nal.term_utils :refer [operation? negation-of-operation? syntactic-complexity]]
    [narjure.memory-management.local-inference.local-inference-utils :refer :all]
    [nal.deriver.truth :refer [t-or frequency confidence expectation desire-strong intersection]]
    [nal.deriver.projection-eternalization :refer [project-eternalize-to]])
  (:refer-clojure :exclude [promise await]))


(defn satisfaction-based-budget-change
  "Budget change based on the satisfaction of the goal by the belief"
  [state goal-task beliefs]
  ;filter goals matching concept content
  ;project-to task time
  ;select best ranked
  (let [projected-list
        (map (fn [a] [a (project-eternalize-to (:occurrence goal-task) a @nars-time)])
             (filter #(question-unifies (:statement goal-task) (:statement %)) beliefs))]
    (when (not-empty projected-list)
      (doseq [[belief belief-task-projected-to-goal] projected-list]
        (when (better-solution belief goal-task)
          ;update budget and solution
          ;(potential-output-answer state goal-task belief)
          (let [new-goal (reduced-goal-budget-by-belief goal-task belief-task-projected-to-goal)
                new-goal-with-solution (assoc new-goal :solution belief)]
            (update-task-in-tasks state new-goal-with-solution goal-task))
          (let [new-belief (increased-belief-budget-by-goal belief-task-projected-to-goal goal-task)]
            (update-task-in-tasks state (assoc belief :budget (:budget new-belief)) belief)))))))


;how big the truth expectation has to be in order to allow execution.
(def decision-threshold 0.5)                                ;0.6

(defn execute?
  "only execute if desire expectation is above decision threshold"
  [task]
  (> (expectation (:truth task)) decision-threshold))

(defn answer-based-budget-change
  "Budget change based on the answer quality (answering quests, which are questions on desire)."
  [state goal-task quests]
  ;filter goals matching concept content
  ;project-to task time
  ;select best ranked
  (let [projected-list
        (map (fn [a] [a (project-eternalize-to (:occurrence a) goal-task @nars-time)])
             (filter #(question-unifies (:statement %) (:statement goal-task)) quests))]
    (when (not-empty projected-list)
      (doseq [[quest goal-task-projected-to-quest] projected-list]
        (when (better-solution goal-task quest)
          (potential-output-answer state (get-task-id quest) quest goal-task)
          ;update budget and solution
          (let [new-quest (reduced-quest-budget-by-goal quest goal-task-projected-to-quest)
                new-quest-with-solution (assoc new-quest :solution goal-task)]
            (update-task-in-tasks state new-quest-with-solution quest))
          (let [new-goal (increased-goal-budget-by-quest goal-task-projected-to-quest quest)]
            (update-task-in-tasks state (assoc goal-task :budget (:budget new-goal)) goal-task)))))))

(defn interval?
  "Is the term an interval?"
  [content]
  (and (sequential? content) (= (first content) :interval)))

(def truth-tolerance 0.005)                                 ;TODO put to nal.deriver.truth?
;<(&&,<(*,{SELF}) --> op_down>,(&/,<ballpos --> [above]>,i2048)) =/> <ballpos --> [below]>>. :|12197|: %1.0;0.20954585215002955%" "
(defn best-operation-selection [beliefs goal]
  "1. find all beliefs that predict the goal ''=/> =|>  <|> </>''
   2. filter those with (precondition,op) => goal
   3. use the one whose precondition is highest fullfilled and statement truth is highest
     3.1 truth_A = getting the strongest truth value of the precondition of the precondition concept projected to current moment
     3.2 truth_B = get the truth value of the implication statement
     3.3 desire = desire value of goal
     3.4 execution desire expectation is: D=desire_strong(desire_strong(desire,truth_B),truth_A)
   by using the one whose expectation(D) is highest
   4. create a result operation goal task with the from the predictive statement first operation
      and evidence trail being the summary of all evidence trails

   <(&/,<{ball} --> [down]>,move_up({SELF})) =/> <{ball} --> [equal]>>. %0.4;09% Premise   <- truth_B
    <{ball} --> [equal]>!  Premise                                                        <- desire
    |-
    (&/,<{ball} --> [down]>,move_up({SELF}))!  Desire:Deduction
    <{ball} --> [down]>.         Premise                                        <- truth_A
    |-
    move_up({SELF})!  Desire:Deduction

  for goal <{ball} --> [equal]>!
  <(&/,<{ball} --> [up]>,move_up({SELF})) =/>
  <{ball} --> [equal]>>
  <(&/,<{ball} --> [up]>,move_down({SELF})) =/>
  <{ball} --> [equal]>>  %0.3;0.9%
  <(&/,<{ball} --> [down]>,move_up({SELF})) =/>
  <{ball} --> [equal]>>. %0.4;09%"

  #_(when (and (= (:id @state) (:statement goal))
               (= (:occurrence goal) :eternal)
               (= (:task-type goal) :goal)
               (= (:statement goal) '[--> ballpos [int-set equal]])
               (println "concept ballpos equ goal processed")))

  ;1.: find all beliefs that predict the goal ''=/> =|>  <|> </>''
  (when (statement-and-conceptid-equal (:statement goal)
                                       (:id @state)) #_(and (= (:occurrence goal) :eternal)
                    (= (:id @state) (:statement goal)))

    #_(when (and (= (:task-type goal) :goal)
                 (= (:statement goal) '[--> ballpos [int-set equal]]))
        (println "concept ballpos equ goal processed"))

    (let [debugme (= (:statement goal) '[--> ballpos [int-set equal]])
          println2 (fn [a] (when debugme (println a)))
          ;2. filter those with (precondition,op) => goal   ;so this one is for future improvement
          #_print1 #_(println "step 1,2")
          #_blub1 #_(println2 "process goal debug marker")
          precondition-op-forms ['[pred-impl [seq-conj [seq-conj ?precondition ?interval1 ?operation] ?interval2] ?goal]] ;TODO add others
          precondition-op-beliefs-and-assigment-tuple (filter
                                                        (fn [z] (and (not= (second z) nil)
                                                                     (= ((second z) '?goal) (:statement goal))
                                                                     (operation? ((second z) '?operation))
                                                                     (not (operation? ((second z) '?precondition)))
                                                                     (interval? ((second z) '?interval1))
                                                                     (not (negation-of-operation? ((second z) '?precondition)))
                                                                     (not (operation? ((second z) '?goal)))
                                                                     (not (negation-of-operation? ((second z) '?goal)))))
                                                        (for [form precondition-op-forms
                                                              belief (for [b (filter (fn [h] (= (:occurrence h) :eternal)) beliefs)] b)]
                                                          [belief (unify form (:statement belief))]))
          #_print2 #_(println (str "step 3.1, 3.2\n" (vec precondition-op-beliefs-and-assigment-tuple)))
          ;3. use the one whose precondition is highest fullfilled and statement truth is highest:
          ;3.1 truth_A = getting the strongest truth value of the precondition concept projected to current moment
          ;3.2 truth_B = get the truth value of the implication statement
          truth-A-B-unification-maps (for [[belief unificaton-map] precondition-op-beliefs-and-assigment-tuple]
                                       (do
                                         ;reward belief uality also for having this for control useful structure
                                         #_(println (str "AND REWARDED " belief))
                                         #_(println (str "rewarded belief" (narsese-print (:statement belief)) " " (:truth belief) " budg: " (:budget belief)))
                                         (let [budget (:budget belief)
                                               new-quality (t-or (expectation (:truth belief)) (t-or (second (:truth goal)) 0.8))] ;TODO see budget-functions (unify)
                                           #_(println (str "rewarding " (narsese-print (:statement belief)) " " (:truth belief)))
                                           #_(update-task-in-tasks state (assoc belief :budget [(max new-quality
                                                                                                   (first (:budget belief)))
                                                                                              (second (:budget belief))
                                                                                              new-quality]) ;new quality
                                                                 belief))
                                         (let [precondition (unificaton-map '?precondition)
                                               [precondition-concept bag] (b/get-by-id @c-bag precondition)
                                               strongest-belief-about-now (project-eternalize-to @nars-time (:strongest-belief-event-about-now precondition-concept) @nars-time)]
                                           (when (and precondition-concept
                                                      (:truth strongest-belief-about-now)
                                                      (:truth belief)
                                                      (> (second (:truth strongest-belief-about-now)) 0.3))
                                             [(:truth strongest-belief-about-now) (:truth belief) (:evidence strongest-belief-about-now) (:evidence belief) unificaton-map belief]))))
          #_print3 #_(println (str "step 3.3\n" (vec truth-A-B-unification-maps)))
          ;3.3 desire = desire value of goal
          desire (:truth (project-eternalize-to @nars-time goal @nars-time))
          #_print4 #_(println (str "step 3.4\n" desire))
          ;3.4 execution desire expectation is: D=desire_strong(intersection(desire,truth_B),truth_A)          ;[truth-A truth-B evidence-A evidence-B unification-map debug-belief]
          D-unification-maps (for [[truth-A truth-B evidence-A evidence-B unification-map debug-belief] (filter (fn [a] a) truth-A-B-unification-maps)]
                               (do
                                 #_(println (str desire truth-B truth-A))
                                 {:D               (intersection (intersection desire truth-B) truth-A)
                                  :evidence-A      evidence-A
                                  :evidence-B      evidence-B
                                  :unification-map unification-map
                                  :debug-belief    debug-belief
                                  :truth-A         truth-A
                                  :truth-B         truth-B}))

          #_print5 #_(println (str "3 get best\n" (vec D-unification-maps)))
          ;by using the one whose expectation(D) is highest
          k-expectation-randomize 50.0
          best-option (apply max-key (comp (fn [a] (+ a (/ (rand) k-expectation-randomize))) expectation :D) ;not always the best one but tend to.
                             (filter (fn [z] true #_(and (non-overlapping-evidence? (:evidence goal) (:evidence-A z))
                                                         (non-overlapping-evidence? (:evidence-A z) (:evidence-B z)) ;overlap check is not transitive: A {1 2 3} B {5} C {1 2 3}
                                                         (non-overlapping-evidence? (:evidence goal) (:evidence-B z)))) D-unification-maps))
          #_print6 #_(println (str "finished 3 \n"best-option))
          ]

      ;4. create a result operation goal task with the from the predictive statement first operation and evidence trail being the summary of all evidence trails
      (when (and best-option
                 (best-option :D)
                 (> (second (best-option :D)) truth-tolerance))
        #_(println (str "had best operator option " (best-option :D)))
        (let [unimap (:unification-map best-option)
              statement (unimap '?operation)
              new-task {:statement  statement
                        :truth      (best-option :D)
                        :evidence   (make-evidence (make-evidence (best-option :evidence-A) (best-option :evidence-B)) (:evidence goal))
                        :occurrence @nars-time               ;needs to be occurrence time of now since task creator leaves derived task occurrences un-touched
                        :budget     (:budget goal)
                        :task-type  :goal
                        :sc (syntactic-complexity statement)}]

          (when (> (expectation (:truth new-task)) decision-threshold)
            (println (str "based on " (best-option :debug-belief)))
            #_(println (str "operator selector sending to task-creator " (:statement new-task) (:truth new-task) (expectation (:truth new-task))))
            ;create prediction for consequence:
            #_(let [
                  cont (unimap '?goal)
                  subj ['seq-conj (unimap '?precondition) (unimap '?interval1) (unimap '?operation)]]
              #_(println "test1")
              (cast! (whereis :task-dispatcher)
                     [:task-msg
                      [nil ;TODO could provide link feedback
                       nil
                       {:truth                          (intersection (intersection truth-value (:truth-A best-option))
                                                                   (:truth-B best-option))
                        :budget                         (:budget goal)
                        :occurrence                     (+ @nars-time (second ((:unification-map best-option) '?interval2)))
                        :source                         :derived
                        :evidence                       (:evidence best-option)
                        :sc                             (syntactic-complexity cont)
                        :terms                          (termlink-subterms cont)
                        :solution                       nil
                        :task-type                      :belief
                        :statement                      cont
                        ;for hypothesis correction:
                        :anticipation-precondition-task {:truth      (intersection truth-value (:truth-A best-option))
                                                         :budget     (:budget goal)
                                                         :occurrence @nars-time
                                                         :source     :derived
                                                         :evidence   (list (get-id))
                                                         :task-type :belief
                                                         :statement subj
                                                         }}]])
              #_(println "sent"))
            )
          (cast! (whereis :task-creator) [:derived-sentence-msg [nil nil new-task]])
          )))))

(defn process-goal
  "Process a goal task: revise, put into the task bag, check for satisfaction and whether it
   is answered or answers a quest, and see whether it needs to execute."
  [state task cnt]
  ;group-by :task-type tasks

  (let [tasks (get-tasks state)
        groups (group-by :task-type tasks)
        goals (:goal groups)
        beliefs (:belief groups)
        quests (:quest groups)]

    ;also allow revision in subterm concepts! this is why statement is compared to task statement, not to ID!!
    (when-not (and (= (:occurrence task) :eternal)
                   (operation? (:statement task)))
      (let [related-goals (filter (fn [z] (and (same-occurrence-type z task)
                                               (= (:statement z) (:statement task)))) goals)]

        (let [total-revision (reduce (fn [a b] (if (non-overlapping-evidence? (:evidence a) (:evidence b))
                                                 (revise a (project-eternalize-to (:occurrence a) b @nars-time))
                                                 a))
                                     task (shuffle related-goals))]

          ;add revised task to bag
          (add-to-tasks state total-revision)
          ; check to see if revised or task is answer to quest and increase budget accordingly
          ;check whether it is fullfilled by belief and decrease budget accordingly
          (satisfaction-based-budget-change state (:task (first (b/get-by-id (:tasks @state) (get-task-id total-revision)))) (filter #(= (:task-type %) :belief) (get-tasks state)))
          (answer-based-budget-change state (:task (first (b/get-by-id (:tasks @state) (get-task-id total-revision)))) (filter #(= (:task-type %) :quest) (get-tasks state)))

          #_(when (and (= (:id @state) (:statement total-revision))
                       (= (:occurrence total-revision) :eternal)
                       (= (:task-type total-revision) :goal)
                       (= (:statement total-revision) '[--> ballpos [int-set equal]]))
              (println "concept ballpos equ goal processed"))

          (try
            (best-operation-selection (filter #(= (:task-type %) :belief) (get-tasks state))
                                      (:task (first (b/get-by-id (:tasks @state) (get-task-id total-revision)))))
            (catch Exception e () #_(println (str "operator selector error" e))))
          )))

    ;best operation project goal to current time
    ; if above decision threshold then execute#
    (let [projected-goals (map #(project-eternalize-to @nars-time % @nars-time)
                               (filter #(= (:statement %) (:statement task))
                                       (filter #(= (:task-type %) :goal) ;re-getting the goals because we also want our just added goal
                                               (conj tasks task))))] ;needs new task as well
      (if (not-empty projected-goals)
        (let [possible-operations (filter #(and (operation? (:statement %)) (execute? %) (statement-and-conceptid-equal (:statement %) (:id @state))) projected-goals)
              operation (if (not-empty possible-operations)
                          (apply max-key confidence possible-operations)
                          nil)]
          (when-not (= nil operation)                   ;todo change to - when operation
            #_(println (str (:truth operation) (expectation (:truth operation))))
            ;(println (str  "goal: " operation))
            (when-not (:execution-evidence @state)
              (set-state! (assoc @state :execution-evidence '())))
            (when true #_(some (fn [z] (not (some #{z} (:execution-evidence @state)))) (:evidence operation))
              (cast! (whereis :operator-executor) [:operator-execution-msg operation])
              (set-state! (assoc @state :execution-evidence (take 50 (concat (:evidence operation) (:execution-evidence @state)))))
              )))))))
