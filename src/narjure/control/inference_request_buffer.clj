(ns narjure.control.inference-request-buffer
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

(def aname :inference-request-buffer)                                ; actor name
(def display (atom '()))                                    ; for lense output
(def search (atom ""))                                      ; for lense output filtering

(defn inference-request-buffer-element
  "Create an element from the task"
  [inference-req]
  {:id inference-req :priority (first (:budget inference-req)) :task inference-req})

(defn inference-req-handler
  "Processes a :inference-req-msg and adds it to buffer."
  [from [_ inference-req]]
  (let [buffer (:buffer @state)]
    (set-state! (assoc @state :buffer (b/add-element buffer (inference-request-buffer-element inference-req))))))

(defn buffer-select-handler
  [from _]
  (let [inference-req-router (whereis :inference-req-router)]
    (doseq [el (:element-map (:buffer @state))]
      (cast! inference-req-router [:task-msg (:inference-req-router el)]))))


(defn inference-tick
  "Apply an inference tick"
  []
  (cast! (whereis :inference-request-buffer)
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
    :inference-req-msg (inference-req-handler from message)
    :timer-start-msg (timer-start-handler from message)
    :timer-stop-msg (timer-stop-handler from message)
    :buffer-select-msg (buffer-select-handler from message)
    (debug aname (str "unhandled msg: " type))))
;
(defn initialise
  "Initialises actor:
      registers actor and sets actor state"
  [aname actor-ref]
  (reset! display '())
  (register! aname actor-ref)
  (set-state! {:buffer (b/default-bag max-tasks)})
  (timer-start-handler nil nil))

(defn inference-request-buffer
  "creates gen-server for inference-request-buffer. This is used by the system supervisor"
  []
  (gen-server
    (reify Server
      (init [_] (initialise aname @self))
      (terminate [_ _])
      (handle-cast [_ from _ message] (msg-handler from message)))))