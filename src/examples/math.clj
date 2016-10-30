(ns examples.math
  (:require [narjure.global-atoms :refer :all]
            [narjure.core :as nar]
            [narjure.narsese :refer [parse2]]
            [narjure.sensorimotor :refer :all]
            [narjure.debug-util :refer :all]
            [narjure.string :as n]))

; --- Support functions ---
(defn map_now [f args] (for [x args] (apply f x))
(defn map-hash-list [op x]
  "Applies op to every item in the map of lists x.
   op is a function taking only the value as a parameter."
  (into {} (map (fn [[k v]] [k (map op v)]) (seq x))))
(defn map-hash-list2 [op x]
  "Applies op to every item in the map of lists x.
   op is a function taking the key and value as a parameter."
  (into {} (map (fn [[k v]] [k (map #(op k %) v)]) (seq x))))

; NARS
(def references (atom {}))
(def names (atom 0))
(defn get-name [obj] (swap! names inc) (n/ext-set (str "%%$" @names)))
(defn nars-deref [arg] (get @references arg))
(defn nars-ref [obj] (let [out_ref (get-name obj)] (swap! references assoc out_ref obj) out_ref))
(defn op-return-sentence [op args_ref out_ref] (n/belief (n/--> (apply n/* args_ref out_ref) op)))
(defn op-return-error [op args_ref err] (n/belief (n/--> (apply n/* args_ref (n/quotes (str err))) op)))

; Sensorimotor template
(defn template
    "The template to register an op with NARS"
    [op]
    (fn [args operationgoal]
        (try 
        (let [o_args  (map_now nars-deref args)
              f       (nars-deref op)
              o_out   (apply f o_args)
              out_ref (nars-ref o_out)]
          (nars-input-narsese (op-return-sentence op o_args out_ref)) true)
        (catch Exception e
          (println e)
          (nars-input-narsese (op-return-error op o_args e)) false))
    ))

; TODO pass values natively as objects
(defn process-map1 [map_name m]
  (for [[k v] (seq m)] (n/belief (n/<-> (n/* map_name k) v))))
(defn process-map2 [map_name m]
  (for [[k v] (seq m)] (n/belief (n/--> (n/* map_name v) k))))
(defn rmap1 [k v]
  (println k v)
  (flatten
  (if-not (map? v)
    [(n/belief (n/<-> k (n/quotes v)))]
    (when-not (empty? (seq v))
      (map (fn [[a b]] (rmap1 (n/* k (n/quotes a)) b)) (seq v))))))
(defn rmap2 [parent k v]
  (println k v)
  (flatten
  (if-not (map? v)
    [(n/belief (n/--> (n/* parent (n/quotes v)) k))]
    (when-not (empty? (seq v))
      (map (fn [[a b]] (rmap2 (n/* parent k) (n/quotes a) b)) (seq v))))))

; List the ops we desire by their library
;(defmacro qt_lst [& x] (let [out (map #(list 'quote %) x)] (into [] out)))
(defmacro meta_fun [fun] (list 'meta (list 'var fun)))
(defmacro var_lst [& x] (let [out (map #(list 'var %) x)] (into [] out)))

; register all operations
(defn requirements [f_ref fvar]
  (rmap1 f_ref (meta fvar)))
(defn register [fvar]
  (let [f_ref (nars-ref fvar)
        temp  (template f_ref)]
  (nars-register-operation temp)
  (map_now nars-input-narsese (requirements f_ref fvar))))

(def ops (var_lst + - * / quot rem float double int))
(defn -main [& args]
  (nars-register-answer-handler (fn [task solution]
                                  (let [msg (str "NARS answer on " (narsese-print (:statement task)) "? is " (task-to-narsese solution))]
                                    (println msg))))
  (for [x ops] (do (println "Registering " x) (register x)))
  (nars-input-narsese "<(^\"#'clojure.core/+\",1,2) --> ?>?")
  (nars-input-narsese "(^\"#'clojure.core/+\",1,2)?"))
