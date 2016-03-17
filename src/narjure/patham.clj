(ns patham
  (:require [instaparse.core :as insta])
  (:gen-class))



;todo implement properly:
(defn rank-value [t] t)
(defn project [t ref] t)

;ranking:
;ref: the >task< we rank our task according to. t: our >task<
(defn rank-task [ref t] {:task t :value (rank-value (project t ref))})
;table: the belief >table< we want to get the best element in respect to the >task< ref

;
(defn best-ranked [table ref] (apply max-key :value (map (partial rank-task ref) table)))
;example: (best-ranked [5 8 1 2 3 4] 1) -> {:task 8, :value 8}


;concept creation


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  ((insta/parser (clojure.java.io/resource "narsese.bnf") :auto-whitespace :standard) "<bird --> swimmer>. %0.10;0.60%"))
