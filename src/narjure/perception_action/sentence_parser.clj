(ns narjure.perception-action.sentence-parser
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [narjure.narsese :refer [parse2]]
    [taoensso.timbre :refer [debug info]]
    [narjure.debug-util :refer :all])
  (:refer-clojure :exclude [promise await]))

(def aname :sentence-parser)                                ; actor name
(def display (atom '()))                                    ; for lense output
(def search (atom ""))                                      ; for lense output filtering

(defn narsese-string-handler
  "Parses a narsese string and posts a :sentence-msg to input-load-reducer"
  [from [msg string]]
  (try (let [sentence (parse2 string)]
         (cast! (whereis :task-creator) [:sentence-msg sentence]))
       (catch Exception e (debuglogger search display (str "parsing error " (.toString e))))))

(defn msg-handler
  "Identifies message type and selects the correct message handler.
   if there is no match it generates a log message for the unhandled message "
  [from [type :as message]]
  (debuglogger search display message)
  (case type
    :narsese-string-msg (narsese-string-handler from message)
    (debug aname (str "unhandled msg: " type))))

(defn initialise
  "Initialises actor:
      registers actor and sets actor state"
  [aname actor-ref]
  (reset! display '())
  (register! aname actor-ref))

(defn sentence-parser
  "creates gen-server for sentence-parser. This is used by the system supervisor"
  []
  (gen-server
    (reify Server
      (init [_] (initialise aname @self))
      (terminate [_ _])
      (handle-cast [_ from _ message] (msg-handler from message)))))