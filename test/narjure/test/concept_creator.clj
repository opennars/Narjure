(ns narjure.test.concept-creator
  (:require
    [clojure.test :refer :all]
    [co.paralleluniverse.pulsar.actors
     :refer [! spawn gen-server register! cast! Server self receive
             shutdown! unregister! set-state! state whereis]]
    [co.paralleluniverse.pulsar.core :refer [join]]
    [narjure.memory-management.concept :refer [concept]]
    [narjure.memory-management.concept-creator :refer [concept-creator]]
    [narjure.actor.utils :refer [defactor]]
    [taoensso.timbre :refer [debug info]])
  (:refer-clojure :exclude [promise await]))

;;{term actor-ref}
(def c-map (atom {}))

(deftest concept-dictionary
  (is (= 0 (count @c-map)))
  (is (= {0 12345} (swap! c-map assoc 0 12345)))
  (is (= 1 (count @c-map)))
  (swap! c-map {})
  (is (= {:0 12345} (swap! c-map assoc :0 12345)))
  (is (= 1 (count @c-map)))
  (swap! c-map {})
  (dotimes [n 10]
    (swap! c-map assoc (keyword (str n)) n))
  (is (= 10 (count @c-map)))
  (is (= true (contains? @c-map (keyword (str 5)))))
  )

; test_1 - initialise test
; a. initial state is correct
; b. actor is registered


; test_2 - shutdown test
; a. check :shutdown is handled correctly


; test_3 - clean-up test
; a. ensure all created concepts are shutdown corectly

; test_1 - test processing of :task-msg [task]
; a. no terms exists as concepts (:terms and :occurrence)
; b. all terms exisit as concepts (:terms and :occurrence)
; c. some terms exist as concepts (:terms and :occurrence)

; test_2 - concept-count is maintained correctly

; test_3 concept-limit is reached
; a. :concept-limit-msg is posted to :forgettable-concept-collator

; test_4 - task is returned to task-dispatcher
; a. :task-msg [task] is posted to :task-dispatcher


