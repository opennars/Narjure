(ns examples.simplenario
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [gui.hnav :as hnav]
            [gui.gui-utils :refer [invert-comp]]
            [narjure.global-atoms :refer :all]
            [narjure.core :as nar]
            [narjure.sensorimotor :refer :all])
  (:gen-class))

(def px (atom 10))
(def py (atom 280))
(def direction (atom 0))
(def direction-x (atom 0))
(def barheight 125)
(def fieldmax 760)
(def fieldmax-x 76000)
(def fieldmin 20)

(defn with-print [x]
  #_(println (str x))
  x)

;e <-> <ballpos --> [equal]>
;u <-> ballpos --> [above]

(defn setup-pong
  "Registers the operations"
  []
  (nars-input-narsese "right! :|:")
  (q/frame-rate 80)
  (nars-register-operation 'self_op_up (fn [args operationgoal]
                                    (do
                                      (when (= @py (- fieldmax barheight (- fieldmin)))
                                        (reset! direction -1))
                                      (with-print (= @py (- fieldmax barheight (- fieldmin)))))))
  (nars-register-operation 'self_op_right (fn [args operationgoal]
                                      (do
                                        (when (= (:source operationgoal) :derived)
                                          #_(println "system decided down"))
                                        (reset! direction-x 1)
                                        true)))
  (nars-register-operation 'self_op_left (fn [args operationgoal]
                                            (do
                                              (when (= (:source operationgoal) :derived)
                                                #_(println "system decided down"))
                                              (reset! direction-x -1)
                                              true)))

  (merge hnav/states {:ball-px 80
                      :ball-py 750
                      :direction-x 1
                      :direction-y 1
                      :iteration 0}))

"
<(&/,(&/,equal,i4,self_op_up),i4) =/> right>.
<(&/,(&/,down,i4,self_op_right),i4) =/> right>.
"

(def allow-continuous-feedback true)
(def updown-state (atom "equal"))

