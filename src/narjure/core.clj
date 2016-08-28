(ns narjure.core
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [immutant.scheduling :refer :all]
    [narjure.narsese :refer [parse2]]
    [taoensso.timbre :refer [info set-level!]]
    [narjure.defaults :refer :all]
    [narjure.debug-util])
  (:refer-clojure :exclude [promise await])
  (:import (ch.qos.logback.classic Level)
           (org.slf4j LoggerFactory)
           (java.util.concurrent TimeUnit))
  (:gen-class))


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
    ))

(def sup (atom '()))

(defn run []
  (setup-logging)
  (info "NARS initialising...")
  (reset! sup (spawn (supervisor :all-for-one child-specs)))

  ; update user with status
  (info "NARS initialised."))


(defn shutdown []
  (info "Shutting down actors...")
  (shutdown! @sup)
  ;(Thread/sleep 3000)
  (info "System shutdown complete."))

; call main function
(run)
