(ns narjure.narsese
  (:require [instaparse.core :as i]
            [clojure.java.io :as io]
            [narjure.defaults :refer :all]
            [nal.term_utils :refer :all]
            [clojure.string :as str]
            [nal.deriver.normalization :refer [sort-commutative]]))

(def bnf-file "narsese.bnf")

(def parser
  "Loads narsese.bnf into instaparse"
  (i/parser (io/resource bnf-file) :auto-whitespace :standard))

(def copulas
  {"-->"  '-->
   "<->"  '<->
   "{--"  'instance
   "--]"  'property
   "{-]"  'instance-property
   "==>"  '==>
   "=/>"  'pred-impl
   "=|>"  '=|>
   "=\\>" 'retro-impl
   "<=>"  '<=>
   "</>"  '</>
   "<|>"  '<|>})

(def compound-terms
  {"{"  'ext-set
   "["  'int-set
   "&"  'ext-inter
   "|"  '|
   "-"  '-
   "~"  'int-dif
   "*"  '*
   "("  '*
   "/"  'ext-image
   "\\" 'int-image
   "--" '--
   "||" '||
   "&&" 'conj
   "&/" 'seq-conj
   "&|" '&|})

(defn get-compound-term [[_ operator-srt]]
  (compound-terms operator-srt))

(def actions {"." :belief
              "?" :question
              "@" :quest
              "!" :goal})

(def ^:dynamic *action* (atom nil))
(def ^:dynamic *lvars* (atom []))
(def ^:dynamic *truth* (atom []))
(def ^:dynamic *budget* (atom []))

(defn keep-cat [fun col]
  (into [] (comp (mapcat fun) (filter (complement nil?))) col))

(defn dispatcher [data]
  (if (string? data) :default (first data)))

(defmulti element dispatcher)

(defn get-copula [[_ [_ cop-symbol]]]
  (copulas cop-symbol))

(defmethod element :sentence [[_ & data]]
  (let [filtered (group-by string? data)]
    (reset! *action* (actions (first (filtered true))))
    (let [cols (filtered false)
          last-el (last cols)]
      (when (= :truth (first last-el))
        (element last-el))
      (element (first cols)))))

(defmethod element :statement [[_ & data]]
  (if-let [copula (get-copula data)]
    `[~copula ~@(keep-cat element data)]
    (keep-cat element data)))

(defmethod element :task [[_ & data]]
  `[~@(keep-cat element data)])

;looks strange but it is because of special syntax for negation --bird.
(defn get-comp-operator [second-el data]
  (let [first-el-type (get-in (vec data) [0 0])]
    (if (some #{(first second-el)} [:op-negation :op-int-set
                                    :op-ext-set :op-product])
      second-el
      ((if (= :term first-el-type) second first) data))))

(defmethod element :compound-term [[_ second-el & data]]
  (let [comp-operator (get-comp-operator second-el data)]
    `[~(get-compound-term comp-operator)
      ~@(keep-cat element (remove string? data))]))

(def var-prefixes '{"#" dep-var "?" qu-var "$" ind-var})
(defmethod element :variable [[_ type [_ v]]]
  (let [front (var-prefixes type)
        v [front (symbol v)]]
    (swap! *lvars* conj v)
    v))                                                     ;let v (symbol (str (var-prefixes type) v))

(defmethod element :word [[_ word]] (symbol word))

(defmethod element :truth
  [[_ & data]]
  (mapv element data))

(defmethod element :budget
  [[_ & data]]
  `[[~@(mapv element data)]])

(defmacro double-element [n a]
  `(defmethod element ~n [[t# d#]]
     (let [d# (Double/parseDouble d#)]
       (swap! ~a conj d#) d#)))

(double-element :frequency *truth*)
(double-element :confidence *truth*)
(double-element :priority *budget*)
(double-element :durability *budget*)
(double-element :quality *budget*)

(defmethod element :task [[_ & data]]
  (when (= :budget (ffirst data))
    (element (first data)))
  (element (last data)))

(defmethod element :term [[_ & data]]
  (when (seq? data)
    (keep element data)))

(defmethod element :default [_])

;TODO check for variables in statemnts, ignore subterm if it contains variable
(defn terms
  "Fetch terms from task."
  [statement]
  (into #{statement} (rest statement)))

(defn check-truth-value [v]
  (concat v (nthrest truth-value (count v))))

(defn check-budget [v act]
  (let [budget (budgets act)]
    (concat v (nthrest budget (count v)))))

(defn parse
  "Parses a Narsese string into task ready for inference"
  [narsese-str]
  (let [data (parser narsese-str)]
    (if-not (i/failure? data)
      (binding [*action* (atom nil)
                *lvars* (atom [])
                *truth* (atom [])
                *budget* (atom [])]
        (let [statement (element data)
              act @*action*]
          {:task-type act
           :lvars     @*lvars*
           :truth     (check-truth-value @*truth*)
           :budget    (check-budget @*budget* act)
           :statement statement
           ;:terms     (terms statement)  ; this is now done in task-creator and uses termlink-subterms
           }))
      data)))

(defn parse2 [stmt-with-potential-comment]
  "workaround in order to have (&&,a) as [conj a] rather than [[conj a]],
  also in order to support :|: as 0 occurring versus :eternal
  and also in order to support recognizing i50 as [interval 50], op_test as ^test
  so everything needed to workaround the parser issues.
  Also apply sentence reduction here, as also done for derivations."            ;TODO fix paser accordingly when there is time!
  (let [stmt (clojure.string/replace (first (str/split stmt-with-potential-comment #"//")) "^" "op_") ;get rid of comments
        parser-workaround (fn [prem, stmt]
                            (if (and (vector? stmt)
                                     (= (count stmt) 1))
                              (assoc prem :statement (first stmt))
                              (assoc prem :statement stmt)))
        time (if (.contains stmt ":|:")
               0
               (if (and (.contains stmt ":|")
                        (.contains stmt "|:"))
                 (read-string (first (str/split (second (str/split stmt #"\:\|")) #"\|\:")))
                 :eternal))
        parsedstmt (assoc (parse (str (first (str/split stmt #"\:\|"))
                                      (second (str/split stmt #"\|\:")))) :occurrence time)]
    (let [parsed (interval-reduction (parser-workaround parsedstmt (parse-intervals (:statement parsedstmt))))]
      (no-truth-for-questions-and-quests (assoc parsed :statement (sort-commutative (:statement parsed)))))))