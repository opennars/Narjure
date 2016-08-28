(ns narjure.sensorimotor
  (:require [narjure.perception-action.operator-executor :refer [registered-operator-functions]]
            [co.paralleluniverse.pulsar.actors :refer [whereis cast!]]
            [narjure.global-atoms :refer [answer-handlers]]))

(defn nars-register-operation
  "Register an operation by providing a callback function."
  [k f]
  #_(reset! registered-operator-functions (assoc @registered-operator-functions k f)))

(defn nars-register-answer-handler
  "Register an answer handler by providing a callback function."
  [f]
  #_(reset! answer-handlers (concat @answer-handlers [f])))

(defn nars-input-narsese
  "Input Narsese into the system."
  [str]
  #_(cast! (whereis :sentence-parser) [:narsese-string-msg str]))