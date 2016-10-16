(ns narjure.memory-management.termlink-utils
  (:require
    [co.paralleluniverse.pulsar
     [core :refer :all]
     [actors :refer :all]]
    [taoensso.timbre :refer [debug info]]
    [narjure
     [global-atoms :refer :all]
     [bag :as b]
     [debug-util :refer :all]
     [control-utils :refer :all]
     [defaults :refer :all]
     [budget-functions :refer :all]]
    [narjure.memory-management
     [concept-utils :refer :all]]
    [clojure.core.unify :refer [unifier]]
    [nal.term_utils :refer [syntactic-complexity termlink-subterms]]
    [narjure.memory-management.local-inference
     [local-inference-utils :refer [get-task-id get-tasks update-task-in-tasks]]
     [belief-processor :refer [process-belief]]
     [goal-processor :refer [process-goal]]
     [quest-processor :refer [process-quest]]
     [question-processor :refer [process-question]]]
    [nal.deriver
     [truth :refer [w2c t-or t-and confidence frequency expectation revision]]
     [projection-eternalization :refer [project-eternalize-to]]]
    [clojure.set :as set])
  (:refer-clojure :exclude [promise await]))

(defn get-linkable-terms
  "disallow linking to itself"
  [task]
  (filter #(not= % (:id @state)) (termlink-subterms (:statement task))))

(defn get-existing-terms-from-links
  "only use links whose target concept still exists"
  [links]
  (filter #(b/exists? @c-bag %) (keys links)))

(defn forget-termlinks-absolute
  "This one is for absolute forgetting of links: Remove termlinks whose concepts were forgot,
  remove worst termlinks if above max termlink count."
  []
  (doseq [[tl _] (:termlinks @state)]
    (when (not (b/exists? @c-bag tl))
      (set-state! (assoc @state :termlinks (dissoc (:termlinks @state) tl)))))
  (while (> (count (:termlinks @state)) concept-max-termlinks)
    (let [worst (apply min-key (comp expectation second) (:termlinks @state))]
      (set-state! (assoc @state :termlinks (dissoc (:termlinks @state) (first worst)))))))

(defn forget-termlinks-relative []
  "Forget termlinks relative."
  (set-state! (assoc @state :termlinks (apply merge (for [[tl [f c]] (:termlinks @state)]
                                                      {tl (revision [f c] [0.0 0.001])})))))

(defn add-termlink
  "Adds a termlink with term tl and strength strength."
  [tl strength]
  (set-state! (assoc @state :termlinks (assoc (:termlinks @state)
                                         tl strength)))
  ;(forget-termlinks)
  )

(defn calc-link-strength
  ([tl]
    (calc-link-strength tl [0.5 0.01]))
  ([tl old-strength]
  old-strength
    #_(let [prio-me (concept-priority (:id @state))
        prio-other (concept-priority tl)
        evidence-mul 0.5
        association (t-and prio-me prio-other)
        disassocation (t-and prio-me (- 1.0 prio-other))]
    (revision [0.0 (* evidence-mul disassocation)]
              (revision old-strength [1.0 (* evidence-mul association)])))))

(defn update-termlink [tl]                                  ;term
  (let [old-strength ((:termlinks @state) tl)]
    (add-termlink tl (calc-link-strength tl (if old-strength old-strength [0.5 0.0])))
    (forget-termlinks-absolute)))

(defn use-stronger [t1 t2]
  (let [all-keys (set/union (map first t1) (map first t2))]
    (apply merge (for [k all-keys]
                 (let [str1 (when t1 (t1 k))
                       str2 (when t2 (t2 k))
                       st1 (if str1 str1 [0.0 0.0])
                       st2 (if str2 str2 [0.0 0.0])]
                   (if (> (first st1) (first st2))
                     {k st1}
                     {k st2}))))))

(defn refresh-termlinks [task]
  "Create new potential termlinks to other terms modulated by the concept priority they link to."
  ; :termlinks {term [budget]}
  (let [concept-prio (fn [z] (let [prio (concept-priority z)]
                               (if prio
                                 prio
                                 0.0)))
        newtermlinks (use-stronger (apply merge
                                   (for [tl (get-linkable-terms task)] ;prefer existing termlinks strengths
                                     {tl (calc-link-strength tl) #_[(* (first termlink-default-budget)
                                                                                                         (concept-prio tl)) (second termlink-default-budget)]}))
                            (:termlinks @state))
        valid-links (select-keys newtermlinks (get-existing-terms-from-links newtermlinks))];only these keys which exist in concept bag
    (set-state! (merge @state {:termlinks valid-links}))))

