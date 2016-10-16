(ns narjure.perception-action.task-creator
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [clojure.set :as set]
    [narjure
     [global-atoms :refer :all]
     [defaults :refer :all]
     [debug-util :refer :all]
     [bag :as b]
     [control-utils :refer :all]]
    [nal.term_utils :refer :all]
    [nal.deriver.projection-eternalization :refer [eternalize]])
  (:refer-clojure :exclude [promise await]))

(def aname :task-creator)                                   ; actor name
(def display (atom '()))                                    ; for lense output
(def search (atom ""))                                      ; for lense output filtering

(defn system-time-tick-handler
  "inc :time value in global atom for each system-time-tick-msg"
  []
  (swap! nars-time inc))

(defn get-id
  "inc :nars-id in global atom after each use"
  []
  (swap! nars-id inc))

(defn create-new-task
  "create a new task with the provided sentence and default values
   convert tense to occurrence time if applicable"
  [sentence syntactic-complexity]
  (let [occurrence (:occurrence sentence)
        toc (case occurrence
              :eternal :eternal
              (+ occurrence @nars-time))
        content (:statement sentence)
        task-type (:task-type sentence)]
    {:truth (:truth sentence)
     ;:desire (:desire sentence)
     :budget (:budget sentence)
     :occurrence toc
     :source :input
     :evidence (list (get-id))
     :sc syntactic-complexity
     :terms (termlink-subterms content)
     :solution nil
     :task-type task-type
     :statement content}))

(defn create-eternal-task
  "Create an eternal task from a non-eternal task"
  [task]
  (eternalize task))

(defn create-derived-task
  "Create a derived task with the provided sentence, budget and occurence time
   and default values for the remaining parameters"
  [sentence]
  (let [content (:statement sentence)]
    {:truth      (:truth sentence)
     :budget     (:budget sentence)
     :occurrence (:occurrence sentence)
     :source     :derived
     :evidence   (:evidence sentence)
     :sc         (:sc sentence)
     :terms      (termlink-subterms content)
     :solution   nil
     :task-type  (:task-type sentence)
     :statement  content
     :parent-statement (:parent-statement sentence)}))

(defn budgetify [task]
  )

(defn event?
  "tests whether the passed task sentence is an event"
  [sentence]
  (not= :eternal (:occurrence sentence)))

(def lastevent (atom nil))

(defn sentence-handler
  "Processes a :sentence-msg and generates a task, and an eternal task
   if the sentence is an event, and posts to task-dispatcher."
  [from [_ sentence]]
  (let [synt-complexity (syntactic-complexity (:statement sentence))]
    (when true #_(< syntactic-complexity max-term-complexity)
      (reset! max-term-complexity (max @max-term-complexity   ;adapt max. term complexity in respect to examples for now
                                      (+ term-complexity-offset
                                         synt-complexity)))
      (let [new-task (create-new-task
                       sentence
                       synt-complexity)
            task-dispatcher (whereis :task-dispatcher)]
        (cast! task-dispatcher [:task-msg [nil nil new-task]])
        (output-task :input new-task)
        (when (event? sentence)
          (when (and @lastevent
                     (= (:task-type new-task) :belief)
                     (> (first (:truth new-task)) 0.5))
            (cast! (whereis :inference-request-router) [:do-inference-msg [nil nil new-task @lastevent]]))

          (when (and (belief? new-task)
                     (not (operation? (:statement new-task)))
                     (> (first (:truth new-task)) 0.5))
            (reset! lastevent new-task))
          (cast! task-dispatcher [:task-msg [nil nil (create-eternal-task new-task)]]))))))

(defn derived-sentence-handler
  "processes a :derived-sentence-msg and posts to task-dispatcher"
  [from [msg [task-concept-id belief-concept-id sentence]]]
  (let [derived-task (create-derived-task sentence)
        task-dispatcher (whereis :task-dispatcher)]
    (cast! task-dispatcher [:task-msg [task-concept-id belief-concept-id derived-task]])
    ; display task in output window
    (output-task :derived derived-task)
    (when (event? sentence)
      (cast! task-dispatcher [:task-msg [task-concept-id belief-concept-id (create-eternal-task derived-task)]]))))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message "
  [from [type :as message]]
  ; don't output :system-time-tick-msg's to logger
  (when (not= type :system-time-tick-msg) (debuglogger search display message))
  (case type
    :sentence-msg (sentence-handler from message)
    :derived-sentence-msg (derived-sentence-handler from message)
    :system-time-tick-msg (system-time-tick-handler)
    ; unhandled case - report to debug logger (console by default)
    (debug aname (str "unhandled msg: " type))))

(defn initialise
  "Initialises actor:
      registers actor and sets actor state"
  [aname actor-ref]
  (reset! display '())
  (register! aname actor-ref)
  ; caches task-dispatcher reference for performance
  ;(set-state! {:task-dispatcher (whereis :task-dispatcher)})
  ;(cast! (whereis :derived-load-reducer) :register-task-creator-msg [])
  )

(defn task-creator
  "creates gen-server for task-creator. This is used by the system supervisor"
  []
  (gen-server
    (reify Server
      (init [_] (initialise aname @self))
      (terminate [_ _])
      (handle-cast [_ from _ message] (msg-handler from message)))))
