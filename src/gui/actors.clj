(ns gui.actors
  (:require [seesaw.core :refer :all]))

(def actor-level-width 175)
(def actor-level-height 100)

(def concept-color [200 255 255])
(def derived-task-color [200 200 255])
(def task-color [255 255 200])
(def util-color [200 255 200])
(def gui-color [240 240 255])

(def nodes [{:name :input :px 400 :py -400 :displaysize 10.0 :backcolor gui-color}
            {:name :sentence-parser :px 400 :py -300 :backcolor util-color}
            {:name :task-creator :px 400 :py -150 :backcolor task-color}
            {:name :concept-manager :px 600 :py 150 :backcolor task-color}
            {:name :task-buffer :px 400 :py 0 :backcolor derived-task-color}
            {:name :concepts :px -100 :py 150 :backcolor concept-color}
            {:name :general-inferencer :px 900 :py 300 :backcolor derived-task-color}
            {:name :inference-request-router :px 900 :py 450 :backcolor derived-task-color}
            {:name :inference-request-buffer :px 900 :py 600 :backcolor derived-task-color}
            {:name :operator-executor :px -100 :py -150 :backcolor [255 200 200]}
            {:name :task-dispatcher :px 400 :py 150 :backcolor task-color}])

(def edges [{:name ":sentence-msg" :from :sentence-parser :to :task-creator :unidirectional true}
            {:name ":task-msg" :from :task-buffer :to :task-dispatcher :unidirectional true}
            {:name ":task-msg" :from :task-creator :to :task-buffer :unidirectional true}
            {:name ":create-concept-msg" :from :task-dispatcher :to :concept-manager :unidirectional true}
            {:name ":task-msg" :from :concept-manager :to :task-dispatcher :unidirectional true}
            {:name ":task-msg" :from :task-dispatcher :to :concepts :unidirectional true}
            {:name ":do-inference-msg" :from :concepts :to :inference-request-buffer :unidirectional true}
            {:name ":do-inference-msg" :from :inference-request-buffer :to :inference-request-router :unidirectional true}
            {:name ":do-inference-msg" :from :inference-request-router :to :general-inferencer :unidirectional true}
            {:name ":derived-sentence-msg" :from :general-inferencer :to :task-creator :unidirectional true}
            {:name ":operator-execution-msg" :from :concepts :to :operator-executor :unidirectional true}
            {:name ":link-feedback-msg" :from :general-inferencer :to :concepts :unidirectional true}
            {:name ":task-msg" :from :concepts :to :task-buffer :unidirectional true}])

(def graph-actors [nodes edges actor-level-width actor-level-height])