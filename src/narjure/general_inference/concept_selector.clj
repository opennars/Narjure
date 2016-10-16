(ns narjure.general-inference.concept-selector
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]    [narjure.global-atoms :refer [c-bag lense-taskbags lense-termlinks]]
    [narjure.bag :as b]
    [nal.term_utils :refer [syntactic-complexity interval?]]
    [narjure.defaults :refer [max-concept-selections]]
    [clojure.math.numeric-tower :as math]
    [narjure.memory-management.concept-utils :refer [concept-observable]]
    [taoensso.timbre :refer [debug info]]
    [narjure.debug-util :refer :all]
    [narjure.control-utils :refer :all])
  (:refer-clojure :exclude [promise await]))

(def aname :concept-selector)
(def display (atom '()))
(def search (atom ""))

(defn temporal-conjunction-observable?
  [id]
  (and (sequential? id)
       (or (and (= (first id) 'seq-conj)
                (not (interval? (last id)))) ;these are for predictions
           #_(= (first id) '&|))))

(defn observable-temporal-conjunction?
  [id]  ;a temporal conjunction is observable if it is a temporal conjunction that is observable or
  (and (temporal-conjunction-observable? id) ;where each element is either a observable temporal conjunctions itself,
       (or (concept-observable id) ;observable, or an interval
         (let [cnt (count id)]
           (every? (fn [z] (or (interval? z)
                               (concept-observable z)
                               (observable-temporal-conjunction? z)))
                   (for [i (range 1 cnt)]
                     (id i))))))) ;or all components are observable

(defn temporal-linkage-justified
  [id]
  (or (concept-observable id)
      (observable-temporal-conjunction? id)))

(defn strengthen-temporal-link
  " creates a term-link between last-selected concept and the currently selected concept"
  [state selected]
  (when-let [last-selected (:last-selected state)] ;the last selected observable concept
    (when (temporal-linkage-justified (:id selected))   ; todo need to be able to link to itself here (&/, a, a) is valid sequence
      (cast! (:ref selected) [:termlink-strengthen-msg [(:id last-selected)]])
      (cast! (:ref last-selected) [:termlink-strengthen-msg [(:id selected)]])))
  (when (concept-observable (:id selected)) ;one needs to be observable directly to justify linkage
    (set-state! (assoc state :last-selected selected))))

(defn inference-tick-handler
  "Select n concepts for inference and post
   inference-request-message to each selected
   concept"
  [from [msg]]
  (doseq [[k v] @lense-taskbags]                            ;is empty if not in debug so can stay here for now since we
    (when-not (b/exists? @c-bag k)                          ;don't want mem to get full just because lense isn't running
      (swap! lense-taskbags (fn [old] (dissoc old k))) ;element doesnt exist anymore
      (swap! lense-termlinks (fn [old] (dissoc old k)))))
  ; (dotimes [n (min (b/count-elements @c-bag) 1)]
  ;one concept for inference is enough for now ^^

  (doseq [selected (select-concepts max-concept-selections @c-bag)]
    (strengthen-temporal-link @state selected)
    (when (sufficient-priority? selected)
      (cast! (:ref selected) [:inference-request-msg (:id selected)])
      (debuglogger search display (list "Concept selected: " [:task selected :priority (:priority selected)])))))

(defn initialise
  "Initialises actor:
      registers actor and sets actor state"
  [aname actor-ref]
  (reset! display '())
  (register! aname actor-ref)
  (set-state! {}))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message "
  [from [type :as message]]
  ;(debuglogger display message) since tick is uninteresting we use what is selected
  (case type
    :inference-tick-msg (inference-tick-handler from message)
    (debug aname (str "unhandled msg: " type))))

(defn concept-selector []
  (gen-server
    (reify Server
      (init [_] (initialise aname @self))
      (terminate [_ cause] #_(info (str aname " terminated.")))
      (handle-cast [_ from id message] (msg-handler from message)))))
