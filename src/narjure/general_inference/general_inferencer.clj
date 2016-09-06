(ns narjure.general-inference.general-inferencer
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [nal
     [deriver :refer [inference]]
     [term_utils :refer [syntactic-complexity]]]
    [nal.deriver :refer [inference]]
    [taoensso.timbre :refer [debug info]]
    [narjure
     [debug-util :refer :all]
     #_[budget-functions :refer [derived-budget]]
     [defaults :refer :all]
     [control-utils :refer [make-evidence non-overlapping-evidence?]]])
  (:refer-clojure :exclude [promise await]))

(def display (atom '()))
(def search (atom ""))

(defn derived-budget [task derived] [0.5 0.5])              ;to do temporary

(defn do-inference-handler
  "Processes :do-inference-msg:
    generates derived results, budget and occurrence time for derived tasks.
    Posts derived sentences to task creator"
  [from [msg {{task :task belief :belief} :id}]]
  ;(println (str "task: " task " belief: " belief))
  (try
    (when (non-overlapping-evidence? (:evidence task) (:evidence belief))
      (let [pre-filtered-derivations (inference task belief)
            filtered-derivations (filter #(not= (:statement %) (:parent-statement task)) pre-filtered-derivations)
            evidence (make-evidence (:evidence task) (:evidence belief))
            task-creator (whereis :task-creator)]
        (when-not (empty? evidence)
          (doseq [derived filtered-derivations]
            (println (str "derived: " derived))
            ;TEMP - derived budget
            (let [sc (syntactic-complexity (:statement derived))
                  derived (assoc derived :sc sc)
                  derived-budget [0.9 0.5]                           ;(derived-budget task derived)
                  derived-task (assoc derived :budget derived-budget
                                              :evidence evidence
                                              :parent-statement (:statement task))]
              (println (str "derived-task: " derived-task))
              (cast! task-creator [:derived-sentence-msg [nil
                                                          nil
                                                          derived-task]]))
            #_(let [sc (syntactic-complexity (:statement derived))
                  derived (assoc derived :sc sc)            ; required for derived-budget
                  budget (derived-budget task derived)
                  derived-task (assoc derived :budget budget
                                              :parent-statement (:statement task)
                                              :evidence evidence)]
              (when (and budget
                         (< sc max-term-complexity)
                         (> (first budget) priority-threshold)
                         (or (not (:truth derived-task))
                             (> (rand) 0.98)
                             (> (first (:truth derived-task)) 0.5))
                         (coll? (:statement derived-task)))
                (cast! task-creator [:derived-sentence-msg [nil
                                                           nil
                                                           derived-task]])))))))
    (catch Exception e (debuglogger search display (str "inference error " (.toString e))))))

(defn initialise
  "Initialises actor:
      registers actor and sets actor state"
  [aname actor-ref]
  (reset! display '())
  (register! aname actor-ref))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message "
  [from [type :as message]]
  (debuglogger search display message)
  (case type
    :do-inference-msg (do-inference-handler from message)
    (debug :ge (str "unhandled msg: " type))))

(defn general-inferencer [aname]
  (gen-server
    (reify Server
      (init [_] (initialise aname @self))
      (terminate [_ cause] #_(info (str aname " terminated.")))
      (handle-cast [_ from id message] (msg-handler from message)))))