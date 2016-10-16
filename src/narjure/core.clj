(ns narjure.core
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [immutant.scheduling :refer :all]
    [narjure.global-atoms :refer :all]
    [narjure.memory-management
     [concept-manager :refer [concept-manager]]
     [forgettor :refer [forgettor]]
     [task-dispatcher :refer [task-dispatcher]]]
    [narjure.general-inference
     [concept-selector :refer [concept-selector]]
     [general-inferencer :refer [general-inferencer]]
     [inference-request-router :refer [inference-request-router]]]
    [narjure.perception-action
     [operator-executor :refer [operator-executor]]
     [sentence-parser :refer [sentence-parser]]
     [derived-load-reducer :refer [derived-load-reducer]]
     [task-creator :refer [task-creator lastevent]]]
    [narjure.narsese :refer [parse2]]
    [taoensso.timbre :refer [info set-level!]]
    [narjure.bag :as b]
    [narjure.defaults :refer :all]
    [narjure.debug-util ])
  (:refer-clojure :exclude [promise await])
  (:import (ch.qos.logback.classic Level)
           (org.slf4j LoggerFactory)
           (java.util.concurrent TimeUnit))
  (:gen-class))


(defn inference-tick
  "Apply an inference tick"
  []                                     ;inference-tick-interval is fast enough compared to system-tick-interval
  (cast! (whereis :concept-selector) [:inference-tick-msg]))

(defn system-tick
  "Apply an system tick"
  []
  (cast! (whereis :task-creator) [:system-time-tick-msg])
  (cast! (whereis :derived-load-reducer) [:system-time-tick-msg])
  (cast! (whereis :forgettor) [:system-time-tick-msg]))

(defn sentence-tick []
  (cast! (whereis :sentence-parser) [:narsese-string-msg
                                     (format "<%s-->%s>.:|10|:"
                                             (rand-nth ["a" "b" "c" "d" "e" "f" "g"])
                                             (rand-nth ["h" "p" "j" "k" "l" "m" "n"]))]))

(defn prn-ok [msg interval] (info (format "\t[OK] %s (%d ms)" msg interval)))

(defn start-timers
  "Start all relevant NARS timers."
  []
  (info "Initialising system timers...")
  (schedule inference-tick {:in    @inference-tick-interval
                            :every @inference-tick-interval})
  (prn-ok :inference-timer @inference-tick-interval)

  (schedule system-tick {:every @system-tick-interval})
  (prn-ok :system-timer @system-tick-interval)

  ;uncomment following two line to auto generate input sentences
  ;(schedule sentence-tick {:every sentence-tick-interval})
  ;(prn-ok :sentence-timer)

  (info "System timer initialisation complete."))

(defn disable-third-party-loggers []
  (doseq [logger ["co.paralleluniverse.actors.behaviors.ServerActor"
                  "co.paralleluniverse.actors.JMXActorMonitor"
                  "org.quartz.core.QuartzScheduler"
                  "co.paralleluniverse.actors.LocalActorRegistry"
                  "co.paralleluniverse.actors.ActorRegistry"
                  "org.projectodd.wunderboss.scheduling.Scheduling"]]
    (.setLevel (LoggerFactory/getLogger logger) Level/ERROR)))

(defn setup-logging []
  (set-level! :debug)
  (disable-third-party-loggers))

; supervisor test code
(def child-specs
  #(list
    ["forgettor" :permanent 5 5 :sec 100 (forgettor)]
    ["0" :permanent 5 5 :sec 100 (inference-request-router)]
    ["1" :permanent 5 5 :sec 100 (derived-load-reducer)]
    ["2.0" :permanent 5 5 :sec 100 (general-inferencer :ge0)]
    ["2.1" :permanent 5 5 :sec 100 (general-inferencer :ge1)]
    ["2.2" :permanent 5 5 :sec 100 (general-inferencer :ge2)]
    ["2.3" :permanent 5 5 :sec 100 (general-inferencer :ge3)]
    ["2.4" :permanent 5 5 :sec 100 (general-inferencer :ge4)]
    ["3" :permanent 5 5 :sec 100 (concept-selector)]
    ["4" :permanent 5 5 :sec 100 (concept-manager)]
    ["5" :permanent 5 5 :sec 100 (task-dispatcher)]
    ["6" :permanent 5 5 :sec 100 (task-creator)]
    ["7" :permanent 5 5 :sec 100 (operator-executor)]
    ["8" :permanent 5 5 :sec 100 (sentence-parser)]))

(def sup (atom '()))

(defn run []

  (reset! lastevent nil)
  (reset! max-term-complexity max-term-complexity-init)
  (info "reset question filter")
  (reset! last-qu-answers [])
  (info "Resetting concepts bagss:")
  (reset! c-bag (b/default-bag max-concepts))
  (info "c-bag count: " (b/count-elements @c-bag))
  ;(reset! e-bag (b/default-bag max-events))
  ;(info "e-bag count: " (b/count-elements @e-bag))

  (info "Reset system Parameters:")
  (reset! nars-id -1)
  (info "nars-id: " @nars-id)
  (reset! nars-time 0)
  (info "nars-time: " @nars-time)

  (setup-logging)
  (info "NARS initialising...")
  (start-timers)

  (reset! output-display '())

  (reset! sup (spawn (supervisor :all-for-one child-specs)))

  ; update user with status
  (info "NARS initialised."))

(defn stop-timers []
  (stop))

(defn shutdown []
  (info "Shutting down actors...")

  ; cancel schedulers
  (stop-timers)

  (shutdown! @sup)
  ;(Thread/sleep 3000)
  (info "System shutdown complete."))

; call main function
(run)
