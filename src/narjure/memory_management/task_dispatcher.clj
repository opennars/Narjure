(ns narjure.memory-management.task-dispatcher
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [nal.term_utils :refer [concept-term-transform]]
    [narjure.global-atoms :refer [c-bag]]
    [narjure.memory-management.local-inference.local-inference-utils :refer [get-task-id]]
    [narjure.bag :as b]
    [taoensso.timbre :refer [debug info]]
    [narjure.debug-util :refer :all])
  (:refer-clojure :exclude [promise await]))

(def aname :task-dispatcher)                                ; actor name
(def display (atom '()))                                    ; for lense output
(def search (atom ""))                                      ; for lense output filtering

(defn event?
  "return true if task is event otherwise false"
  [{:keys [occurrence]}]
  (not= occurrence :eternal))

(defn term-exists?
  "Returns true if concept bag contains term"
  [term]
  (b/exists? @c-bag term))

(defn task-handler
  "If concept, or any sub concepts, do not exist post task to concept-creator,
   otherwise, dispatch task to respective concepts. Also, if task is an event
   dispatch task to event buffer actor."
  [from [_ [task-concept-id_ belief-concept-id_ task]]]
  (let [task-concept-id (concept-term-transform task-concept-id_)
        belief-concept-id (concept-term-transform belief-concept-id_)
        terms (map concept-term-transform (:terms task))]
    (if (every? term-exists? terms)
      (let [task (dissoc task :terms)]
        (when task-concept-id
          (when-let [{c-ref :ref} ((:elements-map @c-bag) task-concept-id)]
            (cast! c-ref [:link-feedback-msg [task belief-concept-id]])))
        (doseq [term terms]
          (when-let [{c-ref :ref} ((:elements-map @c-bag) term)]
            (cast! c-ref [:task-msg [task]]))))
      (cast! (whereis :concept-manager) [:create-concept-msg [task-concept-id belief-concept-id task]]))))

(defn task-from-cmanager-handler
  "If concept, or any sub concepts, do not exist post task to concept-creator,
   otherwise, dispatch task to respective concepts. Also, if task is an event
   dispatch task to event buffer actor."
  [from [_ [task-concept-id belief-concept-id task]]] ;interval transform already applied to task-concept-id, belief-concept-id
  (let [terms (map concept-term-transform (:terms task))]
    (let [task (dissoc task :terms)]
      (when task-concept-id
        (when-let [{c-ref :ref} ((:elements-map @c-bag) task-concept-id)]
          (cast! c-ref [:link-feedback-msg [task belief-concept-id]])))
      (doseq [term terms]
        (when (b/exists? @c-bag term)
          (when-let [{c-ref :ref} ((:elements-map @c-bag) term)]
            (cast! c-ref [:task-msg [task]])))))))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message"
  [from [type :as message]]
  (debuglogger search display message)
  (case type
    :task-msg (task-handler from message)
    :task-from-cmanager-msg (task-from-cmanager-handler from message)
    (debug aname (str "unhandled msg: " type))))

(defn initialise
  "Initialises actor:
    registers actor and sets actor state"
  [aname actor-ref]
  (reset! display '())
  (register! aname actor-ref)
  ; cache actor references for performance
  ;(set-state! {:concept-manager (whereis :concept-manager)})
  )

(defn task-dispatcher
  "creates gen-server for task-dispatcher. This is used by the system supervisor"
  []
  (gen-server
    (reify Server
      (init [_] (initialise aname @self))
      (terminate [_ _])
      (handle-cast [_ from _ message] (msg-handler from message)))))