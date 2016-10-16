(ns narjure.memory-management.concept
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [clojure.core.unify :refer [unifier]]
    [nal.term_utils :refer [not-statement-and-conceptid-equal
                            statement-and-conceptid-equal]]
    [nal.deriver
     [truth :refer [w2c t-or t-and confidence frequency expectation revision]]
     [projection-eternalization :refer [project-eternalize-to]]]
    [narjure
     [global-atoms :refer :all]
     [bag :as b]
     [debug-util :refer :all]
     [control-utils :refer :all]
     [defaults :refer :all]
     [projection-utils :refer [max-statement-confidence-projected-to-now]]]
    [narjure.memory-management
     [concept-utils :refer :all]
     [termlink-utils :refer :all]]
    [nal.term_utils :refer [syntactic-complexity]]
    [narjure.memory-management.local-inference
     [local-inference-utils :refer [get-task-id get-tasks]]
     [belief-processor :refer [process-belief]]
     [goal-processor :refer [process-goal]]
     [quest-processor :refer [process-quest]]
     [question-processor :refer [process-question]]])
  (:refer-clojure :exclude [promise await]))

(def display (atom '()))
(def search (atom ""))

(defn task-handler
  [from [_ [task_]]]
  (when true
    (let [foreign-penalty (if (not-statement-and-conceptid-equal (:statement task_)
                            (:id @state))
                            1.0
                            1.0)
          eternal-penalty (if (= (:occurrence task_) :eternal)
                            1.0
                            1.0)
          task (assoc task_ :budget [(* foreign-penalty eternal-penalty (first (:budget task_)))
                                     (second (:budget task_))
                                     (nth (:budget task_) 2)])]
      (debuglogger search display ["task processed:" task])

      (refresh-termlinks task)

      ; check observable and set if necessary
      (when-not (:observable @state)
        ;(println "obs1")
        (let [{:keys [occurrence source]} task]
          (when (and (not= occurrence :eternal) (= source :input) (statement-and-conceptid-equal (:statement task) (:id @state)))
            (set-state! (assoc @state :observable true)))))

      #_(when (and (= (:task-type task) :goal)
                   (= (:statement task) '[--> ballpos [int-set equal]]))
          (println "concept ballpos equ exists"))

      (case (:task-type task)
        :belief (process-belief state task 0)
        :goal (process-goal state task 0)
        :question (process-question state task)
        :quest (process-quest state task)))))

(defn belief-request-handler
  ""
  [from [_ [task-concept-id task]]]

  (try                                                      ;update termlinks at first
    (update-termlink (:statement task))          ;task concept here
    (catch Exception e (debuglogger search display (str "belief side termlink strength error " (.toString e)))))

  ;todo get a belief which has highest confidence when projected to task time
  (try
    (let [tasks (get-tasks state)
          beliefs (filter #(and (statement-and-conceptid-equal (:statement %) (:id @state))
                                (= (:task-type %) :belief)) tasks)
          projected-belief-tuples (map (fn [z] [z (project-eternalize-to (:occurrence task) z @nars-time)]) beliefs)]

      (when (not-empty projected-belief-tuples)
        (doseq [belief beliefs]
          (debuglogger search display ["selected belief:" belief "§"])
          (cast! (:inference-request-router @state) [:do-inference-msg [task-concept-id (:id @state) task belief]])))

      ;dummy? belief as "empty" termlink belief selection for structural inference
      (let [belief {:statement (:id @state) :task-type :question :occurrence :eternal :evidence '()}]
        (debuglogger search display ["selected belief:" belief "§"])
        (cast! (:inference-request-router @state) [:do-inference-msg [task-concept-id (:id @state) task belief]])))
    (catch Exception e (debuglogger search display (str "belief request error " (.toString e))))))

(defn inference-request-handler
  ""
  [from message]
  (let [task-bag (:tasks @state)]
    (when true
      ;(println (syntactic-complexity (:id @state)))
      (when (pos? (b/count-elements task-bag))
        (let [[el] (b/lookup-by-index task-bag (selection-fn (b/count-elements task-bag)))]
          (debuglogger search display ["selected inference task:" el])
          (when-let [[c-id c-ref] (select-termlink-ref (:record (:task el)) (:lbudgets (:task el)))]
            (set-state!
              (assoc @state :tasks
                            (b/update-element task-bag
                                              (assoc-in el [:task :record]
                                                        (if (nil? (:record (:task el)))
                                                          [[c-id @nars-time]];;;
                                                          (if (some (fn [[id _]] (= id c-id)) (:record (:task el)))
                                                            (filter not-outdated-record-entry (:record (:task el)))
                                                            (take termlink-record-size (filter not-outdated-record-entry
                                                                                               (concat [[c-id @nars-time]]
                                                                                                       (:record (:task el)))))))))))
            (try                                                  ;update termlinks at first
              (update-termlink c-id)          ;task concept here
              (catch Exception e (debuglogger search display (str "task side termlink strength error " (.toString e)))))
            (cast! c-ref [:belief-request-msg [(:id @state) (:task el)]])))))))

(defn termlink-strengthen-handler
  "Strenghtens the termlink between two concepts or creates it if not existing.
   A link is {key value] where key is term and value is budget [priority durability]"
  [from [_ [term]]]
  (try (let
         [termlinks (:termlinks @state)
          old-link-strength (when termlinks (termlinks term))
          temporal-link-bonus [0.5 0.01]
          new-link-strength (calc-link-strength term (if old-link-strength old-link-strength temporal-link-bonus))]
         (set-state! (assoc-in @state [:termlinks term] new-link-strength)))
       (catch Exception e (println "termlink strenghten fatal error"))))

(defn concept-state-handler
  "Sends a copy of the actor state to requesting actor"
  [from _]
  (let [concept-state @state]
    (cast! from [:concept-state-msg concept-state])))

(defn set-concept-state-handler
  "set concept state to value passed in message"
  [from [_ new-state]]
  (set-state! (merge @state new-state))
  (let [elements (:elements-map (:tasks new-state))]
    (set-state! (assoc @state :tasks (b/default-bag max-tasks)))
    (doseq [[_ el] elements]
      (set-state! (assoc @state :tasks (b/add-element (:tasks @state) el))))))

(defn concept-forget-handler
  "update cocnept budget"
  [from [_ new-state]]
  (forget-tasks)
  (forget-termlinks-relative)
  (forget-termlinks-absolute)
  (update-concept-budget @state @self))

(defn shutdown-handler
  "Processes :shutdown-msg and shuts down actor"
  [from msg]
  (set-state! {})
  (unregister!)
  (shutdown!))

(defn initialise
  "Initialises actor: registers actor and sets actor state"
  [name]
  (set-state! {:id                       name
               :priority                 0.01
               :quality                  0.0
               :tasks                    (b/default-bag max-tasks)
               :termlinks                {}
               :anticipations            {}
               :concept-manager          (whereis :concept-manager)
               :inference-request-router (whereis :inference-request-router)
               :last-forgotten           @nars-time
               :observable               false}))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message"
  [from [type :as message]]
  (when-not (= type :concept-forget-msg) (debuglogger search display message))

  (when (b/exists? @c-bag (:id @state))                     ;check concept has not been removed first
    (case type
      :termlink-strengthen-msg (termlink-strengthen-handler from message)
      :task-msg (task-handler from message)
      :link-feedback-msg (link-feedback-handler from message)
      :belief-request-msg (belief-request-handler from message)
      :inference-request-msg (inference-request-handler from message)
      :concept-state-request-msg (concept-state-handler from message)
      :set-concept-state-msg (set-concept-state-handler from message)
      :concept-forget-msg (concept-forget-handler from message)
      :shutdown (shutdown-handler from message)
      (debug (str "unhandled msg: " type))))

  (when (pos? debug-messages)
    ;(reset! lense-anticipations (:anticipation @state))
    (swap! lense-taskbags
           (fn [dic]
             (assoc dic (:id @state) (:tasks @state))))
    (swap! lense-termlinks
           (fn [dic]
             (assoc dic (:id @state) (:termlinks @state))))))

(defn concept [name]
  (gen-server
    (reify Server
      (init [_] (initialise name))
      (terminate [_ cause] #_(info (str aname " terminated.")))
      (handle-cast [_ from id message] (msg-handler from message)))))
