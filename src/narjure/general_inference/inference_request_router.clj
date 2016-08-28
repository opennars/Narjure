(ns narjure.general-inference.inference-request-router
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [nal.deriver :refer [inference]]
    [taoensso.timbre :refer [debug info]]
    [narjure.global-atoms :refer :all]
    [narjure.debug-util :refer :all]
    [narjure.defaults :refer [priority-threshold]]
    [narjure.control-utils :refer [make-evidence]]
    [nal.deriver.truth :refer [expectation]])
  (:refer-clojure :exclude [promise await]))

(def aname :inference-request-router)
(def display (atom '()))
(def search (atom ""))

(defn get-next-routee []

  (let [routee ((:routees @state) (:next-routee @state))]
    (set-state! (update @state :next-routee #(mod (inc %) (:num-routees @state))))
    ;(println (str ":routee: " routee " next: " (:next-routee @state)))
    (whereis routee)))

(defn do-inference-handler
  "Processes :do-inference-msg:
    posts msg to next routee in round robin fashion"
  [from message]
  (cast! (get-next-routee) message)
  )

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message "
  [from [type :as message]]
  (debuglogger search display message)
  (case type
    :do-inference-msg (do-inference-handler from message)
    (debug aname (str "unhandled msg: " type))))

(defn initialise
  "Initialises actor:
      registers actor and sets actor state"
  [aname actor-ref]
  (reset! display '())
  (register! aname actor-ref)
  (let [new-state {:num-routees 5
                   :next-routee 0
                   :routees [:ge0 :ge1 :ge2 :ge3 :ge4]}]
    (set-state! new-state)))

(defn inference-request-router []
  (gen-server
    (reify Server
      (init [_] (initialise aname @self))
      (terminate [_ cause] #_(info (str aname " terminated.")))
      (handle-cast [_ from id message] (msg-handler from message)))))