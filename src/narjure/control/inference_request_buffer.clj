(ns narjure.control.inference-request-buffer
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [narjure.debug-util :refer :all]
    [narjure.defaults :refer :all]
    [narjure.control.bounded-priority-queue :as q])
  (:refer-clojure :exclude [promise await]))

(def aname :inference-request-buffer)                                ; actor name
(def display (atom '()))                                    ; for lense output
(def search (atom ""))                                      ; for lense output filtering

(defn inference-req-handler
  "Processes a :inference-req-msg and adds it to buffer."
  [from [_ inference-req]]
  (debuglogger search display inference-req)
  (let [buffer (:buffer @state)
        [_ buffer'] (q/push-element buffer (q/el inference-req (first (:budget inference-req))))]
    (set-state! (assoc @state :buffer buffer'))))

(defn inference-tick-handler
  [from _]
  (let [inference-req-router (whereis :inference-request-router)]
    (doseq [el (:priority-index (:buffer @state))]
      (cast! inference-req-router [:inference-req el]))
    (set-state! (assoc @state :buffer (q/default-queue max-tasks)))))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message "
  [from [type :as message]]
  (debuglogger search display message)
  (case type
    :inference-req-msg (inference-req-handler from message)
    :inference-tick-msg (inference-tick-handler from message)
    (debug aname (str "unhandled msg: " type))))
;
(defn initialise
  "Initialises actor:
      registers actor and sets actor state"
  [aname actor-ref]
  (reset! display '())
  (register! aname actor-ref)
  (set-state! {:buffer (q/default-queue max-tasks)}))

(defn inference-request-buffer
  "creates gen-server for inference-request-buffer. This is used by the system supervisor"
  []
  (gen-server
    (reify Server
      (init [_] (initialise aname @self))
      (terminate [_ _])
      (handle-cast [_ from _ message] (msg-handler from message)))))