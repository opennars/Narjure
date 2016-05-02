(ns narjure.test.task-creator-test
  (:use co.paralleluniverse.pulsar.core
        co.paralleluniverse.pulsar.actors)
  (:refer-clojure :exclude [snd promise await])
  (:require
    [narjure.actor.test-utils :refer :all]
    [narjure.perception-action.task-creator :refer [task-creator-gen]]
    [midje.sweet :refer :all]))

(test-setup)

(defn run-task-creator
  "Spawn a mock task-dispatcher (with a channel) and a task-creator.
   See `mock-server-fn`"
  []
  (let [msg-channel (channel)
        task-dispatcher (spawn (mock-server-fn msg-channel))
        task-creator (spawn (task-creator-gen :task-creator task-dispatcher))]
    [task-creator task-dispatcher msg-channel]))

(defn run-task-creator-no-dispatcher
  "Spawn a task-creator without a task-dispatcher."
  []
  (spawn (task-creator-gen :task-creator nil)))

; State for the tests (resetted after each fact)
(def ^:dynamic *task-creator* (atom nil))
(def ^:dynamic *task-dispatcher* (atom nil))
(def ^:dynamic *dispatcher-channel* (atom nil))

(defn start-task-creator-test
  "Initialize the default setup for the tests"
  []
  (let [[task-creator task-dispatcher dispatcher-channel] (run-task-creator)]
    (reset! *task-creator* task-creator)
    (reset! *task-dispatcher* task-dispatcher)
    (reset! *dispatcher-channel* dispatcher-channel)))

(defn stop-task-creator-test
  "Shutdown actors that were started for the tests"
  []
  (shutdown! @*task-creator*)
  (shutdown! @*task-dispatcher*)
  (close! @*dispatcher-channel*)
  (reset! *task-creator* nil)
  (reset! *task-dispatcher* nil)
  (reset! *dispatcher-channel* nil))

(fact "Task creator raises an exception if the task-dispatcher is nil
       and it receives a message."
      (let [task-creator (run-task-creator-no-dispatcher)
            exit-promise (run-exit-watch task-creator)]
        (cast! task-creator [:sentence-msg :empty])
        (exception-type (deref-timeout exit-promise)) => NullPointerException
        (wait-actor-exit task-creator) => (throws NullPointerException)))

(against-background [(before :facts (start-task-creator-test)) (after :facts (stop-task-creator-test))]
  (fact "If the task-creator receives an unknown message, it logs an error"
        (let [task-creator @*task-creator*
              log-channel (setup-test-log-channel)]
          (cast! task-creator [:unknown-msg])
          (test-rcv-log log-channel #"unhandled msg: :unknown-msg")
          ))

  (fact "The task-dispatcher can receive :sentence-msg :derived-sentence-msg :system-time-tick-msg and :shutdown messages"
        (let [log-channel (setup-test-log-channel)
              task-creator @*task-creator*
              ]
          (cast! task-creator [:sentence-msg :empty])
          (cast! task-creator [:derived-sentence-msg :empty])
          (cast! task-creator [:system-time-tick-msg])
          (cast! task-creator [:shutdown])
          (test-channel-empty log-channel) ; Test that no debug logs were generated
          ))

  (fact "If the task-dispatcher is shut down and the task-creator wants to send a message to the task-dispatcher,
the task-creator raises an exception"
        (let [task-creator @*task-creator*
              task-dispatcher @*task-dispatcher*
              exit-promise (run-exit-watch task-creator)]
          (shutdown! task-dispatcher)     ; If we shutdown the task-dispatcher
          (wait-actor-exit task-dispatcher) ; (and wait for the shutdown)
          (cast! task-creator [:sentence-msg :empty]) ; And send a message to the task-creator
          (exception-cause (deref-timeout exit-promise)) => "Task dispatcher exited" ; Then the task-creator throws an exception
          ))

  (fact "The task-creator converts :sentence-msg and :derived-sentence-msg into :task-msg"
        (let [task-creator @*task-creator*
              dispatcher-channel @*dispatcher-channel*]
          (cast! task-creator [:sentence-msg :sentence])
          (cast! task-creator [:derived-sentence-msg :derived-sentence])
          (let [[msg-type {sentence :statement}] (rcv-timeout dispatcher-channel)]
            msg-type => :task-msg
            sentence => :sentence)
          (let [[msg-type {sentence :statement}] (rcv-timeout dispatcher-channel)]
            msg-type => :task-msg
            sentence => :derived-sentence)))

  (fact "The task-creator creates unique tasks (test id increment)"
        (let [task-creator @*task-creator*
              dispatcher-channel @*dispatcher-channel*]
          (cast! task-creator [:sentence-msg :empty])
          (cast! task-creator [:sentence-msg :empty])
          (let [[_ msg1] (rcv-timeout dispatcher-channel)
                [_ msg2] (rcv-timeout dispatcher-channel)]
            (not= msg1 msg2) => true)))

  (fact "The task-creator timestamps the tasks and tasks may have the same timestamp"
        (let [[task-creator _ dispatcher-channel] (run-task-creator)]
          (cast! task-creator [:sentence-msg :empty])
          (cast! task-creator [:sentence-msg :empty])
          (cast! task-creator [:system-time-tick-msg])
          (cast! task-creator [:sentence-msg :empty])
          (let [[_ {time1 :creation}] (rcv-timeout dispatcher-channel)
                [_ {time2 :creation}] (rcv-timeout dispatcher-channel)
                [_ {time3 :creation}] (rcv-timeout dispatcher-channel)]
            time1 => time2
            (not= time2 time3) => true
            ))))
