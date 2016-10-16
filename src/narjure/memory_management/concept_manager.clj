(ns narjure.memory-management.concept-manager
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [clojure.java.io :as io]
    [narjure.global-atoms :refer [c-bag nars-time nars-id]]
    [narjure.memory-management.concept :as c]
    [narjure.bag :as b]
    [narjure.defaults :refer [c-priority]]
    [nal.term_utils :refer [concept-term-transform]]
    [taoensso.timbre :refer [debug info]]
    [narjure.debug-util :refer :all])
  (:refer-clojure :exclude [promise await]))

(def aname :concept-manager)                                ; actor name
(def display (atom '()))                                    ; for lense output
(def search (atom ""))                                      ; for lense output filtering

(defn make-general-concept
  "Create a concept, for the supplied term, and add to
   the concept bag"
  [term]
  (let [concept-ref (spawn (c/concept term))]
    (swap! c-bag b/add-element {:id term :priority c-priority :quality 0.0 :observable false :ref concept-ref})
    concept-ref))

(defn create-concept-handler
  "Create a concept for each term in statement, if they dont
   exist. Then post the task back to task-dispatcher."
  [from [_ [task-concept-id belief-concept-id {:keys [statement] :as task}]]]
  (doseq [term_ (:terms task)]
    (let [term (concept-term-transform term_)]
      (when-not (b/exists? @c-bag term)
        (make-general-concept term))))
  (cast! from [:task-from-cmanager-msg [task-concept-id belief-concept-id task]]))

(defn persist-state-handler
  "Posts :concept-state-request-msg to each concept in c-map"
  [from [_ path]]
  (set-state! (assoc @state :concept-count (count @c-bag)
                            :received-states 0))
  (spit (:path @state) {:nars-time @nars-time :nars-id @nars-id})
  (doseq [[_ {c-ref :ref}] (:elements-map @c-bag)]
    (cast! c-ref [:concept-state-request-msg])))

(defn concept-state-handler
  "process each :concept-state-msg by serialising the state to backing store.
   where state specifies the path of the backing store. The number of received
   states is tracked. The file is overwritten."
  [from [_ concept-state]]
  (let [c-state (dissoc concept-state
                        :inference-request-router
                        :concept-manager)
        {:keys [path concept-count]} @state]
    (spit path (pr-str c-state) :append true)
    (set-state! (update @state :received-states inc))
    (when (= (:received-states @state) concept-count)
      (info aname "Persisting concept state to disk complete"))))

(defn read-one
  ""
  [r]
  (try
    (read r)
    (catch java.lang.RuntimeException e
      (if (= "EOF while reading" (.getMessage e))
        ::EOF
        (throw e)))))

(defn read-seq-from-file
  ""
  [path]
  (with-open [r (java.io.PushbackReader. (clojure.java.io/reader path))]
    (binding [*read-eval* true]
      (doall (take-while #(not= ::EOF %) (repeatedly #(read-one r)))))))

(defn load-state-handler
  "read concept state from passed path, create concept for each 'record'
   and set the state as the 'record'"
  [from [_ path]]
  (doseq [c-state (read-seq-from-file (:path @state))]
    (if (:nars-time c-state )
      (do
        (reset! nars-time (:nars-time c-state))
        (reset! nars-id (:nars-id c-state)))
       (cast! (make-general-concept (:id c-state)) [:set-concept-state-msg c-state]))))

(defn clean-up
  "Shutdown all concept actors"
  []
  (doseq [[_ {actor-ref :ref}] (:elements-map @c-bag)]
    (shutdown! actor-ref)))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message "
  [from [type :as message]]
  (debuglogger search display message)
  (case type
    :create-concept-msg (create-concept-handler from message)
    :persist-state-msg (persist-state-handler from message)
    :concept-state-msg (concept-state-handler from message)
    :load-state-msg (load-state-handler from message)
    (debug aname (str "unhandled msg: " type))))

(defn initialise
  "Initialises actor: registers actor and sets actor state"
  [aname actor-ref]
  (reset! c/display '())                                    ;we also reset concept display here
  (reset! display '())                                      ;since concept actor startup is not
  (register! aname actor-ref)                               ;a place where it can be done
  (set-state! {:path "memory.snapshot"
               :concept-count 0
               :received-states 0}))

(defn concept-manager
  "creates gen-server for concept-manager. This is used by the system supervisor"
  []
  (gen-server
    (reify Server
      (init [_] (initialise aname @self))
      (terminate [_ _] (clean-up))
      (handle-cast [_ from _ message] (msg-handler from message)))))

