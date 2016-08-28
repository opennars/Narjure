(ns gui.actors
  (:require [seesaw.core :refer :all]))

(def actor-level-width 175)
(def actor-level-height 100)

(def concept-color [200 255 255])
(def derived-task-color [200 200 255])
(def task-color [255 255 200])
(def util-color [200 255 200])
(def gui-color [240 240 255])

(def nodes [{:name :sentence-parser :px 400 :py -300 :backcolor util-color}])

(def edges [])

(def graph-actors [nodes edges actor-level-width actor-level-height])