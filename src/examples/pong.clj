(ns examples.pong
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [gui.hnav :as hnav]
            [gui.gui-utils :refer [invert-comp]]
            [narjure.global-atoms :refer :all]
            [narjure.core :as nar]
            [narjure.defaults :refer [max-term-complexity]]
            [narjure.sensorimotor :refer :all])
  (:gen-class))

(def py (atom 280))
(def direction (atom 0))
(def barheight 125)
(def fieldmax 760)
(def fieldmin 20)

(defn with-print [x]
  #_(println (str x))
  x)

(defn setup-pong
  "Registers the operations"
  []
  (reset! max-term-complexity 21)
  (nars-input-narsese "<ballpos --> [equal]>! :|:")
  (q/frame-rate 100)
  (nars-register-operation 'op_up (fn [args operationgoal]
                                    (do
                                      (when (= (:source operationgoal) :derived)
                                        #_(println "system decided up"))
                                      (reset! direction -1)
                                      (with-print (not= @py fieldmin)))))
  (nars-register-operation 'op_down (fn [args operationgoal]
                                      (do
                                        (when (= (:source operationgoal) :derived)
                                          #_(println "system decided down"))
                                        (reset! direction 1)
                                        (with-print (not= @py (- fieldmax barheight (- fieldmin)))))))

  (merge hnav/states {:ball-px 380
                      :ball-py 400
                      :direction-x 1
                      :direction-y 1
                      :iteration 0}))

"
<(&/,(&/,<ballpos --> [below]>,i4,<(*,{SELF}) --> op_down>),i4) =/> <ballpos --> [equal]>>.
<(&/,(&/,<ballpos --> [above]>,i4,<(*,{SELF}) --> op_up>),i4) =/> <ballpos --> [equal]>>.
"

(def allow-continuous-feedback true)
(def updown-state (atom "equal"))

(defn update-pong
  "World state transition"
  [state]

  (when (= @direction -1)
    (reset! py (+ @py -3)))
  (when (= @direction 1)
    (reset! py (+ @py 3)))
  (when (= (mod (:iteration state) 25) 0)
    (println (str "above truth " (vec (:truth (lense-max-statement-confidence-projected-to-now '[--> ballpos [int-set above]] :belief :event)))
                  " below truth " (vec (:truth (lense-max-statement-confidence-projected-to-now '[--> ballpos [int-set below]] :belief :event)))
                  " equal truth " (vec (:truth (lense-max-statement-confidence-projected-to-now '[--> ballpos [int-set equal]] :belief :event)))))
    (nars-input-narsese "<ballpos --> [equal]>! :|:"))
  (when (= (mod (:iteration state) 125) 1)
    (println "rand action")
    (nars-input-narsese (str (rand-nth ["<(*,{SELF}) --> op_up>! :|:"
                                        "<(*,{SELF}) --> op_down>! :|:"
                                        #_"<(*,{SELF}) --> op_stop>! :|:"]))))


  ;also give info from time to time
  #_(when (= (mod (:iteration state) 80) 0)                    ;1
    #_(nars-input-narsese (str "<{" (int (* 100 (quot (:ball-py state) 100))) "} --> ballpos>. :|:" ))
    #_(nars-input-narsese (str "<{" (int (* 100 (quot @py 100))) "} --> barpos>. :|:" ))
    (if (and (>= (:ball-py state) @py)
             (<= (:ball-py state) (+ @py barheight)))
      (when true
        (nars-input-narsese "<ballpos --> [equal]>. :|: %1.0;0.9%")
        (reset! updown-state "equal")
        #_(when allow-continuous-feedback
            ;(println "good NARS")
            (nars-input-narsese "<{SELF} --> [good]>. :|: %1.0;0.9%")))

      (if (> (:ball-py state) @py)
        (when true
          (nars-input-narsese (str "<ballpos --> [below]>. :|:"))
          (reset! updown-state "below")
          #_(when allow-continuous-feedback
              ;(println "bad NARS")
              (nars-input-narsese "<{SELF} --> [good]>. :|: %0.0;0.9%")))
        (when true
          (nars-input-narsese (str "<ballpos --> [above]>. :|:"))
          (reset! updown-state "above")
          #_(when allow-continuous-feedback
              ;(println "bad NARS")
              (nars-input-narsese "<{SELF} --> [good]>. :|: %0.0;0.9%"))))))

  (when (= (mod (:iteration state) 1) 0)                    ;1
    #_(nars-input-narsese (str "<{" (int (* 100 (quot (:ball-py state) 100))) "} --> ballpos>. :|:" ))
    #_(nars-input-narsese (str "<{" (int (* 100 (quot @py 100))) "} --> barpos>. :|:" ))
    (if (and (>= (:ball-py state) @py)
             (<= (:ball-py state) (+ @py barheight)))
      (when (not= @updown-state "equal")
        (nars-input-narsese "<ballpos --> [equal]>. :|: %1.0;0.9%")
        #_(nars-input-narsese "<ballpos --> [above]>. :|: %0%")
        #_(nars-input-narsese "<ballpos --> [below]>. :|: %0%")
        (reset! updown-state "equal")
        #_(when allow-continuous-feedback
            ;(println "good NARS")
            (nars-input-narsese "<{SELF} --> [good]>. :|: %1.0;0.9%")))

      (if (> (:ball-py state) @py)
        (when (not= @updown-state "below")
          (nars-input-narsese "<ballpos --> [below]>. :|:")
          #_(nars-input-narsese "<ballpos --> [above]>. :|: %0%")
          #_(nars-input-narsese "<ballpos --> [equal]>. :|: %0%")
          (reset! updown-state "below")
          #_(when allow-continuous-feedback
              ;(println "bad NARS")
              (nars-input-narsese "<{SELF} --> [good]>. :|: %0.0;0.9%")))
        (when (not= @updown-state "above")
          (nars-input-narsese "<ballpos --> [above]>. :|:")
          #_(nars-input-narsese "<ballpos --> [below]>. :|: %0%")
          #_(nars-input-narsese "<ballpos --> [equal]>. :|: %0%")
          (reset! updown-state "above")
          #_(when allow-continuous-feedback
              ;(println "bad NARS")
              (nars-input-narsese "<{SELF} --> [good]>. :|: %0.0;0.9%"))))))

  (let [kset-x (+ 0.6 (/ (Math/random) 2.0))
        kset-y (+ 0.6 (/ (Math/random) 2.0))
        state2 (assoc state
                 :ball-px #_(:ball-px state) (+ (:ball-px state) (* (:direction-x state) 1 1))
                 :ball-py #_(:ball-py state) (+ (:ball-py state) (* (:direction-y state) 1 1)))

        state3 (if (>= (:ball-px state2)                     ;collided on right wall
                       fieldmax)
                 (assoc state2 :direction-x (- kset-x))
                 state2)

        state4 (if (<= (:ball-px state3)                     ;collided on left wall!!
                       fieldmin)
                 (do
                   #_(nars-input-narsese "<{SELF} --> [good]>. :|: %0.0;0.9%")
                   #_(nars-input-narsese "<{SELF} --> [good]>! :|:")
                   ;(println "bad NARS")
                   (assoc state3 :direction-x kset-x))
                 state3)

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
      :ball-px (max fieldmin (min (:ball-px state7) fieldmax))
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
  (q/rect fieldmin fieldmin fieldmax fieldmax)
  (q/fill 128)
  (q/rect 25 @py 10 barheight)
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