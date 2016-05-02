(ns narjure.actor.test-utils
  (:use co.paralleluniverse.pulsar.core
        co.paralleluniverse.pulsar.actors)
  (:refer-clojure :exclude [snd promise await])
  (:require
    [narjure.core :refer [disable-third-party-loggers]]
    [midje.sweet :refer :all]
    [taoensso.timbre :refer [set-config! merge-config! set-level!]])
  (:import (java.util.concurrent TimeoutException)))

(defn mock-server-fn
  "Mock server that receives messages and forwards them to a channel.
  Note: This is a server, because it mocks cast! and this is only supported
  by the server behavior.

  `channel` - the channel that receives all messages of the server.
  "
  [channel]
  (gen-server
    (reify Server
      (init [_] ())
      (terminate [_ _])
      (handle-cast [_ _ _ message] (snd channel message)))))

(defn exit-watch-fn
  "Watches another actor. If the other actor exits, delivers the reason to
  the promise

  `actor` - actor that is watched.
  `exit-promise` - promise to deliver the reason to, when the actor exits.
  "
  [actor exit-promise]
  #(let [w (watch! actor)]
    (receive
      [:exit w actor reason] (deliver exit-promise reason))
    nil))

(def test-log-config
  "A log config for tests that require a custom log output."
  {:level :trace
   :appenders
          {:custom-output
           {:enabled?   false
            :async?     true
            :min-level  nil
            :rate-limit [[1 250] [10 5000]]
            :fn         nil}}})

(defn- forward-log-channel
  "Returns an output function for custom logger that forwards the
   logged message to a channel."
  [channel]
  (fn [data] (snd channel ((:output-fn data) data))))

(defn- use-log-channel
  "Update the log config to enable the custom logger.
   See: `forward-log-channel`, `test-log-config`"
  [channel]
  (merge-config! {:appenders {:custom-output {:enabled? true
                                              :fn       (forward-log-channel channel)}}}))

(def ^:dynamic *default-actor-timeout*
  "Default timeout for suspendable functions related to actors in the tests"
  500)

(def ^:dynamic *default-channel-timeout*
  "Default timeout for suspendable functions related to channels in the tests"
  500)

(defn test-channel-empty
  "Test if a channel is empty by using a timeout.
   TODO: If the channel contained a message, the message is lost.
   TODO: nil messages are also considered empty, because it is not possible to
   differentiate between nil and a timeout."
  [channel]
  (fact (rcv channel *default-channel-timeout* :ms) => nil))

(defn rcv-timeout
  "Receive a message from a channel and throw a `TimeoutException`
  if no message is available"
  [channel]
  (if-let [result (rcv channel *default-channel-timeout* :ms)]
    result
    (throw (TimeoutException.))))

(defn wait-actor-exit [actor]
  "Wait until an actor exits."
  (join *default-actor-timeout* :ms actor))

(defn test-setup
  "Default test setup"
  []
  (set-config! test-log-config)
  (set-level! :debug))

(defn run-exit-watch
  "Spawn an actor that delivers to the exit-promise after the passed actor exits.
   Returns the exit-promise.
   `actor` - the actor to watch."
  [actor]
  (let [exit-promise (promise)]
    (spawn (exit-watch-fn actor exit-promise))
    exit-promise))

(defn setup-test-log-channel
  "Return a channel that receives log messages.
   Note: `test-setup` must be called for this to work."
  []
  (let [log-channel (channel)]
    (use-log-channel log-channel)
    log-channel))

(defn exception-type
  "Return exception type of a Throwable"
  [throwable]
  (-> throwable Throwable->map :via first :type))

(defn exception-cause
  "Return exception cause of a Throwable"
  [throwable]
  (-> throwable Throwable->map :cause))

(defn deref-timeout
  "Deref with a default timeout."
  [ref]
  (deref ref *default-actor-timeout* :timeout))

(defn test-rcv-log
  "Test if regex can be found inside the latest message in the channel"
  [log-channel regex]
  (let [log-output (rcv-timeout log-channel)]
    (= nil log-output) => false
    (boolean (re-find regex log-output)) => true
    )
  )
