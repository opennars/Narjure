(ns narjure.defaults)

(def belief-frequency 1.0)
(def belief-confidence 0.9)

(def truth-value
  [belief-frequency belief-confidence])

(def belief-priority 0.99)
(def belief-durability 0.9)
;todo clarify this
(def belief-quality 0.5)

(def belief-budget
  [belief-priority belief-durability belief-quality])

(def question-priority 0.9)
(def question-durability 0.9)
;todo clarify this
(def question-quality 0.56)

(def question-budget
  [question-priority question-durability question-quality])

(def goal-confidence 0.9)
(def goal-priority 0.99)                                    ;set higher than potential quality rewards in the system (best-operation-selection, structural reinforcement)
(def goal-quality 0.567)
(def goal-durability 0.9)

(def goal-budget
  [goal-priority goal-durability goal-quality])

(def budgets
  {:belief belief-budget
   :question question-budget
   :goal goal-budget
   :quest question-budget})

(def c-priority 0.5)                                        ; default concept priority

(def ^{:type double} horizon 1)                             ; personality factor for evidential horizon
(def max-evidence 10)                                       ; maximum length of evidence trail
(def temporal-window-duration 1)                           ; number of system cycles to consider as concurrent
(def term-complexity-offset 11) ;todo a better budget function instead
(def max-term-complexity-init 10)
(def max-term-complexity (atom max-term-complexity-init))                                ; maximum number of terms and sub terms in a statement - syntactic complexity
(def priority-threshold 0.0)                              ; concepts and tasks have to be above this to be processed
(def max-concept-selections 5)                              ; don't set higher if not on a sumpercomputer, will cause trouble
(def termlink-single-sample-evidence-amount 0.01)           ; default conf for termlink truth value
(def concept-max-termlinks 10)                              ; max size of termlink bag per concept
(def max-tasks 30)                                          ; max size of task bag per concept
(def max-anticipations 10)                                   ; max size of anticipation bag per concept
(def max-concepts 250)                                     ; do not make too small (less than 50) as causes cyclic issue between task-dispatcher and concept-manager
(def max-derived-sentences 50)                              ; derived task bag capacity
(def max-derived-selections 10)                             ; max derived selections per cycle
(def max-event-selections 10)                               ; no longer used - number of events to select per cycle
(def inverse-decay-rate 10)                                        ; forgetting adjustment rate for concepts e^-lt where l = (1.0 - durabiity) / decay-rate
; durability of 0.5 and decay rate of 100 fully decays priority in 1000 cycles
; decay-rate of 10 would fully decay it in 100 cycles
(def system-tick-interval-slow 136)
(def inference-tick-interval-slow 100)
(def system-tick-interval-medium 50)
(def inference-tick-interval-medium 25)
(def system-tick-interval-fast 20)
(def inference-tick-interval-fast 10)
(def system-tick-interval (atom system-tick-interval-medium))                               ;make big enough
(def inference-tick-interval (atom inference-tick-interval-medium))
(def anticipation-scale-dependent-tolerance 4.0)            ;has to be 4 since interval rounding has to agree with time measurement in 2-power
(def anticipation-disappointment-priority-gain 1.5)         ;should be >=1.0 !
(def termlink-default-budget [0.1 0.9])
(def concept-selection-introduced-termlink-default-budget [0.1 0.3])
(def termlink-context-adaptations-speed 0.05)
(def revision-relevant-event-distance 30.0)                  ;TODO check its relation to temporal window and the parameter in projection
(def termlink-record-size concept-max-termlinks)
(def negation-rule-priority-threshold 0.9) 