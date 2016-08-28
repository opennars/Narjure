(ns gui.lense
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [gui.gui-utils :refer :all]
            [gui.actors :refer :all]
            [gui.gui :refer [graph-gui]]
            [nal.deriver.projection-eternalization :refer [project-eternalize-to]]
            [gui.hud :refer [hud]]
            [gui.hnav :as hnav]
            [seesaw.core :refer :all]
            [gui.globals :refer :all]
            [narjure.global-atoms :refer :all]
            [narjure.core :as nar]
            [co.paralleluniverse.pulsar.actors :refer [whereis cast!]]
            [narjure.control.concept-manager :as concept-manager]
            [narjure.control.inference-request-buffer :as inference-request-buffer]
            [narjure.control.task-buffer :as task-buffer]
            [narjure.control.task-dispatcher :as task-dispatcher]
            [narjure.perception-action.task-creator :as task-creator]
            [narjure.perception-action.sentence-parser :as sentence-parser]
            [narjure.general-inference.inference-request-router :as inference-request-router]
            [narjure.memory-management.concept :as concepts]
            [narjure.general-inference.general-inferencer :as general-inferencer]
            [narjure.perception-action.operator-executor :as operator-executor]
            [narjure.debug-util :refer :all]
            [narjure.defaults :refer [priority-threshold max-concept-selections max-tasks]]
            [clojure.set :as set]
            [clojure.string :as str])
  (:gen-class))

(def debugmessage {:inference-request-router [(fn [] (deref inference-request-router/display)) inference-request-router/search]
                   :inference-request-buffer [(fn [] (deref inference-request-buffer/display)) inference-request-buffer/search]
                   :general-inferencer       [(fn [] (deref general-inferencer/display)) general-inferencer/search]
                   :concept-manager          [(fn [] (deref concept-manager/display)) concept-manager/search]
                   :task-buffer              [(fn [] (deref task-buffer/display)) task-buffer/search]
                   :task-dispatcher          [(fn [] (deref task-dispatcher/display)) task-dispatcher/search]
                   :operator-executor        [(fn [] (deref operator-executor/display)) operator-executor/search]
                   :sentence-parser          [(fn [] (deref sentence-parser/display)) sentence-parser/search]
                   :task-creator             [(fn [] (deref task-creator/display)) task-creator/search]
                   :concepts                 [(fn [] (deref concepts/display)) concepts/search]
                   :input                    [(fn [] "") inputstr]
                   :output                   [(fn [] (deref output-display)) output-search]
                   :+prioTh.                 [(fn [] (deref prio-threshold))]
                   :speed                    [(fn [] (deref speed))]
                   :>                   [(fn [] (deref nars-time))]})

(def static-graphs [graph-actors graph-gui])
(def graphs (atom static-graphs))

(defn setup []
  (q/frame-rate 30)
  ;(nar/run)
  (merge hnav/states {}))

(defn update [state] state)

(defn nameof [a]
  (if (string? a) a (name a)))

(defn draw-actor
  "Draw an actor block"
  [{:keys [name px py backcolor frontcolor displaysize titlesize stroke-weight custom-w custom-h]} node-width node-height]
  (q/stroke-weight (if (= nil stroke-weight) 1.0 stroke-weight))
  (apply q/fill (invert-color (if (= backcolor nil) [255 255 255] backcolor)))
  (q/rect px py (if custom-w custom-w node-width) (if custom-h custom-h node-height))
  (apply q/fill (invert-color (if (= frontcolor nil) [0 0 0] frontcolor)))
  (q/text-size (if (= nil titlesize) 10.0 titlesize))
  (q/text (nameof name) (+ px 5) (+ py (if (= nil titlesize) 10.0 titlesize)))
  (q/text-size (if (= displaysize nil) 2.0 displaysize))
  (when (contains? debugmessage name)
    (q/text (hnav/display-string debugmessage name)
            (+ px 5) (+ py 20)))
  (q/text-size 2.0))

(defn in-picture
  "Checks whether the point p is inside of the double-sized screen."
  [state p hud]
  (if hud
    true
    (if (and (> (:px p) (hnav/mouse-to-world-coord-x state (- (hnav/width))))
             (< (:px p) (hnav/mouse-to-world-coord-x state (+ (hnav/width) (hnav/width))))
             (> (:py p) (hnav/mouse-to-world-coord-y state (- (hnav/height))))
             (< (:py p) (hnav/mouse-to-world-coord-y state (+ (hnav/height) (hnav/height)))))
      true
      false)))

(defn in-picture-aggressive
  "Checks whether the point p is inside of the screen."
  [state p hud]
  (if hud
    true
    (if (and (> (:px p) (hnav/mouse-to-world-coord-x state 0.0))
             (< (:px p) (hnav/mouse-to-world-coord-x state (hnav/width)))
             (> (:py p) (hnav/mouse-to-world-coord-y state 0.0))
             (< (:py p) (hnav/mouse-to-world-coord-y state (hnav/height))))
      true
      false)))

