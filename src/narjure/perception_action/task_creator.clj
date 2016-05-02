(ns narjure.perception-action.task-creator
  (:use [co.paralleluniverse.pulsar.core]
        [co.paralleluniverse.pulsar.actors :exclude [defactor]])
  (:refer-clojure :exclude [await promise])
  (:require
    [narjure.actor.utils :refer [defactor]]
    [taoensso.timbre :refer [debug info]])
  (:refer-clojure :exclude [promise await]))

(def aname :task-creator)

(defn sentence [_ _]
  (debug aname "process-sentence"))

(defn system-time-tick-handler
  "inc :time value in actor state for each system-time-tick-msg"
  []
  #_(debug aname "process-system-time-tick")
  (set-state! (update @state :time inc)))

(defn get-id
  "inc the task :id in actor state and returns the value"
  []
  (set-state! (update @state :id inc))
  (@state :id))

(defn get-time
  "return the current time from actor state"
  []
  (@state :time))

(defn initialise
  "Initialises actor:
      registers actor and sets actor state"
  [aname actor-ref task-dispatcher-name]
  (set-state! {:time 0 :id -1 :task-dispatcher task-dispatcher-name}))

(defn create-new-task
  "create a new task with the provided sentence and default values
   convert tense to occurrence time if applicable"
  [sentence time id]
  {:creation   time
   :occurrence 0
   :source     :input
   :id         id
   :evidence   '(id)
   :solution   nil
   :statement  sentence})

(defn create-derived-task
  "Create a derived task with the provided sentence, budget and occurence time
   and default values for the remaining parameters"
  [sentence budget occurrence time id]
  {:creation   time
   :occurrence occurrence
   :source     :derived
   :id         id
   :evidence   '(id)
   :solution   nil
   :statement  sentence})

(defn sentence-handler
  "Processes a :sentence-msg"
  [task-dispatcher time id from [_ sentence]]
  (cast! task-dispatcher [:task-msg (create-new-task sentence time id)]))

(defn derived-sentence-handler
  "processes a :derived-sentence-msg"
  [task-dispatcher time id from [msg sentence budget occurrence]]
  (cast! task-dispatcher [:task-msg (create-derived-task sentence budget occurrence time id)]))

(defn shutdown-handler
  "Processes :shutdown-msg and shuts down actor"
  [from msg]
  (shutdown!))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message "
  [from [type :as message]]
  (let [task-dispatcher (:task-dispatcher @state)
        time (get-time)
        id (get-id)]
    (when (done? task-dispatcher) (throw (Exception. "Task dispatcher exited")))
    (case type
      :sentence-msg (sentence-handler task-dispatcher time id from message)
      :derived-sentence-msg (derived-sentence-handler task-dispatcher time id from message)
      :system-time-tick-msg (system-time-tick-handler)
      :shutdown (shutdown-handler from message)
      (debug aname (str "unhandled msg: " type))
      )))

(defmacro task-creator-gen [name task-dispatcher-name]
  `(gen-server
     (reify Server
       (init [_] (initialise ~name @self ~task-dispatcher-name))
       (terminate [_ cause] #_(info (str name " terminated.")))
       (handle-cast [_ from# id message#] (msg-handler from# message#)))))

(def task-creator (task-creator-gen aname (whereis :task-dispatcher)))
