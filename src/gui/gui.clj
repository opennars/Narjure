(ns gui.gui
  (:require [seesaw.core :refer :all]
            [gui.globals :refer :all]
            [co.paralleluniverse.pulsar.actors :refer [whereis cast!]]
            [clojure.string :as str]
            [gui.gui-utils :refer :all]
            [narjure.core :refer [shutdown run]]))

(def backcolor [230 230 230])
(def gui-width 50)
(def gui-height 25)

(def nodes [{:name :send :px 900 :py -325 :onclick (fn [state]
                                                     (input-str (deref inputstr))
                                                     (swap! inputstr (fn [st] ""))) :backcolor backcolor}
            {:name :paste :px 750 :py -325 :onclick (fn [state]
                                                     (input-str (slurp-clipboard)))
             :backcolor backcolor}
            {:name :clear :px 800 :py -325 :onclick (fn [state]
                                                     (swap! inputstr (fn [st] ""))) :backcolor backcolor}
            {:name :pop-up :px 850 :py -325 :onclick (fn [state]
                                                      (cast! (whereis :sentence-parser) [:narsese-string-msg (str (input "Add Narsese" :to-string :name) "\n")]))
             :backcolor backcolor}


            #_{:name :resume :px 450 :py -525 :onclick (fn [state]
                                                      (start-timers))
             :backcolor backcolor}
            #_{:name :pause :px 500 :py -525 :onclick (fn [state]
                                                       (stop-timers))
             :backcolor backcolor}
            #_{:name :off :px 650 :py -525 :onclick (fn [state]
                                                       (shutdown))
             :backcolor backcolor}
            #_{:name :start :px 700 :py -525 :onclick (fn [state]
                                                       (run))
             :backcolor backcolor}

            #_{:name :color :px 1000 :py -525 :onclick (fn [state]
                                                      (reset! invert-colors (not @invert-colors)))
             :backcolor backcolor}
            #_{:name :+prioTh. :px 1100 :py -525 :onclick (fn [state]
                                                       (reset! prio-threshold (+ @prio-threshold 0.1)))
             :backcolor backcolor}
            #_{:name :-prioTh. :px 1200 :py -525 :onclick (fn [state]
                                                            (reset! prio-threshold (- @prio-threshold 0.1)))
             :backcolor backcolor}
            #_{:name :edgeInf. :px 1300 :py -525 :onclick (fn [state]
                                                       (reset! link-labels (not @link-labels)))
             :backcolor backcolor}])

(def graph-gui [nodes [] gui-width gui-height])