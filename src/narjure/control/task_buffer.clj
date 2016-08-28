(ns narjure.control.task-buffer
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [narjure.debug-util :refer :all]
    [narjure.defaults :refer :all]
    [narjure.control.bag :as b]
    [immutant.scheduling :refer :all])
  (:refer-clojure :exclude [promise await]))

(def aname :task-buffer)                                ; actor name
(def display (atom '()))                                    ; for lense output
(def search (atom ""))                                      ; for lense output filtering

(defn task-buffer-element
  "Create an element from the task"
  [task]
  {:id task :priority (first (:budget task)) :task task})

(defn task-handler
  "Processes a :task-msg and generates a task, and an eternal task."
  [from [_ task]]
  (let [buffer (:buffer @state)]
    (set-state! (assoc @state :buffer (b/add-element buffer (task-buffer-element task))))))

(defn buffer-select-handler
  [from _]
  (let [task-dispatcher (whereis :task-dispatcher)]
    (doseq [el (:element-map (:buffer @state))]
     (cast! task-dispatcher [:task-msg (:task el)]))))


(defn inference-tick
  "Apply an inference tick"
  []
  (cast! (whereis :task-buffer)
         [:buffer-select-msg nil])
  )

(defn timer-start-handler
  [from _]
  (schedule inference-tick {:in    @inference-tick-interval
                            :every @inference-tick-interval}))

(defn timer-stop-handler
  [from _]
  (stop))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message "
  [from [type :as message]]
  (debuglogger search display message)
  (case type
    :task-msg (task-handler from message)
    :timer-start-msg (timer-start-handler from message)
    :timer-stop-msg (timer-stop-handler from message)
    :buffer-select-msg (buffer-select-handler from message)
    (debug aname (str "unhandled msg: " type))))
; (set-state! {:last-forgotten 0})
(defn initialise
  "Initialises actor:
      registers actor and sets actor state"
  [aname actor-ref]
  (reset! display '())
  (register! aname actor-ref)
  (set-state! {:buffer (b/default-bag max-tasks)})
  (timer-start-handler nil nil))

(defn task-buffer
  "creates gen-server for sentence-parser. This is used by the system supervisor"
  []
  (gen-server
    (reify Server
      (init [_] (initialise aname @self))
      (terminate [_ _])
      (handle-cast [_ from _ message] (msg-handler from message)))))