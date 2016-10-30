(ns narjure.string
    (:require [narjure.narsese :refer [copulas compound-terms actions]]))

; General
(defn quotes [s] (str "\"" s "\""))

; Define sentences
(defn belief [stmnt] (str stmnt "."))
(defn question [stmnt] (str stmnt "?"))
(defn quest [stmnt] (str stmnt "@"))
(defn goal [stmnt] (str stmnt "!"))
(def sentence belief)

; Define Time
(defn present [sent] (str sent " :|:"))
(defn future [sent] (str sent " :|:"))
(defn past [sent] (str sent " :|:"))
(defn deftime [sent t] (str sent " :"t":"))
(def now present)

; Define Ops
(defmacro defineop
    ([o]
    `(defn ~o [a# b#] (str "<" a# ~o b# ">")))
    ([o s]
    `(defn ~o [a# b#] (str "<" a# (str ~s) b# ">"))))

; TODO: Find some way to do this with mapping onto copulas
; (for [[k# v#] (into [] (seq copulas))] (do (println k# v#) (eval (list 'defineop v#))))
; CompilerException java.lang.RuntimeException: Can't refer to qualified var that doesn't exist, compiling:(/tmp/form-init3886415578392221818.clj:1:7459)
(defineop --> "-->")
(defineop <-> "<->")
(defineop instance "{--")
(defineop property "--]")
(defineop instance-property "{-]")
(defineop ==> "==>")
(defineop pred-impl "=/>")
(defineop =|> "=|>")
(defineop retro-impl "=\\>")
(defineop <=> "<=>")
(defineop pred-eq "</>")
(defineop <|> "<|>")

; Define Combinations
(defmacro definecomp
    ([o]
    `(defn ~o [& a#] (str "(" ~o ", " (clojure.string/join ", " a#) ")")))
    ([o s]
    `(defn ~o [& a#] (str "(" (str ~s) ", " (clojure.string/join ", " a#) ")"))))

(definecomp ext-set "{")
(definecomp int-set "[")
(definecomp ext-inter "&")
(definecomp | "|")
(definecomp - "-")
(definecomp int-dif "~")
(definecomp * "*")
(definecomp ext-image "/")
(definecomp int-image "\\")
(definecomp -- "--")
(definecomp || "||")
(definecomp conj "&&")
(definecomp seq-conj "&/")
(definecomp &| "&|")
