(ns narjure.perception-action.task-creator
  (:require
    [narjure.actor.utils :refer [defactor]]
    [taoensso.timbre :refer [debug]])
  (:refer-clojure :exclude [promise await]))

(declare task-creator sentence)

(defactor task-creator
  "Creates task from Sentence
  - Sets source property based on origin
  - Add serial-no
  - Converts tense to occurrence time (has system time in state)"
  {:sentence-msg  sentence})

(defn sentence [_ _]
  (debug aname "process-sentence"))