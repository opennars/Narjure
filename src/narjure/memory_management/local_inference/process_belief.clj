(ns narjure.memory-management.local-inference.process-belief
  (:require
    [nal.deriver.truth :refer :all]
    [narjure
     [global-atoms :refer :all]
     [control-utils :refer :all]
     [defaults :refer :all]
     ]))

(defn event?
  "tests whether the passed task sentence is an event"
  [sentence]
  (not= :eternal (:occurrence sentence)))

;temporally project task to ref task (note: this is only for event tasks!!)"
(defn project-to
  "projects the task t to the target-time given the current time cur-time."
  [target-time t cur-time]
  (when (= :eternal (:occurrence t))
    (println "ERROR: Project called on eternal task!!"))
  (let [source-time (:occurrence t)
        dist (fn [a b] (Math/abs (- a b)))
        a 10.0]
    (if (= target-time source-time)
      t
      (assoc t
        :truth [(frequency t)
                (* (confidence t)
                   (- 1 (/ (dist source-time target-time)
                           (+ (dist source-time cur-time)
                              (dist target-time cur-time)
                              a))))]
        :occurrence target-time))))

(defn revise
  "Revision of two tasks."
  [t1 t2]
  (let [revised-truth (nal.deriver.truth/revision (:truth t1) (:truth t2))
        evidence (make-evidence (:evidence t1) (:evidence t2))]
    (assoc t1 :truth revised-truth :source :derived :evidence evidence)))

(defn choice [b1 b2]
  (let [[f1 c1] (:truth b1)
        [f2 c2] (:truth b2)]

    (cond
      (> c1 c2) b1
      (< c1 c2) b2
      (< (expectation [f1 c1]) (expectation [f2 c2])) b2
      :else b1)))

(defn revision-relevant-tasks
  "Whether the new task should do revision with the old one,
  see https://groups.google.com/forum/#!topic/open-nars/4uMf_kI3Etk"
  [task old-event]
  (or (= (:occurrence task) :eternal)
      (< (Math/abs (- (:occurrence task) (:occurrence old-event)))
         revision-relevant-event-distance)))

(defn revise-or-choose-best-eternal [belief host-belief]
  (if (non-overlapping-evidence? (:evidence belief) (:evidence host-belief))
    (revise belief host-belief)
    (choice belief host-belief)))

(defn revise-or-choose-best-event [belief host-event]
  (if (and (revision-relevant-tasks belief host-event)
           (non-overlapping-evidence? (:evidence belief) (:evidence host-event)))
    (revise belief (project-to (:occurrence belief) host-event @nars-time))
    (let [b1 (project-to (:occurrence belief) belief @nars-time)
          b2 (project-to (:occurrence belief) host-event @nars-time)]
      (choice b1 b2))))

(defn process-eternal-belief [belief state]
  ; process eternal belief task
  ; if existing eternal belief (:host-belief) then try revision
  ; else save eternal belief as host-belief
  (if-let [host-belief (:host-belief @state)]
    (revise-or-choose-best-eternal belief host-belief)
    belief))

(defn process-event-belief [belief state]
  (if-let [host-event (:host-event @state)]
    (revise-or-choose-best-event belief host-event)
    belief))

(defn process-belief [state belief]
  (if (event? belief)
    (assoc @state :host-event (process-event-belief belief state))
    (assoc @state :host-belief (process-eternal-belief belief state))))