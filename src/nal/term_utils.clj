(ns nal.term_utils
  (:require [clojure.set :as set]
            [narjure.defaults :refer :all]
            [clojure.core.unify :refer [unify]]))

(defn interval?
  "Is the term an interval?"
  [content]
  (and (sequential? content) (= (first content) :interval)))

(defn operation?
  "Checks whether st is an operation, namely a term of form <(*,{SELF},arg) --> op_name>"
  [st]
  (if (and (coll? st)
           (= (first st) '-->)
           (coll? (second st))
           (= (first (second st)) '*)
           (or (= (second (second st)) ['ext-set 'SELF])
               (= (second (second st)) ['ext-set 'SELF2])))
    (let [op (nth st 2)]
      (and (not (coll? op))
           (clojure.string/starts-with? (name op) "op_")))
    (clojure.string/starts-with? (str st) "self_op_")))

(defn negation-of-operation?
  "Checks whether st is the negation of an operation"
  [st]
  (and (coll? st)
       (= (first st) '--)
       (operation? (second st))))

(defn variable?
  "Checks whether it is a variable symbol"
  [t]
  (and (coll? t) (or (= (first t) 'ind-var)
                     (= (first t) 'dep-var)
                     (= (first t) 'qu-var))))

(defn compound?
  "Is the term a compound term?"
  [content]
  (and (sequential? content) (not= (first content) :interval) (not (variable? content))))

(defn syntactic-complexity
  "Calculates the syntactic complexity of a content term,
  for example (| (& a b) c) has complexity 5"
  [content]
  (if (compound? content)
    (reduce + (map syntactic-complexity content))
    1))

;todo -  temp function below should be in nal.clj - Patham9 to resolve
(def logic-ops
  #{'--> '<-> 'instance 'property 'instance-property '==> 'pred-impl '=|> 'retro-impl '<=> '</> '<|> 'ext-set 'int-set 'ext-inter '| '- 'int-dif '* 'ext-image 'int-image '-- '|| 'conj 'seq-conj '&|})

(defn placeholder? [t] (= '_ t))

(defn is-singular-sequence [st]
  "Checks whether the sequence is of (&/,a) form"
  (and (coll? st)
       (= (count st) 2)
       (= (first st) 'seq-conj)))

(defn reduce-sequence [st]                                  ;TODO move to term utils!!
  "Replaces (&/,a) => a recursively"
  (if (coll? st)
    (if (is-singular-sequence st)
      (second st)
      (apply vector (for [x st]
                      (reduce-sequence x))))
    (if (is-singular-sequence st)
      (second st)
      st))
  )

(defn interval-only-sequence [st]                                ;TODO move to term utils!!
  "checks whether st is of form (&/,[:interval n]),
  if yes, return n"
  (when (and (coll? st)
             (= (first st) 'seq-conj)
             (= (count st) 2)
             (coll? (second st))
             (= (first (second st)) :interval))
    (second (second st))))

(defn is-implication [st]                                   ;TODO move to term utils!!
  "checks whether the statement st is an implication"
  (and (coll? st)
       (or (= (first st) 'pred-impl)
           (= (first st) '==>)
           (= (first st) '=|>))))

(defn interval-at [f st]                                    ;TODO move to term utils!!
  "Returns the f=first/last etc. interval of a sequence st"
  (when (and (coll? st)
             (= (first st) 'seq-conj)
             (coll? (f st))
             (= (first (f st)) :interval))
    (second (f st))))

(defn interval-reduction
  "Reductions for intervals/sequences"
  [s]                                   ;TODO move to sentence utils?
  ;there are certain equivalence transformations only being valid in ''NAL7i (NAL7+Intervals)
  ;''and which indicate how intervals have to be treated as occurrence time modulators.
  ;These are:
  ;''Forward Interval'':
  ;< (&/, i10) ==> b> = <i10 ==> b> = b. :/10:
  ;(&/,i10,a_1, ..., a_n) = (&/,a_1, ..., a_n). :/10:
  ;''Backward Interval'':
  ;<a ==> (&/, i10)> = <a ==> i10> = a. :\10:
  ;(&/, a_1, ..., a_n, /10) = (&/, a_1, ..., a_n) . :\10:
  ;this term is only derived as a A detachment from A =/> B this is why this treatment is valid.
  ;Also note that these are mandatory reductions, as else if i10 is treated as normal terms,
  ;semantical nonsense could be derived if an interval alone is the subject or predicate of an implication
  ;or an event itself, since strictly speaking an interval itself does not encode an event semantically
  ;but only measures the distance between them!
  (let [ival-reducer
        (fn [st]
          (if (is-implication st)
            (let [subject (second st)
                  predicate (nth st 2)
                  ivalseq-s (interval-only-sequence subject)
                  ivalseq-p (interval-only-sequence predicate)]
              (if ivalseq-s
                [predicate ivalseq-s]                       ;<(&/, i10) ==> b> = <i10 ==> b> = b. :/10:
                (if ivalseq-p
                  [subject (- ivalseq-p)]
                  [(reduce-sequence st) 0])))                                 ;<a ==> (&/, i10)> = <a ==> i10> = a. :\10:
            (if (and (coll? st)
                     (= 'seq-conj (first st)))
              (let [ival-l (interval-at second st)
                    ival-r (interval-at last st)]
                (if ival-l
                  [(reduce-sequence (apply vector (first st) (rest (rest st)))) ival-l] ;(&/,i10,a_1, ..., a_n) = (&/,a_1, ..., a_n). :/10:
                  (if ival-r
                    [(reduce-sequence (apply vector (drop-last st))) (- ival-r)] ;(&/, a_1, ..., a_n, /10) = (&/, a_1, ..., a_n) . :\10:
                    [(reduce-sequence st) 0])))
              [(reduce-sequence st) 0])))]                                       ;TODO (&/ case) !!!!!
    (let [occurence (:occurrence s)
          [st shift] (ival-reducer (:statement s))]
      (assoc (assoc s :statement st) :occurrence
                                     (if (= :eternal occurence)
                                       :eternal
                                       (+ occurence shift))))))

(defn next-interval-point [n]                               ;term-utils
  "for establishing tolerance in temporal distance,
  by rounding to the next power of 2"
  (let [pot-2 (for [i (range 25)]
                (int (Math/pow 2 i)))]
    (first (for [x (filter #(> % n) pot-2)]
             (if (< (Math/abs (- x n))
                    (Math/abs (- (/ x 2) n)))
               x
               (/ x 2))))))

(defn str-is-integer [s]
  (when (not= s "")
    (every? #(Character/isDigit %) s)))

(defn interval-atom-to-interval
  "Change the interval atom to an interval"
  [t]
  (let [pot-ival (name t)
        num (apply str (rest pot-ival))]
    (if (and (= \i (first pot-ival))
             (str-is-integer num))
      [:interval (next-interval-point (Integer/parseInt num))]
      t)))

(defn parse-intervals [t]
  "all occurrences of i50 in the term are transformed to [:interval 50]"
  (if (coll? t)
    (apply vector
           (for [x t]
             (parse-intervals x)))
    (interval-atom-to-interval t)))

(defn apply-interval-precision [t]
  "all intervals are changed to the next interval precision point
  in this magnitude"
  (if (coll? t)
    (if (= (first t) :interval)
      [:interval (next-interval-point (second t))]
      (apply vector
             (for [x t]
               (apply-interval-precision x))))
    t))

(defn apply-interval-concept-term-transform [t]
  "all intervals are changed to the next interval precision point
  in this magnitude"
  (if (coll? t)
    (if (= (first t) :interval)
      [:interval "x"]
      (apply vector
             (for [x t]
               (apply-interval-concept-term-transform x))))
    t))

(defn concept-term-transform [t]
  (let [term (if (and (coll? t)   ;conceptualize (&/,a,i13) as a
                      (= (count t) 3)
                      (= (first t) 'seq-conj)
                      (interval? (last t)))
               (second t)
               t)]
    (apply-interval-concept-term-transform term)))

(defn statement-and-conceptid-equal [statement concept-id]
  "same in respect to intervals"
  (= (concept-term-transform statement)
     concept-id))

(defn not-statement-and-conceptid-equal [statement concept-id]
  "not same in respect to intervals"
  (not (statement-and-conceptid-equal statement concept-id)))

(defn termlink-subterms
  "Extract the termlink relevant subterms of the term up to 3 levels as demanded by the NAL rules"
  ([level content]
   (if (and (< level 3) (compound? content))
     (reduce set/union #{content} (map (partial termlink-subterms (inc level)) content))
     #{content}))
  ([content]
   (remove #(or (logic-ops %) (interval? %) (placeholder? %) (variable? %))
           (map concept-term-transform (termlink-subterms 0 content)))))

(defn no-truth-for-questions-and-quests [st]         ;sentence util
  "makes absolutely sure that goals and beliefs have no truth and desire value for now"
  (if (or (= (:task-type st) :quest)
          (= (:task-type st) :question))
    (dissoc st :truth)
    st))

(defn max-var-term
  "get the so far max. used variable number
  to illustrate: (max-var-term '[conj [disj [dep-var 2]] [ind-var 1]]) => 2"
  ([st]
   (max-var-term st 0))
  ([st m]
   (if (coll? st)
     (if (and (or (= (first st) 'ind-var)
                  (= (first st) 'dep-var))
              (str-is-integer (str (second st))))
       (read-string (str (second st)))                     ;dangerous? ^^
       (apply max (for [x st]
                    (max-var-term x))))
     m)))

(defn normalize-variables
  "inference rules introduce one #X or $X, #Y or $Y variable,
  we use the maximum so far existing variable number and add one,
  in order for new introduced variables to
  have a unique variable name when the old variables
  were of the form $i #k. Should be fine for most purposes for now."
  ([st]
   (normalize-variables st (inc (max-var-term st))))
  ([st m]
   (if (coll? st)
     (if (or (= st '[dep-var X])
             (= st '[dep-var Y]))
       ['dep-var (symbol (str m))]
       (if (or (= st '[ind-var X])
               (= st '[ind-var Y]))
         ['ind-var (symbol (str m))]                        ;why do we have symbols in derivations?
         (apply vector (for [x st]
                         (normalize-variables x m)))))
     st)))

(defn precondition-operation-consequent-statement
  "checks whether the statement of task is a it is a type of ((&/,precondition,operation) =/> consequent) statement,
  also returning the unification map used for the unification between the form and the statement."
  [task] ;(doseq [op ['pred-impl '</>]])
  (let [precondition-op-forms ['[pred-impl [seq-conj [seq-conj ?precondition ?interval1 ?operation] ?interval2] ?goal]]

        additional-condition (fn [z] (and (not= (second z) nil)
                                          (operation? ((second z) '?operation))
                                          (not (operation? ((second z) '?precondition)))
                                          (interval? ((second z) '?interval1))
                                          (not (negation-of-operation? ((second z) '?precondition)))
                                          (not (operation? ((second z) '?goal)))
                                          (not (negation-of-operation? ((second z) '?goal)))))]
    (first
      (filter additional-condition
             (for [form precondition-op-forms]
               [task (unify form (:statement task))])))))