(ns narjure.perception-action.operator-executor
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [narjure.debug-util :refer :all]
    [narjure.defaults :refer [budgets]]
    [narjure.global-atoms :refer :all])
  (:refer-clojure :exclude [promise await]))

(def aname :operator-executor)                              ; actor name
(def display (atom '()))                                    ; for lense output
(def search (atom ""))                                      ; for lense output filtering
(def registered-operator-functions (atom {}))

(defn operator-execution-handler
  "Processes an :operator-execution-msg:
    executes operation with optionally supplied parameters
    if feedback msg required posts :sentence-msg to task creator"
  [from [msg operationgoal]]
  (let [feedback (assoc operationgoal :task-type :belief
                                      :occurrence 0
                                      :source :input
                                      :budget (budgets :belief)
                                      )
        operation (:statement operationgoal)
        arguments (if (coll? operation) (rest (second operation)) [])
        operator (if (coll? operation) (nth operation 2) operation)]
    (try (let [func (@registered-operator-functions operator)]
           (if (not= nil func)
             (let [success (func arguments operationgoal)]
               (output-task :execution operationgoal)
               (if success          ;when there is an operator function, give positive/negative feedback based on success
                 (do
                   (cast! (whereis :task-creator) [:sentence-msg feedback])
                   #_(when (coll? success)                  ;TODO collides with operation task idea
                       (doseq [custom-feedback success] ;also allowing custom feedback tasks
                         (cast! (whereis :task-creator) [:sentence-msg custom-feedback]))))
                 #_(cast! (whereis :task-creator) [:sentence-msg (assoc feedback :truth
                                                                               [(- 1.0 (first (:truth feedback)))
                                                                                (second (:truth feedback))])])))
             (do ;if no function is registered, just enter the feedback, eliminates the issue that we need to register ops just to use them in examples
               (output-task :execution operationgoal)
               (cast! (whereis :task-creator) [:sentence-msg feedback]))))
         (catch Exception e (debuglogger search display (str "operator execution error " (.toString e)))))
    (output-task :execution operationgoal)))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message "
  [from [type :as message]]
  (debuglogger search display message)
  (case type
    :operator-execution-msg (operator-execution-handler from message)
    (debug aname (str "unhandled msg: " type))))

(defn initialise
  "Initialises actor:
      registers actor and sets actor state"
  [aname actor-ref]
  (reset! display '())
  (register! aname actor-ref)
  ; cache task-creator reference for performance
  ; (set-state! {:task-creator (whereis :task-creator)})
  )

(defn operator-executor []
  "creates gen-server for operator-executor. This is used by the system supervisor"
  (gen-server
    (reify Server
      (init [_] (initialise aname @self))
      (terminate [_ _])
      (handle-cast [_ from _ message] (msg-handler from message)))))
