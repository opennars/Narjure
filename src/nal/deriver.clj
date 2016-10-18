(ns nal.deriver
  (:require
    [nal.deriver.utils :refer [walk]]
    [nal.deriver.key-path :refer [mall-paths all-paths mpath-invariants
                                  path-with-max-level]]
    [nal.deriver.rules :refer [rule]]
    [nal.deriver.normalization :refer [commutative-ops sort-commutative]]
    [clojure.set :as set]
    [nal.term_utils :refer :all]
    [clojure.core.memoize :refer [lru]]
    [nal.rules :as r]))

(defn get-matcher [rules p1 p2]
  (let [matchers (->> (mall-paths p1 p2)
                      (filter rules)
                      (map rules))]
    (case (count matchers)
      0 (constantly [])
      1 (first matchers)
      (fn [t1 t2] (mapcat #(% t1 t2) matchers)))))

#_(def mget-matcher (memoize get-matcher))
(def mget-matcher (lru get-matcher :lru/threshold 50))
#_(def mget-matcher get-matcher)
#_(def mpath (memoize path-with-max-level))
(def mpath (lru path-with-max-level :lru/threshold 50))
#_(def mpath path-with-max-level)

(defn parallel-conj [term]
  (and (coll? term)
       (= (first term) '&|)))

(defn sequ-conj [term]
  (and (coll? term)
       (= (first term) 'seq-conj)))

(defn parallel-conj-reduce [term layer]
  term
  (if (parallel-conj term)
    (let [reduced (apply concat
                         (for [x term]
                           (if (parallel-conj x)
                             (parallel-conj-reduce x (inc layer))
                             (if (= x '&|)
                               []
                               [x]))))]
      (if (= layer 0)
        (vec (conj reduced '&|))
        (vec reduced)))
    term))

(defn generate-conclusions-no-commutativity
  "generate conclusions not taking commutative subterms of premises into account"
  [rules {p1 :statement :as t1} {p2 :statement :as t2}]
  (let [matcher (mget-matcher rules (mpath p1) (mpath p2))
        result (set (matcher t1 t2))]
    (for [z result]
      (assoc z :statement (sort-commutative (parallel-conj-reduce (:statement z) 0))))))

;USE COUNTER (global seed, for making testcased deterministic
(def use-counter (ref 0))

(defn use-counter-reset []
  (do
    (dosync (ref-set use-counter 0))
    @use-counter))

;Adjusted shuffle given a seed, "shuffle-with-seed", from sloth:
;http://stackoverflow.com/questions/24553212/how-to-take-n-random-items-from-a-collection-in-clojure
;(since we don't want non-deterministic testcases)
(defn shuffle-random
  "Return a random permutation of coll with a seed"
  [coll]
  (dosync (commute use-counter inc)
          (let [seed (deref use-counter)
                al (java.util.ArrayList. coll)
                rnd (java.util.Random. (* seed 50000))]
            (java.util.Collections/shuffle al rnd)
            (clojure.lang.RT/vector (.toArray al)))))

(defn shuffle-term-one-layer [t]                            ;TODO put into term utils once merged with master
  (concat (list (first t)) (shuffle-random (rest t))))

(defn shuffle-term [A]                                      ;TODO put into term utils once merged with master
  "Shuffle a term recursively (all commutative subterms like (A <-> B)"
  (let [shuffle? (fn [A] (and (coll? A)
                              (some #(= (first A) %) commutative-ops)))
        shuffled (if (shuffle? A)
                   (shuffle-term-one-layer A)
                   A)]
    (if (shuffle? A)
      (for [x shuffled]
        (shuffle-term x))
      A)))

(defn generate-conclusions
  "Generate all conclusions between task t1 and task t2"
  [{p1_ :statement :as t1} {p2 :statement :as t2}]
  ;assign statement
  (let [p1 (if                                       ;ok the first one is eternal the second not, so lets use
             (and (= (:occurrence p1_) :eternal)     ;the occurence time of the second to not lose the temporal information (valid since it is eternal anyway)
                  (not= (:occurrence p2) :eternal))  ;this way eternalization is left to eternalization
             (assoc p1_ :occurrence (:occurrence p2)) ;note that we use the eternal belief also anyway currently
             p1_)                                     ;so the eternal case is not lost!!
        iterate-n (fn [next-it prev-shuffles prev-results x]
                    (let [shuffled-p1 (shuffle-term p1)
                          shuffled-p2 (shuffle-term p2)
                          shuffle-pair [shuffled-p1 shuffled-p2]
                          unification-power 5] ;how much shuffle attempts
                      (if (< x unification-power)
                        (if (some #{shuffle} prev-shuffles)
                          (next-it next-it prev-shuffles prev-results (inc x)) ;generate-conclusions-no-commutativity is expensive this is why we avoid it
                          (let [new-results (set/union prev-results
                                                       (set (generate-conclusions-no-commutativity (r/rules (:task-type t1))
                                                                                                   (assoc t1 :statement shuffled-p1)
                                                                                                   (assoc t2 :statement shuffled-p2))))]
                            (next-it next-it (set/union prev-shuffles #{shuffle-pair}) (set/union prev-results new-results) (inc x))))
                        prev-results)))]
    (iterate-n iterate-n #{} #{} 0)))

(defn valid-statement
  "Valid statement filter.
  The filter is a preliminary solution to get rid of statements that are not valid NAL statements.
  Some of these need detailled analysis and a lot of care / inference rule condition improvement to get rid of." ;TODO extent
  [term]
  (and
    #_(coll? term)

    ;dont allow a. terms, only NAL statements are allowed (TODO discuss NAL9 name operator handling)
    #_(some #(= % (first term)) '[--> <-> ==> pred-impl retro-impl
                                =|> <=> </> <|>
                                -- || conj seq-conj &|])

    ;temporal copula only allowed once
    (<= (count (filter '#{==> pred-impl retro-impl
                        =|> <=> </> <|>} (flatten term)))
       1)

    ;inheritance and Similarity can't have independent vars
    (not (and (coll? term)
              (some #(= % (first term)) '[--> <->])
              (some #(= % 'ind-var) (flatten term))))

    (not (and (coll? term)
              (= (count term) 3)
              (coll? (first term))
              (= 'seq-conj (first (first term)))
              (interval? (first (first term)))))

    (not-any? #(and (coll? term)
                    (= (count term) 3)
                    (= (first term) %)
                    (= (second term) (nth term 2)))
              '[--> <-> ==> pred-impl retro-impl =|> <=> </> <|>])))

(defn occurrence-type
  "Occurrence task of tasks, either :eternal or :event"
  [occ]
  (case occ
    :eternal :eternal
    :event))

;this is the inference function we should use
(defn inference
  "Inference between two premises"
  [parsed-p1 parsed-p2]
  (set (map #(assoc % :statement
                      (apply-interval-precision (normalize-variables (:statement %))))
            (filter (fn [st] (and (or (= (:task-type st) :question)
                                      (= (:task-type st) :quest)
                                      (and (contains? st :truth)
                                           (coll? (:truth st))
                                           (> (second (:truth st)) 0)))
                                  (valid-statement (:statement st))
                                  (not (and (= (:task-type st) :belief) ;TODO why happens at all?
                                            (some #{'qu-var} (flatten (:statement st)))))))
                    (map no-truth-for-questions-and-quests
                         (map interval-reduction
                              (generate-conclusions parsed-p1 parsed-p2)))))))