(ns examples.tcp.interface
  (:require [narjure.global-atoms :refer :all]
            [narjure.core :as nar]
            [narjure.sensorimotor :refer :all]
            [narjure.narsese :refer [parse2]]
            [narjure.debug-util :refer :all]
            [manifold.stream :as s]
            [manifold.deferred :as d]
            [co.paralleluniverse.pulsar.core :refer [promise]]
            [clojure.string :as cstr]
            [examples.tcp.server :refer :all])
  (:gen-class))

; The help dialogue for the server
(def HELP
"
         Client Querys                       |         Expected Reply
        op    |       args                   |     op     |          args
1.   new-op   | opname1 opname2...           | valid      | success1? success2?...
2a.  ask      | narsese1                     | valid      | validnarsese1?
2b.           |                              | answer     | task solution
3.   input    | narsese1 narsese2...         | valid      | validnarsese1? validnarsese2?...
4.   valid    | narsese1 narsese2...         | valid      | validnarsese1? validnarsese2?...
5.   concept  | narsese1 narsese2...         | concept    | conceptstr1/invalid conceptstr2/invalid...
6.   concept  |                              | concept    | conceptstr1 conceptstr2...
7.   parse    | narsese                      | confirmed? | data
8.   help     |                              | help       | helpstring
9.   reset    |                              | confirmed? |
10.  quit     |                              | confirmed? |
14. op-name   | success? return1 return2...  | confirmed? | errorstring

         Server Querys                       |         Expected Reply
        op    |       args                   |     op     |          args
11. op-name   | arg1 arg2...                 | confirmed? |
12. say       | narsese                      |            |
13. answer    | task solution                |            |
14. error     |                              |            |
              |                              | NOTE: Must be returned in 500ms.
")

; ID & Dividers
(defn newid [] (str (java.util.UUID/randomUUID))) ; Easy new uuid wrapper.
(def IN ":<:")
(def OUT ":>:")
(def CONFIRMED "confirmed")
(def INVALID "invalid")
;(def RUNNING (atrom true))      ; Defines that the server is running
(def WAITING (atom {}))         ; Maps tasks to lists of channel and query id's to be sent upon updated answer. {taskn [{:ch s/stream :id id}]}
                                ; Also maps id's to promises from new-op queries.
(def ANSWERS (atom {}))         ; Maps tasks to solutions, allows for immediate responses upon ask.
(def OPS (atom {}))             ; Maps custom operation names to the channel (s/stream) which hosts them.
(def TIMEOUT 500)               ; Timeout used for custom operations, set to half a second
(def predefined-ops ["new-op" "input" "valid" "concept" "concepts"
                     "help" "reset" "quit" "answer" "say" "parse" CONFIRMED INVALID IN OUT]) ; Illegal custom op-names
(def PORT (if (empty? *command-line-args*)        ; The port can be set from the command line
              8080                                ; It defaults to 8080 which is commonly used on c9.io and localhost
              (first (int *command-line-args*)))) ; Otherwise, the first arg becomes the port.

; Support functions
(defn replace-with
  "Replaces any x in collection l with y, given f(x) returns true."
  [l f y]
  (let [r (fn [x] (if (f x) y x))] ; if f(x) is true, then replace with y, else return x
    (map r l)))                    ; return the mapping of r onto l

(defn conj-to-map-at-key
  "Given a map associating keys with collections of values,
   Conjoins v to collection associated with k.
   Associates new collection containing v if k does not exist in m."
  [m k v]
  (let [l (get m k false)]
    (if l
      (assoc m k (conj l v))
      (assoc m k [v]))))

(defn peek-from-map-at-key
  "Given a map associating keys with collections of values,
   Peeks from the associated collection in map m at key k."
  [m k]
  (peek (get m k [])))

(defn pop-from-map-at-key
  "Given a map associating keys with collections of values,
   Pops from the associated collection in map m at key k.
   Returns new map, usable as (swap! m pop-from-map-at-key k)."
    [m k]
    (let [l (get m k false)]
      (if l (assoc m k (pop l)) (m))))

; Communication
(defn send-stream
  "Sends string over TCP to given stream."
  ([ch op]
  (send-stream ch (newid) op))
  ([ch id op]
  (let [msg (str id OUT op)]
    (println (str "Sending: " msg))
    (when (not (s/closed? ch)) (s/put! ch msg))))
  ([ch id op & args]
  (let [msg (str id OUT op OUT (cstr/join OUT args))]
    (println (str "Sending: " msg))
    (when (not (s/closed? ch)) (s/put! ch msg)))))

(defn confirm
  "Quick function to send confirmation or error as a response given boolean input."
  [ch id success & args]
  (do
    (if success
      (apply send-stream (into [ch id CONFIRMED] args))
      (apply send-stream (into [ch id INVALID] args)))
  success))

; Automatic true/false
(def error #(confirm % % false))
(def conf  #(confirm % % true))

; Narsese Input
(defn input-narsese
  "Input the received Narsese into the system."
  ([string]
  (try
    (let [statement (parse2 string)]
      (nars-input-narsese string)
      (println (str "NARS hears " string))
      true)
    (catch Exception e false))))

(defn valid-narsese
  "Checks if input narsese is valid."
  ([string]
  (try
    (do (parse2 string) true)
    (catch Exception e false))))

(defn request-answer
    [ch id taskn]
    (let [soln (get @ANSWERS taskn)]
    (if soln
        (send-stream ch id "answer" soln)
        (swap! WAITING conj-to-map-at-key taskn {:ch ch :id id}))))

(defn update-answer
    [taskn soln]
    (swap! ANSWERS assoc taskn soln)
    (loop [v (peek-from-map-at-key @WAITING taskn)
           cont (not (empty? (get @WAITING taskn)))]
      (println (str "Here1" cont))
      (when cont
        ;(println "Here2")
        (send-stream (:ch v) (:id v) "answer" soln)
        ;(println "Here3")
        (swap! WAITING pop-from-map-at-key taskn)
        ;(println "Here4")
        (recur (peek-from-map-at-key @WAITING taskn)
               (not (empty? (get @WAITING taskn)))))))


;(defn append_to_key [m k v] (assoc m k (if (contains? m k) (conj (get m k) v) [v])))
(defn ask
  "Ask a question."
  [ch id string]
  (if (valid-narsese string)
    (let [statement (str (narsese-print (:statement (parse2 string))))]
        (send-stream ch id "valid" true)
        (request-answer ch id statement)
        (input-narsese string) true)
    ;(do (send-stream ch id "valid" false) false)))
    false))

(defn confirmed? [lst] (= (first lst) CONFIRMED))
; Asyncronous listening function
(defn new_op_template
  "Used as the template to define new operations in narsee over the server."
  [op_name args operationgoal]
  ; First, create the key we will use for this particular call
  (println (str "new op call " op_name " " args " " operationgoal))
  (let [id (newid)
        swrite (get @OPS op_name :None)
        sread (promise)]
      (if-not (= swrite :None)             ; If this is an op
        (do (swap! WAITING assoc id sread) ; Create a waiting reference at this id
            ; Send the request to the appropriate stream hosting the op
            (apply send-stream (into [swrite id op_name operationgoal] args))
            ; Then wait for a reply
            (let [out (deref sread @TIMEOUT :timeout)] ; out is a vector. (first out) is assumed to be true/false, the rest are considered narsese statements.
              (swap! WAITING dissoc id) ; Destroy the waiting reference
              (if (= out :timeout) (do (error swrite id "Timeout Error.") ; Write a timeout error to channel
                                       false)                             ; And return a false vector
                                   (confirmed? out))                      ; Otherwise, return what was sent
        (println "No streams to write for op.") false)))))                ; Otherwise return false

(defn new-op
  "Register a new operation."
  [ch op_name]
  (if (not (or (some #{op_name} predefined-ops) ; op_name must not be a predefined-op
               (some #{op_name} (keys @OPS))))  ; op_name must not be taken by some other definition
    (do
        (swap! OPS assoc op_name ch) ; Associate operation name with the chanel of the host
        (swap! OPS assoc ch op_name) ; Associate channel with the operation name so it can be removed on close of host
        (nars-register-operation (symbol op_name) (partial new_op_template op_name)) ; Register the operation with NARS
        true) ; Return True
    false)) ; Otherwise Return False

(defn string-to-bool
  "Converts to true if it matches certain templates, false otherwise."
  [tfstr]
  (some #{tfstr} ["True" "true" "t" "T" "1" "1." "1.0"]))

(defn answer-op
  "Specifically handles answers."
  [ch id tfstr & concequences]
  (let [tf (string-to-bool tfstr)
        w (get @WAITING id)]
    (deliver w tf)))

(defn concequences
  [operation concequences]
  (let [op_name (first operation)
        op_args (cstr/join ", " (rest operation))
        c_args  (cstr/join ", " concequences)]
    (str "<(^" op_name "," op_args ") --> (&&, " c_args ")>. :|:")))

; Copied from ircbot
(defn concept
  "Show a concept"
  ([concept-str]
  (try
    (let [statement (parse2 (str concept-str "."))]
      (dissoc
        (first (narjure.bag/get-by-id @c-bag (:statement statement)))
        :ref))
    (catch Exception e nil))))

(defn concepts
  "Show all the concepts"
  ([]
  (:priority-index @c-bag))              ; get all concepts. FIXME: Is this a list?
  ([args]
  (let [returns (map concept args)]      ; get a list containing each concept or an error
    (replace-with returns not INVALID))) ; replace errors with INVALID and return
  ([arg0 & args] (apply concepts (into [arg0] args))))            ; can take them either as a list or as a bunch of items

(defn quit
  "Closes a client connection."
  [ch]
  (loop [ops (get @OPS ch)]                    ; Loop
    (if-not (empty? ops)                       ; Terminate loop when empty or nul
      (do (swap! OPS (fn [m k] (dissoc m (peek-from-map-at-key m k))) ch) ; dissasoc the opname key in OPS that associated with this channel
          (swap! OPS pop-from-map-at-key ch) ; Pop the opname from this channel's associated vector in OPS
          (recur (get @OPS ch)))               ; Recur with the rest of the associated ops
      (swap! OPS dissoc ch)))                  ; Finally, remove the ch key itself
  (s/close! ch)                                ; Close the channel after all references to it have been removed
  true)

(defn reset-nars
  "Resets the system"
  []
  (nar/shutdown)
  (nar/run)
  true)

(defn parse-in
  "Prints the string as received and splits it in two."
  [string]
    (println (str "Parsing: " string))
    (map cstr/trim (cstr/split string (re-pattern IN))))

(defn process-in
  [ch id op & args]
  (case op
    "new-op"     (apply send-stream (into [ch id "valid"] (for [x args] (new-op ch x))))
    "parse"      (apply confirm (into [ch id] (try [true (parse2 args)] (catch Exception e [false]))))
    "ask"        (ask ch id (first args))
    "input"      (apply send-stream (into [ch id "valid"] (for [x args] (input-narsese x))))
    "valid"      (apply send-stream (into [ch id "valid"] (for [x args] (valid-narsese x))))
    "concept"    (if (> (count args) 0)
                    (apply send-stream (into [ch id "concept"] (concepts args)))
                    (apply send-stream (into [ch id "concept"] (concepts))))
    "help"       (send-stream ch id "help" (str HELP))
    "reset"      (confirm ch id (reset-nars))
    "quit"       (do (confirm ch id) (quit ch))
    (if (contains? @WAITING op) (apply answer-op (into [ch op] args))
                                (error ch id "No such operation."))))

; Main Read Loop
(def SERVER (start-server
  (fn
    ;REF: https://github.com/ztellman/aleph/blob/master/examples/src/aleph/examples/tcp.clj
    [ch info]
      (println (str "New Connection: " info))
      (d/loop []

        ;; take a message, and define a default value that tells us if the connection is closed
        (-> (s/take! ch ::none)

          (d/chain

            ;; process message
            (fn [msg]
              (if (= ::none msg)
                (do (s/close! ch) (println "Lost Connection") false)
                (do
                  (println (str "Received: " msg))
                  (let [parse (into [ch] (parse-in msg))]
                    (when (>= (count parse) 3)
                      (apply process-in parse)
                      true)))))

            ;; if we were successful in our response, recur and repeat
            (fn [result]
              (when result
                (d/recur))))

          ;; if there were any issues on the far end, send a stringified exception back
          ;; and close the connection
          (d/catch
            (fn [ex]
              (println (str "Lost Connection" ex))
              (s/put! ch (str -1 OUT "quit" OUT ex)) ; sends a quit signal id of -1
              (quit ch))))))                         ; Quit this channel
  PORT))

;(defn shutdown
;  "Quits everything"
;  []
;  (map quit STREAMS)
;  (nar/shutdown)
;  (swap! RUNNING not)
;  (.close SERVER)
;  true)

; Startup
(defn setup-nars
  "Registers the operation and answer handler"
  []
  ;(nars-register-operation 'op_say (fn [args operationgoal]
  ;                                    (let [allargs (into [operationgoal] args)
  ;                                      total   (into [(newid) "say"] allargs)]
  ;                                (apply send-stream (into [ch] total)))))
  (nars-register-answer-handler (fn [task solution]
                                  (let [taskn (str (narsese-print (:statement task)))
                                        soln  (str (task-to-narsese solution))]
                                    (println (str "New Solution: " taskn " " soln))
                                    (update-answer taskn soln)))))
;(defn -test
;  []
;  (>!! reader (str 1 IN "input" IN "<a --> b>."))
;  (>!! reader (str 2 IN "input" IN "<b --> c>."))
;  (>!! reader (str 3 IN "input" IN "<a --> c>?"))
;  (loop [] (println (<!! writer)) (recur)))

(defn -main [& args]
  ; Setup nars answer-handler and new-op function
  (println "Running!")
  (setup-nars))
