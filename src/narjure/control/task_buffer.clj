(ns narjure.control.task-buffer
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [narjure.debug-util :refer :all]
    [narjure.defaults :refer :all]
    [narjure.control.bounded-priority-queue :as q])
  (:refer-clojure :exclude [promise await]))

(def aname :task-buffer)                                ; actor name
(def display (atom '()))                                    ; for lense output
(def search (atom ""))                                      ; for lense output filtering

(defn task-handler
  "Processes a :task-msg"
  [from [_ task]]
  (debuglogger search display task)
  (let [buffer (:buffer @state)
        [_ buffer'] (q/push-element buffer (q/el task (first (:budget task))))]
    (set-state! (assoc @state :buffer buffer'))))

(defn system-time-tick-handler
  [from _]
  (let [concept-manager (whereis :concept-manager)
        buffer (:buffer @state)]
    (doseq [el (:priority-index buffer)]
      (cast! concept-manager [:task-msg (:id el)]))
    (set-state! (assoc @state :buffer (q/default-queue max-tasks)))))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message "
  [from [type :as message]]
  (case type
    :task-msg (task-handler from message)
    :system-time-tick-msg (system-time-tick-handler from message)
    (debug aname (str "unhandled msg: " type))))

(defn initialise
  "Initialises actor:
      registers actor and sets actor state"
  [aname actor-ref]
  (reset! display '())
  (register! aname actor-ref)
  (set-state! {:buffer (q/default-queue max-tasks)}))

(defn task-buffer
  "creates gen-server for sentence-parser. This is used by the system supervisor"
  []
  (gen-server
    (reify Server
      (init [_] (initialise aname @self))
      (terminate [_ _])
      (handle-cast [_ from _ message] (msg-handler from message)))))