(defmacro when-let*
  "https://clojuredocs.org/clojure.core/when-let"
  ([bindings & body]
   (if (seq bindings)
     `(when-let [~(first bindings) ~(second bindings)]
        (when-let* ~(drop 2 bindings) ~@body))
     `(do ~@body))))

(defn link-feedback-handler
  "Link growth by contextual relevance of the inference and the result, as well as usefulness of the result."
  [from [_ [derived-task belief-concept-id]]]                       ;this one uses the usual priority durability semantics
  (try
    ;TRADITIONAL BUDGET INFERENCE (BLINK PART)
    (let [truth-quality (if (:truth derived-task)
                 (truth-to-quality (:truth derived-task))
                 (w2c 1.0))
          truth-quality-to-confidence (* truth-quality 1.0)
          [f c] ((:termlinks @state) belief-concept-id)
          premise-task-id (first (:parent-statement derived-task))]
      (when (and f c (:truth derived-task)) ;just revise by using the truth value directly
        (add-termlink belief-concept-id (revision [1.0 truth-quality-to-confidence] [f c]))
        (forget-termlinks-absolute)
        (when-let* [[element _] (b/get-by-id (:tasks @state) premise-task-id)
                   elem-task (:task element)
                    lbudgets (if-let [buds (:lbudgets elem-task)] buds {})
                    lbudget (if-let [bud (belief-concept-id lbudgets)] bud [0.5 0.0])
                    lbudget* (revision [1.0 truth-quality-to-confidence] lbudget)
                    lbudgets* (assoc lbudgets belief-concept-id lbudget*)
                    task* (assoc elem-task :lbudgets lbudgets*)]
                   (update-task-in-tasks state task* elem-task))))
    (catch Exception e () #_(println "fail"))))

(defn not-outdated-record-entry [[id time]]
  (< (- @nars-time time) 25))

(defn get-termlink-endpoints
  "Get the link endpoints, namely the concepts which the concept links to: their id as well as priority."
  [record link-budgets]
  (let [initbag (b/default-bag concept-max-termlinks)
        get-link-budget-exp (fn [k] (if (and link-budgets (k link-budgets))
                                  (expectation (k link-budgets))
                                  0.0))
        individual-budget-weight 1.0]
    (try
      (reduce (fn [a b] (b/add-element a b)) initbag (for [[k v] (filter (fn [[k _]] (or (nil? record)
                                                                                         (not (some (fn [[id time]]
                                                                                                      (and
                                                                                                        (not-outdated-record-entry [id time])
                                                                                                        (= id k)))

                                                                                                    record))))
                                                                         (:termlinks @state))]
                                                      {:priority (+    (expectation v)
                                                                       (* individual-budget-weight (get-link-budget-exp k))
                                                                   (:priority (first (b/get-by-id @c-bag k))))
                                                       :id       k}))
      (catch Exception e (print "")
                         ;(println (str "error in get-termlink-endpoints: " e)
                         ))))

(defn select-termlink-ref
  "Select the termlink probabilistically, taking link strength and target priorities into account."
  [record link-budgets]
  ;now search through termlinks, get the endpoint concepts, and form a bag of them
  (let [initbag (b/default-bag concept-max-termlinks)
        resbag (get-termlink-endpoints record link-budgets)]
    ;now select an element from this bag
    (if (and resbag (pos? (b/count-elements resbag)))
      (let [[beliefconcept _] (b/get-by-index resbag (selection-fn (b/count-elements resbag)))]
        #_(forget-termlink (:id beliefconcept))               ;apply forgetting for termlinks only on selection
        [(:id beliefconcept) (get-ref-from-term (:id beliefconcept))])
      nil)))