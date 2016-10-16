(ns gui.hud
  (:require [seesaw.core :refer :all]
            [gui.globals :refer :all]
            [co.paralleluniverse.pulsar.actors :refer [whereis cast!]]
            [clojure.string :as str]
            [narjure.core :refer [start-timers shutdown run stop-timers]]
            [gui.gui-utils :refer :all]
            [narjure.defaults :refer :all]))

(def backcolor [230 230 230 100])
(def hud-width 50)
(def hud-height 25)

;TODO put at proper place
(defn set-fast-speed []
  "Sets the speed of the reasoner to fast"
  (reset! system-tick-interval system-tick-interval-fast)
  (reset! inference-tick-interval inference-tick-interval-fast)
  (reset! speed "fast")
  (stop-timers)
  (start-timers))

;TODO put at proper place
(defn set-medium-speed []
  "Sets the speed of the reasoner to fast"
  (reset! system-tick-interval system-tick-interval-medium)
  (reset! inference-tick-interval inference-tick-interval-medium)
  (reset! speed "medium")
  (stop-timers)
  (start-timers))

;TODO put at proper place
(defn set-slow-speed []
  "Sets the speed of the reasoner to slow"
  (reset! system-tick-interval system-tick-interval-slow)
  (reset! inference-tick-interval inference-tick-interval-slow)
  (reset! speed "slow")
  (stop-timers)
  (start-timers))

(defn swap-speed []
  (if (= @speed "slow")
    (set-fast-speed)
    (if (= @speed "fast")
      (set-medium-speed)
      (set-slow-speed))))

(def nodes [{:name :pop-up :px 710 :py 0 :onclick (fn [state]
                                                       (cast! (whereis :sentence-parser) [:narsese-string-msg (str (input "Add Narsese" :to-string :name) "\n")]))
             :backcolor backcolor}
            {:name :paste :px 760 :py 0 :custom-w 40 :onclick (fn [state]
                                                      (input-str (slurp-clipboard)))
             :backcolor backcolor}

            {:name :> :px 0 :py 0 :onclick (fn [state]
                                                  (stop-timers)
                                                  (start-timers))
             :displaysize 10
             :backcolor backcolor}
            {:name :|| :px 50 :py 0 :custom-w 25 :onclick (fn [state]
                                                     (stop-timers))
             :backcolor backcolor}
            {:name :>| :custom-w 25 :px 75 :py 0 :onclick (fn [state]
                                                               (cast! (whereis :concept-selector) [:inference-tick-msg])
                                                               (cast! (whereis :task-creator) [:system-time-tick-msg])
                                                               (cast! (whereis :derived-load-reducer) [:system-time-tick-msg])
                                                               (cast! (whereis :forgettor) [:system-time-tick-msg]))
             :backcolor backcolor}
            {:name :reset :custom-w 35 :px 150 :py 0 :onclick (fn [state]
                                                              (shutdown)
                                                              (run))
             :backcolor backcolor}

            {:name :save :custom-w 35 :px 185 :py 0 :onclick (fn [state]
                                                               (cast! (whereis :concept-manager) [:persist-state-msg []]))
             :backcolor backcolor}

            {:name :load :custom-w 35 :px 220 :py 0 :onclick (fn [state]
                                                               (shutdown)
                                                               (run)
                                                               (cast! (whereis :concept-manager) [:load-state-msg []]))
             :backcolor backcolor}

            {:name :color :custom-w 30 :px 275 :py 0 :onclick (fn [state]
                                                       (reset! invert-colors (not @invert-colors)))
             :backcolor backcolor}
            {:name :speed :px 100 :py 0 :onclick (fn [state]
                                                   (swap-speed))
             :displaysize 10
             :backcolor backcolor}
            {:name :+prioTh. :px 305 :py 0 :onclick (fn [state]
                                                      (reset! prio-threshold (+ @prio-threshold 0.1)))
             :displaysize 10
             :backcolor backcolor}
            {:name :-prioTh. :px 355 :py 0 :onclick (fn [state]
                                                            (reset! prio-threshold (- @prio-threshold 0.1)))
             :backcolor backcolor}
            {:name :details :custom-w 40 :px 405 :py 0 :onclick (fn [state]
                                                            (reset! link-labels (not @link-labels)))
             :backcolor backcolor}

            {:name :send :px 660 :py 0 :onclick (fn [state]
                                                     (input-str (deref inputstr))
                                                     (swap! inputstr (fn [st] ""))) :backcolor backcolor}

            {:name :input :px 460 :custom-w 200 :py 0 :backcolor backcolor :displaysize 10}
            ])

(def hud [nodes [] hud-width hud-height])