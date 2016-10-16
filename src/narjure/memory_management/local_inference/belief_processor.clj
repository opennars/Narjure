(ns narjure.memory-management.local-inference.belief-processor
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [narjure.bag :as b]
    [narjure.debug-util :refer :all]
    [narjure.control-utils :refer :all]
    [narjure.global-atoms :refer :all]
    [narjure.defaults :refer :all]
    [nal.term_utils :refer [not-statement-and-conceptid-equal
                            statement-and-conceptid-equal]]
    [narjure.perception-action.task-creator :refer [event? get-id]]
    [narjure.memory-management.local-inference.local-inference-utils :refer :all]
    [nal.term_utils :refer :all]
    [nal.deriver.truth :refer [t-or confidence frequency w2c t2-evidence-weights]]
    [nal.deriver.projection-eternalization :refer [project-eternalize-to eternalize]])
  (:refer-clojure :exclude [promise await]))

(defn expired?
  "Is the anticipation expired so that a negative disappointment event needs to be generated?"
  [anticipation]
  (> @nars-time (:expiry anticipation)))

(defn create-negative-confirmation-task [anticipation]
  "collected input-evidence: [w+,w-]
   anticipated evidence: [wa+,wa-]
   positive evidence lack: lack=max(0,wa+ - w+)
   evidence that was not observed: [f,c]_result = [0,  w2c(positive-lack)]
   ''justified by the amount of positive evidence that was NOT observed as anticipated to be observed''"
  #_(println (:truth anticipation))
  (let [budget (:budget anticipation)
        anticipated-good-evidence (max 0 (- (:positive-evidence (t2-evidence-weights (:anticipated-truth anticipation)))
                                            (:negative-evidence (t2-evidence-weights (:anticipated-truth anticipation)))))
        observed-good-evidence (max 0 (- (:positive-evidence (t2-evidence-weights (:truth anticipation)))
                                         (:negative-evidence (t2-evidence-weights (:truth anticipation)))))
        good-evidence-lack (max 0 (- anticipated-good-evidence
                                     observed-good-evidence))
        confidence-of-lack (w2c good-evidence-lack)]
    (println (str "lack confidence: " confidence-of-lack))
    (dissoc (assoc anticipation :task-type :belief
                                :evidence (list (get-id))
                                :truth [0.0 confidence-of-lack]
                                :budget [(t-or 0.7 (nth budget 0)) (nth budget 1) (nth budget 2)]
                                :parent-statement nil
                                :occurrence (:expiry anticipation) ;could be minus/plus anticipation tolerance later
                                :terms (termlink-subterms (:statement anticipation))
                               #_:budget #_[(min 1.0
                                             (* (first (:budget anticipation))
                                                confidence-of-lack
                                                anticipation-disappointment-priority-gain))
                                        (second (:budget anticipation))
                                        (nth (:budget anticipation) 2)])
           :expiry
            :minconfirm)))