(defn draw-graph
  "Draw a graph, its nodes and edges and their contents"
  [state [nodes edges node-width node-height] hud]
  (let [prefer-id (fn [n] (if (= nil (:id n))
                            (:name n)
                            (:id n)))]
    (doseq [c edges]
      (when (and (some #(= (:from c) (prefer-id %)) nodes)
                 (some #(= (:to c) (prefer-id %)) nodes))
        (let [pxtransform (fn [x] (+ (:px x) (/ node-width 2.0)))
              pytransform (fn [y] (+ (:py y) (/ node-height 2.0)))
              left (first (filter #(= (:from c) (prefer-id %)) nodes))
              right (first (filter #(= (:to c) (prefer-id %)) nodes))
              middle {:px (/ (+ (:px left) (:px right)) 2.0)
                      :py (/ (+ (:py left) (:py right)) 2.0)}
              namepos {:px (/ (+ (* 0.6 (:px left)) (* 0.4 (:px right))) 1.0)
                       :py (+ 3 (/ (+ (* 0.6 (:py left)) (* 0.4 (:py right))) 1.0))}
              target (if (not= true (:unidirectional c))
                       right middle)
              weight (if (not= nil (:stroke-weight c))
                       (:stroke-weight c)
                       0.5)
              left-x (pxtransform left) left-y (pytransform left)
              right-x (pxtransform right) right-y (pytransform right)
              target-x (pxtransform target) target-y (pytransform target)
              middle-x (pxtransform middle) middle-y (pytransform middle)
              namepos-x (pxtransform namepos) namepos-y (pytransform namepos)
              pointf (fn [a b] {:px a :py b})
              name (:name c)]
          (when (or (:no-in-picture-check c)
                    (in-picture state (pointf left-x left-y) hud)
                    name
                    (and @link-labels
                         (in-picture state (pointf namepos-x namepos-y) hud)))

            (let [eval-color (if (= nil (:link-color c) )
                               (invert-color [0 0 0])
                               (invert-color (:link-color c)))
                  col eval-color
                  r (first col)
                  g (second col)
                  b (nth col 2)]

              (q/stroke r g b)
              (q/stroke-weight (* weight 2.0))
              (q/line left-x left-y
                      middle-x target-y)
              (when (and (:unidirectional c)
                         (not (:opposite-edge-exists c)))
                (if (:ghost-opposite c)
                  (do
                    (q/stroke (invert-comp 210.0))
                    (q/stroke-weight 0.15))
                  (do
                    (q/stroke-weight weight)))
                (q/line right-x right-y
                        middle-x middle-y))
              (when (not= nil name)
                (q/text (str name) namepos-x namepos-y))))))))
  (q/stroke (first (invert-color [0 0 0])))
  (doseq [a nodes]
    (when (in-picture state (assoc a :px (+ (:px a) (/ node-width 2.0))
                                     :py (+ (:py a) (/ node-height 2.0))) hud)
      (draw-actor a node-width node-height))))

(def selected-concept (atom []))

;copy of draw below marked as draw2
(defn draw [state]
  (q/background (first (invert-color [255 255 255])))
  (q/reset-matrix)
  (q/push-matrix)
  (hnav/transform state)
  (doseq [g @graphs]
    (draw-graph state g false))
  ;(reset! graphs static-graphs)
  ;concept graph
  #_(when (> (hnav/mouse-to-world-coord-x state (hnav/width)) 1600)
    (try (let [elems (apply vector (:priority-index (deref c-bag)))
               a 75.0
               pxfun (fn [ratio] (+ 3500 (* a ratio (Math/cos ratio))))
               pyfun (fn [ratio] (+ 200 (* a ratio (Math/sin ratio))))
               isin
               (apply set/union
                      (for [i (range (count elems))]
                        (let [elem (elems i)
                              ratio (* 30.0 (+ 0.10 (/ i (count elems))))
                              px (pxfun ratio)
                              py (pyfun ratio)]
                          (when ((if @link-labels in-picture in-picture-aggressive) state {:px px :py py} false)
                            (set/union (set (keys (@lense-termlinks (:id elem))))
                                       #{(:id elem)})))))
               nodes (for [i (range (count elems))]
                       (let [elem (elems i)
                             ratio (* 30.0 (+ 0.10 (/ i (count elems))))
                             id (:id elem)
                             px (pxfun ratio)
                             py (pyfun ratio)
                             priority (:priority elem)
                             show-concept ((:elements-map (deref c-bag)) id)
                             quality (:quality show-concept)
                             observable (:observable show-concept)]
                         (when (try
                                 (and
                                   (every? (fn [x] (.contains (narsese-print id) x))
                                           (str/split (deref concept-filter) #"\n"))
                                      (> priority priority-threshold)
                                      (> priority @prio-threshold)
                                      (or (isin id)
                                          ((if @link-labels in-picture in-picture-aggressive) state {:px px :py py} false)))
                                 (catch Exception e false))
                           {:name          (str "\n" (narsese-print id)
                                                (if (= id @selected-concept)
                                                  (let [truth  (:truth (lense-max-statement-confidence-projected-to-now id :belief nil))
                                                        desire (:truth (lense-max-statement-confidence-projected-to-now id :goal nil))]
                                                    (str "\nfilter: " @task-filter "priority: " priority " " "quality: " quality " "
                                                        (when truth "truth: " truth " ")
                                                        (when desire "desire: " desire " ")
                                                         (when observable "observable")
                                                         "\n"
                                                        (bag-format
                                                          (limit-string (narsese-print (apply vector
                                                                                              (if @link-labels
                                                                                                (bagfilter task-filter (:elements-map (@lense-taskbags id)))
                                                                                                (let [curbag (@lense-taskbags id)
                                                                                                      pindex (:priority-index curbag)]
                                                                                                  (map (fn [has-id]
                                                                                                         (let [task (:task (first (b/get-by-id curbag (:id has-id))))]
                                                                                                           (assoc has-id :truth (:truth task)
                                                                                                                         :occurrence (if (= (:occurrence task) :eternal)
                                                                                                                                       :eternal
                                                                                                                                       (- (:occurrence task) @nars-time))
                                                                                                                         :quality (nth (:budget task) 2)
                                                                                                                         :solution (when (:solution task)
                                                                                                                                     {:statement  (:statement (:solution task))
                                                                                                                                      :truth      (:truth (:solution task))
                                                                                                                                      :occurrence (:occurrence (:solution task))}))))
                                                                                                       (bagfilter task-filter (:priority-index (@lense-taskbags id)))))))) 20000))))
                                                  ""))       ;"\n" @lense-termlinks
                            :px            px
                            :py            py
                            :displaysize   1.0
                            :backcolor     [(- 255 (* priority 255.0)) 255 255]
                            :titlesize     2.0
                            :stroke-weight 0.5
                            :id            id
                            :onclick       (fn [state]
                                             (reset! input-string task-filter)
                                             (reset! selected-concept id))})))
               edges (for [n nodes
                           [k [freq conf]] (@lense-termlinks (:id n))]
                       (let [red-blue-border 0.5
                             disttomiddle (Math/abs (- 0.5 freq))
                             rterm (/ (if (>= freq red-blue-border) (* 510.0 disttomiddle) 0.0) 2.0)
                             bterm (/ (if (< freq red-blue-border) (* 510.0 disttomiddle) 0.0) 2.0)]
                         {:from                 (:id n)
                          :to                   k :unidirectional true
                          :no-in-picture-check true
                          :stroke-weight        (* 0.5 conf)
                          :link-color           (if @invert-colors
                                                  (invert-color [(+ 128.0 rterm) 128.0 (+ 128.0 bterm)])
                                                  [0.0 (+ 0 rterm) (+ 0 bterm)])
                          :name                 (when @link-labels [freq conf])
                          :opposite-edge-exists (some (fn [[tl2 _]]
                                                        (= tl2 (:id n)))
                                                      (@lense-termlinks k))
                          :ghost-opposite       true}))
               concept-graph [(filter #(not= % nil) nodes) edges 10 10]]
           (reset! graphs (concat static-graphs [concept-graph])))
         (catch Exception e (println "test"))))
  (q/pop-matrix)
  (try
    (draw-graph state hud true)
    (catch Exception e ()))
  (draw-graph state hud true)
  )

(defn key-pressed [state event]
  (let [name (name (:key event))
        code (:key-code event)]
    (swap! (deref input-string) (fn [inputstr] (str (if (not (= code 8))
                                                      inputstr (subs inputstr 0 (max 0 (dec (count inputstr)))))
                                                    (if (not (> (count name) 1))
                                                      (if (not (= code 8))
                                                        name "") ""))))
    state))

(defn lense-mousepress [state event]
  (hnav/mouse-pressed graphs debugmessage false state event)
  (hnav/mouse-pressed (atom [hud]) debugmessage true state event))

(defn -main []
  (q/defsketch example
              :size [(hnav/width) (hnav/height)]
              :setup setup
              :draw draw
              :update update
              :mouse-pressed lense-mousepress
              :mouse-dragged hnav/mouse-dragged
              :mouse-wheel hnav/mouse-wheel
              :key-pressed key-pressed
              :middleware [m/fun-mode]
              :features [:resizable]
              :title "OpenNARS 2.0.0: Lense"))