(def ballpx-tmp (atom 10))
(def ballpy-tmp (atom 10))
(def pxmax (atom 100000))
(def collide (atom 0))
(defn update-pong
  "World state transition"
  [state]
  (reset! collide 0)
  (reset! pxmax 100000)
  (when (and (< (Math/abs (- @px (:ball-px state))) 10)
             (< (Math/abs (- (+ @py (/ barheight 2.0)) (:ball-py state))) (/ barheight 2.0)))
    #_(reset! direction-x -1)
    (reset! collide 1)
    (reset! pxmax @px))

  (reset! px (min (+ @direction-x @px)
                  @pxmax))
  (reset! direction-x (* 0.98 @direction-x))
  (reset! direction (+ 0.01 @direction))
  (reset! ballpx-tmp (:ball-px state))
  (reset! ballpy-tmp (:ball-py state))
  (reset! py (+ @py (* 3.0 @direction)))



  (when (> @px (+ 50.0 @ballpx-tmp))
    (reset! ballpx-tmp (+ @px (+ 100.0 (* 30.0 (Math/random)))))
    (reset! ballpy-tmp (- fieldmax (* 200.0 (Math/random)))))

  (when (= (mod (:iteration state) 25) 0)
    #_(println (str "above truth " (vec (:truth (lense-max-statement-confidence-projected-to-now 'above :belief :event)))
                  " below truth " (vec (:truth (lense-max-statement-confidence-projected-to-now 'below :belief :event)))
                  " equal truth " (vec (:truth (lense-max-statement-confidence-projected-to-now 'equal :belief :event)))))
    (nars-input-narsese "right! :|:"))
  (when (= (mod (:iteration state) 200) 1)
    (println "rand action")
    (nars-input-narsese (str (rand-nth ["self_op_up! :|:"
                                        "self_op_left! :|:"
                                        "self_op_right! :|:"
                                        #_"<(*,{SELF}) --> op_stop>! :|:"]))))


  (when (and (= (mod (:iteration state) 60) 0)
             (= @collide 1))
    (nars-input-narsese "collide. :|: %1.0;0.9%"))
    ;also give info from time to time
  (when (and (not= @collide 1)
             (= (mod (:iteration state) 60) 0))                     ;1
    #_(nars-input-narsese (str "<{" (int (* 100 (quot (:ball-py state) 100))) "} --> ballpos>. :|:" ))
    #_(nars-input-narsese (str "<{" (int (* 100 (quot @py 100))) "} --> barpos>. :|:" ))
    (when (> @direction-x 0.1)
      (nars-input-narsese "right. :|: %1.0;0.9%"))

    (if (and (>= (:ball-py state) @py)
             (<= (:ball-py state) (+ @py barheight)))
      (when true
        (nars-input-narsese "nocoll. :|: %1.0;0.9%")
        (reset! updown-state "equal")
        #_(when allow-continuous-feedback
            ;(println "good NARS")
            (nars-input-narsese "<{SELF} --> [good]>. :|: %1.0;0.9%")))

      (if (> (:ball-py state) @py)
        (when true
          (nars-input-narsese (str "nocoll. :|:"))
          (reset! updown-state "below")
          #_(when allow-continuous-feedback
              ;(println "bad NARS")
              (nars-input-narsese "<{SELF} --> [good]>. :|: %0.0;0.9%")))
        (when true
          (nars-input-narsese (str "nocoll. :|:"))
          (reset! updown-state "above")
          #_(when allow-continuous-feedback
              ;(println "bad NARS")
              (nars-input-narsese "<{SELF} --> [good]>. :|: %0.0;0.9%"))))))

  (when (and (= (mod (:iteration state) 1) 0)
             (not= @collide 1))                    ;1
    #_(nars-input-narsese (str "<{" (int (* 100 (quot (:ball-py state) 100))) "} --> ballpos>. :|:" ))
    #_(nars-input-narsese (str "<{" (int (* 100 (quot @py 100))) "} --> barpos>. :|:" ))
    (if (and (>= (:ball-py state) @py)
             (<= (:ball-py state) (+ @py barheight)))
      (when (not= @updown-state "equal")
        (nars-input-narsese "nocoll. :|: %1.0;0.9%")
        #_(nars-input-narsese "above. :|: %0%")
        #_(nars-input-narsese "below. :|: %0%")
        (reset! updown-state "equal")
        #_(when allow-continuous-feedback
            ;(println "good NARS")
            (nars-input-narsese "<{SELF} --> [good]>. :|: %1.0;0.9%")))

      (if (> (:ball-py state) @py)
        (when (not= @updown-state "below")
          (nars-input-narsese "nocoll. :|:")
          #_(nars-input-narsese "above. :|: %0%")
          #_ (nars-input-narsese "equal. :|: %0%")
          (reset! updown-state "below")
          #_(when allow-continuous-feedback
              ;(println "bad NARS")
              (nars-input-narsese "<{SELF} --> [good]>. :|: %0.0;0.9%")))
        (when (not= @updown-state "above")
          (nars-input-narsese "nocoll. :|:")
          #_(nars-input-narsese "below. :|: %0%")
          #_(nars-input-narsese "equal. :|: %0%")
          (reset! updown-state "above")
          #_(when allow-continuous-feedback
              ;(println "bad NARS")
              (nars-input-narsese "<{SELF} --> [good]>. :|: %0.0;0.9%"))))))

  (let [kset-x (+ 0.6 (/ (Math/random) 2.0))
        kset-y (+ 0.6 (/ (Math/random) 2.0))
        state2 (assoc state
                 :ball-px @ballpx-tmp
                 :ball-py @ballpy-tmp)

        state3 state2
        state4 state3
        state5 (if (>= (:ball-py state4)                     ;collided on upper wall
                       fieldmax)
                 (assoc state4 :direction-y (- kset-y))
                 state4)

        state6 (if (<= (:ball-py state5)                     ;collided on down wall
                       fieldmin)
                 (assoc state5 :direction-y kset-y)
                 state5)

        state7 (if (and (<= (:ball-px state6) 40)            ;got it
                        (>= (:ball-py state6) @py)
                        (<= (:ball-py state6) (+ @py barheight)))
                 (do
                   #_(nars-input-narsese "<{SELF} --> [good]>. :|: %1.0;0.9%")
                   #_(nars-input-narsese "<{SELF} --> [good]>! :|:")
                   ;(println "good NARS")
                   (assoc state6 :direction-x kset-x))
                 state6)]

    (when (> @py (- fieldmax barheight (- fieldmin)))
      (reset! py (- fieldmax barheight (- fieldmin))))
    (when (< @py fieldmin)
      (reset! py fieldmin))

    (assoc state7
      :ball-px (max fieldmin (min (:ball-px state7) fieldmax-x))
      :ball-py (max fieldmin (min (:ball-py state7) fieldmax))
      :iteration (inc (:iteration state7)))))


(defn draw-pong
  "Draw the game"
  [state]
  (q/background (invert-comp 255))
  (q/stroke (invert-comp 0))
  (q/reset-matrix)
  (hnav/transform state)
  (q/fill (invert-comp 255))
  (q/rect -1000 fieldmin fieldmax-x fieldmax)
  (q/fill 128)
  (q/rect @px @py 10 barheight)
  (q/rect (:ball-px state) (:ball-py state) 10 10))

(defn -main []
  (q/defsketch pong
              :size [(hnav/width) (hnav/height)]
              :setup setup-pong
              :draw draw-pong
              :update update-pong
              :mouse-pressed (partial hnav/mouse-pressed [] {} false)
              :mouse-dragged hnav/mouse-dragged
              :mouse-wheel hnav/mouse-wheel
              :middleware [m/fun-mode]
              :features [:resizable]
              :title "OpenNARS 2.0.0: Pong"))