(defn create-negated-negative-confirmation-task
  "Creates the negated negative confirmation task, the negation of the anticipated event
  in Narsese-negation representation."
  [neg-confirmation]
  (assoc neg-confirmation :statement ['-- (:statement neg-confirmation)] :truth (nal.deriver.truth/negation (:truth neg-confirmation) [0.0 0.0])))

(defn confirmable-observable?
  "Is the t
  ask observable and derived so potentially predicted?"
  [task]
  (and #_(:observable @state) (not= (:occurrence task) :eternal)
       (= (:source task) :derived)))

(defn create-anticipation-task
  "The by anticipation disappointment created task."
  [task]
  (let [  ;example: i23 would still be i16 but i25 would be i32, border 24
          ;i13 would be i16, but i11 would be i8, border 12
        leftborder (/ (Math/abs (- (:occurrence task) @nars-time)) 4.0)
        rightborder (/ (Math/abs (- (:occurrence task) @nars-time)) 2.0)]
    (assoc task :task-type :anticipation
                :minconfirm (- (:occurrence task) leftborder) ;left side limit
               :expiry (+ (:occurrence task) rightborder))))      ;right side limit

(defn satisfaction-based-budget-change
  "Budget change based on the satisfaction of the goal by the belief"
  [state belief-task goals]
  ;filter goals matching concept content
  ;project-to task time
  ;select best ranked
  (let [projected-list
        (map (fn [a] [a (project-eternalize-to (:occurrence a) belief-task @nars-time)])
             (filter #(question-unifies (:statement %) (:statement belief-task)) goals))]
    (when (not-empty projected-list)
      (doseq [[goal belief-task-projected-to-goal] projected-list]
        (when (better-solution belief-task goal)
          ;update budget and solution
          ;(potential-output-answer state goal belief-task)
          (let [new-goal (reduced-goal-budget-by-belief goal belief-task-projected-to-goal)
                new-goal-with-solution (assoc new-goal :solution belief-task)]
            (update-task-in-tasks state new-goal-with-solution goal))
          (let [new-belief (increased-belief-budget-by-goal belief-task-projected-to-goal goal)]
            (update-task-in-tasks state (assoc belief-task :budget (:budget new-belief)) belief-task)))))))

(defn answer-based-budget-change
  "Budget change based on the quality of the answer."
  [state belief-task questions]
  ;filter goals matching concept content
  ;project-to task time
  ;select best ranked
  (let [projected-list
        (map (fn [a] [a (project-eternalize-to (:occurrence a) belief-task @nars-time)])
             (filter #(question-unifies (:statement %) (:statement belief-task))
                     questions))]
    (when (not-empty projected-list)
      (doseq [[question belief-task-projected-to-question] projected-list]
        (when (better-solution belief-task question)
          (potential-output-answer state (get-task-id question) question belief-task)
          ;update budget and solution
          (let [new-question (reduced-question-budget-by-belief question belief-task-projected-to-question)
                new-question-with-solution (assoc new-question :solution belief-task)]
            (update-task-in-tasks state new-question-with-solution question))
          (let [new-belief (increased-belief-budget-by-question belief-task-projected-to-question question)]
            (update-task-in-tasks state (assoc belief-task :budget (:budget new-belief)) belief-task)))))))

(defn revision-relevant-tasks
  "Whether the new task should do revision with the old one,
  see https://groups.google.com/forum/#!topic/open-nars/4uMf_kI3Etk"
  [task old-event]
  (or (= (:occurrence task) :eternal)
      (< (Math/abs (- (:occurrence task) (:occurrence old-event)))
      revision-relevant-event-distance)))

(defn process-belief
  "Process a belief: revise and put into the task bag, check whether it answers a question, and manage anticipations."
  [state task cnt]
    ;also allow revision in subterm concepts! this is why statement is compared to task statement, not to ID!!
  (when true #_(not (and (= (:statement task) (:id @state))
                  (:observable @state)
                  (= (:task-type task) :belief)
                  (= (:occurrence task) :eternal)))

      (let [[{existing-belief :task} _] (b/get-by-id (:tasks @state) (get-task-id task))]
        (let [processed-task (if (and existing-belief
                                    (revision-relevant-tasks task existing-belief)
                                    (non-overlapping-evidence? (:evidence task) (:evidence existing-belief)))
                             (revise task (project-eternalize-to (:occurrence task) existing-belief @nars-time))
                             task)]
           ;add revised task to bag:
           (add-to-tasks state processed-task)
           ;check if it satisfies a goal or question and change budget accordingly
           (satisfaction-based-budget-change state (:task (first (b/get-by-id (:tasks @state) (get-task-id processed-task)))) (filter #(= (:task-type %) :goal) (get-tasks state)))
           (answer-based-budget-change state (:task (first (b/get-by-id (:tasks @state) (get-task-id processed-task)))) (filter #(= (:task-type %) :question) (get-tasks state))))))

    ; processing revised anticipations

  (when (and (event? task) #_(= (:source task) :input) (belief? task))
    (when (pos? (count (:anticipations @state)))
      (doseq [[id anticipation] (:anticipations @state)]  ;{task-id task task-id2 task2}

        (when (and (= (:statement anticipation) (:statement task))
                   (> (:occurrence task) (:minconfirm anticipation)))
          (let [projected-task (project-eternalize-to (:occurrence anticipation) task @nars-time)]
             ;revise anticipation and add to tasks
             (when (non-overlapping-evidence? (:evidence projected-task) (:evidence anticipation))
               #_(println (str "anticipation: " anticipation "\nprojected task: " projected-task))
               (set-state! (assoc-in @state [:anticipations id]  (revise anticipation projected-task)))))))))

  (doseq [[id anticipation] (:anticipations @state)]                                                 ;be sure to use updated anticipation
      ;generate neg confirmation for expired anticipations
      ;and add to tasks
      (when (and anticipation (expired? anticipation))
       (let [neg-confirmation (create-negative-confirmation-task anticipation) ;      ;todo review budget in create-negative - currently priority of 1.0 with parents for d and q
             negated-neg-confirmation (create-negated-negative-confirmation-task neg-confirmation)]
         (set-state! (assoc @state :anticipations (dissoc (:anticipations @state) id)))
         ;(println (str "truth: " neg-confirmation))
         (when (not= (:truth neg-confirmation) [0.0 0.0])
           ;add neg-confirmation to tasks bag and remove anticiptaion
           (cast! (whereis :task-dispatcher) [:task-msg [nil nil neg-confirmation]])
           (cast! (whereis :task-dispatcher) [:task-msg [nil nil negated-neg-confirmation]])
           ;hypothesis correction:
           (when (:anticipation-precondition-task neg-confirmation)
             (cast! (whereis :inference-request-router) [:do-inference-msg [nil nil (:anticipation-precondition-task neg-confirmation) neg-confirmation :belief]])
             (println "hypothesis corrected!!! with task: " (:anticipation-precondition-task neg-confirmation) "\n"
                      "belief: " neg-confirmation))))))

    ;when task is confirmable and observabnle
    ;add an anticipation tasks to tasks
    (when (and (= (:task-type task) :belief)
            (statement-and-conceptid-equal (:statement task)                             ;only allow anticipation with concept content
              (:id @state)))
      (when (and (confirmable-observable? task)
                 (> (:occurrence task) @nars-time))
        (let [with-anticipated-truth (fn [t] (assoc t :source :derived :anticipated-truth (:truth t) :truth [0.5 0.0]))
              anticipated-task (with-anticipated-truth (create-anticipation-task task))
              anticipations (:anticipations @state)]

          (if (< (count anticipations) max-anticipations)
            (do (set-state! (assoc @state :anticipations (assoc anticipations (get-anticipation-id anticipated-task) anticipated-task)))
                (println (str "created anticipation: "  (:anticipations @state) " " anticipated-task)))
            (let [[max-future-id max-future-anticipation] (apply max-key (fn [[id anticipation]] (:occurrence anticipation)) anticipations)]
              (when (<= (:occurrence anticipated-task) (:occurrence max-future-anticipation))
                (set-state! (assoc @state :anticipations (assoc (dissoc anticipations max-future-id)
                                                (get-anticipation-id anticipated-task)
                                                anticipated-task)))
                (println (str "created anticipation(full): " anticipated-task)))))))))
