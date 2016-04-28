(ns narjure.memory-management.persistence-manager
  (:require
    [co.paralleluniverse.pulsar.actors :refer [self ! whereis cast! Server gen-server register! shutdown! unregister! set-state! state]]
    [narjure.actor.utils :refer [defactor]]
    [narjure.memory-management.task-dispatcher :refer [c-map]]
    [narjure.memory-management.concept-creator :refer [create-concept]]
    [narjure.actor.utils :refer [read-seq-from-file]]
    [environ.core :refer [env]]
    [taoensso.timbre :refer [debug info]])
  (:refer-clojure :exclude [promise await]))

(def aname :persistence-manager)

(defn persist-concept-state-handler
  "Posts :concept-state-request-msg to each concept in c-map"
  [from [_ path]]
  (set-state! (assoc @state :path path :concept-count (count @c-map) :received-states 0))
  (doseq [concept-ref (vals @c-map)]
    (cast! concept-ref [:concept-state-request-msg]))
  #_(debug aname "process-persist-concept-state-msg"))

(defn concept-state-handler
  "process each :concept-state-msg by serialising the state to backing store.
   where state specifies the path of the backing store. The number of received
   states is tracked. The file is overwritten."
  [from [_ concept-state] state]
  (let [c-state (dissoc concept-state
                        :active-concept-collator
                        :general-inferencer
                        :forgettable-concept-collator)
        {:keys [path concept-count]} state]
    (if (zero? (:received-states state))
      (spit path c-state)
      (spit path c-state :append true))
    (set-state! (update state :received-states inc))
    (when (= (:received-states state) concept-count)
      (info aname "Persisting concept state to disk complete"))))

(defn restore-concept-state-handler
  "read concept state from passed path, create concept for each 'record'
   and set the state as the 'record'"
  [from [_ path]]
  (info (str "Path: " path))
  (doseq [c-state (read-seq-from-file [path])]
    (info (str "read state: " c-state))
    (let [c-name (:name c-state)]
      (create-concept c-name c-map)
      (info (str "Passing comcept state"))
      (cast! c-name [:set-concept-state-msg c-state]))))

(defn shutdown-handler
  "Processes :shutdown-msg and shuts down actor"
  [from msg]
  (unregister!)
  (shutdown!))

(defn initialise
  "Initialises actor: registers actor and sets actor state"
  [aname actor-ref]
  (register! aname actor-ref)
  (set-state! {:path (env :persistence-path) :concept-count 0 :received-states 0}))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message "
  [from [type :as message]]
  (case type
    :persist-concept-state-msg (persist-concept-state-handler from message)
    :restore-concept-state-msg (restore-concept-state-handler from message)
    :concept-state-msg (concept-state-handler from message @state)
    :shutdown (shutdown-handler from message)
    (debug aname (str "unhandled msg: " type))))

(def persistence-manager (gen-server
                         (reify Server
                           (init [_] (initialise aname @self))
                           (terminate [_ cause] #_(info (str aname " terminated.")))
                           (handle-cast [_ from id message] (msg-handler from message)))))
