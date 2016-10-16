(ns nal.test.deriver
  (:require [clojure.test :refer :all]
            [nal.test_utils :refer :all]))

;NAL1 testcases:

(deftest nal1-deduction
  (is (derived "<shark --> fish>."
               "<fish --> animal>."
               ["<shark --> animal>. %1.00;0.81%"])))       ;y

(deftest nal1-abduction
  (is (derived "<sport --> competition>."
               "<chess --> competition>. %0.90;0.90%"
               ["<chess --> sport>. %0.90;0.45%"])))        ;y

(deftest nal1-abduction-swap
  (is (derived "<sport --> competition>."
               "<chess --> competition>. %0.90;0.90%"
               ["<sport --> chess>. %1.00;0.42%"])))        ;y

(deftest nal1-induction
  (is (derived "<swan --> swimmer>. %0.90;0.90%"
               "<swan --> bird>."
               ["<bird --> swimmer>. %0.90;0.45%"])))       ;y

(deftest nal1-induction-swap
  (is (derived "<swan --> swimmer>. %0.90;0.90%"
               "<swan --> bird>."
               ["<swimmer --> bird>. %1.00;0.42%"])))       ;y

(deftest nal1-exemplification
  (is (derived "<robin --> bird>. %0.90;0.90%"
               "<bird --> animal>."
               ["<animal --> robin>. %1.00;0.42%"])))       ;y

(deftest nal1-conversion
  (is (derived "<swimmer --> bird>?"
               "<bird --> swimmer>."
               ["<swimmer --> bird>. %1.00;0.47%"])))       ;y

(deftest nal1-backward
  (is (derived "<?1 --> swimmer>?"
               "<bird --> swimmer>."
               ["<?1 --> bird>?",
                "<bird --> ?1>?"])))                        ;y

;NAL2 testcases:

(deftest nal2-comparison
  (is (derived "<swan --> swimmer>. %0.9;0.9%"
               "<swan --> bird>."
               ["<swimmer <-> bird>. %0.9;0.45%"])))        ;y

(deftest nal2-comparison2
  (is (derived "<sport --> competition>."
               "<chess --> competition>. %0.9;0.9%"
               ["<sport <-> chess>. %0.9;0.45%"])))         ;y

(deftest nal2-analogy
  (is (derived "<swan --> swimmer>."
               "<gull <-> swan>."
               ["<gull --> swimmer>. %1.0;0.81%"])))        ;y

(deftest nal2-analogy2
  (is (derived "<gull --> swimmer>."
               "<gull <-> swan>."
               ["<swan --> swimmer>. %1.0;0.81%"])))        ;y

(deftest nal2-resemblance
  (is (derived "<robin <-> swan>."
               "<gull <-> swan>."
               ["<gull <-> robin>. %1.0;0.81%"])))          ;y

(deftest nal2-inheritance-to-similarity
  (is (derived "<swan --> bird>."
               "<bird --> swan>. %0.1;0.9%"
               ["<swan <-> bird>. %0.1;0.81%"])))           ;y

(deftest nal2-inheritance-to-similarity2
  (is (derived "<swan --> bird>."
               "<bird <-> swan>. %0.1;0.9%"
               ["<bird --> swan>. %0.1;0.73%"])))           ;y

(deftest nal2-inheritance-to-similarity3
  (is (derived "<bird <-> swan>?"
               "<swan --> bird>. %0.9;0.9%"
               ["<bird --> swan>. %0.9;0.45%"])))           ;y

(deftest nal2-inheritance-to-similarity4
  (is (derived "<swan --> bird>?"
               "<bird <-> swan>. %0.9;0.9%"
               ["<swan --> bird>. %0.9;0.81%"])))           ;y

(deftest setDefinition
  (is (derived "<{Tweety} --> {Birdie}>."
               "{Tweety}."
               ["<{Birdie} <-> {Tweety}>. %1.0;0.81%"])))    ;y

(deftest setDefinition2
  (is (derived "<[smart] --> [bright]>."
               "[smart]."
               ["<[smart] <-> [bright]>. %1.0;0.81%"])))     ;y

(deftest setDefinition3
  (is (derived "<{Birdie} <-> {Tweety}>."
               "{Birdie}."
               ["<Birdie <-> Tweety>. %1.0;0.81%"
                "<{Tweety} --> {Birdie}>. %1.0;0.81%"])))    ;y

(deftest setDefinition4
  (is (derived "<[bright] <-> [smart]>."
               "[bright]."
               ["<bright <-> smart>. %1.0;0.81%"
                "<[bright] --> [smart]>. %1.0;0.81%"])))     ;y

(deftest structureTransformation
  (is (derived "<{Birdie} <-> {Tweety}>?"
               "<Birdie <-> Tweety>. %0.9;0.9%"
               ["<{Birdie} <-> {Tweety}>. %0.9;0.9%"])))    ;y

(deftest structureTransformation2
  (is (derived "<[bright] --> [smart]>?"
               "<bright <-> smart>. %0.9;0.9%"
               ["<[bright] --> [smart]>. %0.9;0.9%"])))     ;y

(deftest structureTransformation3
  (is (derived "<{bright} --> {smart}>?"
               "<bright <-> smart>. %0.9;0.9%"
               ["<{bright} --> {smart}>. %0.9;0.9%"])))     ;y

(deftest backwardInference
  (is (derived "<{?x} --> swimmer>?"
               "<bird --> swimmer>."
               ["<{?x} --> bird>?"])))                      ;y

(deftest missingEdgeCase1
  (is (derived "<p1 --> p2>."
               "<p2 <-> p3>."
               ["<p1 --> p3>. %1.00;0.81%"])))              ;y

;NAL3 testcases:

(deftest compound_composition_two_premises
  (is (derived "<swan --> swimmer>. %0.9;0.9%"
               "<swan --> bird>. %0.8;0.9%"
               ["<swan --> (|,swimmer,bird)>. %0.98;0.81%"
                "<swan --> (&,swimmer,bird)>. %0.72;0.81%"]))) ;y

(deftest compound_composition_two_premises2
  (is (derived "<sport --> competition>. %0.9;0.9%"
               "<chess --> competition>. %0.8;0.9%"
               ["<(|,chess,sport) --> competition>. %0.72;0.81%"
                "<(&,chess,sport) --> competition>. %0.98;0.81%"]))) ;y

(deftest compound_decomposition_two_premises
  (is (derived "<robin --> swimmer>. %0.0;0.9%"
               "<robin --> (|,swimmer,bird)>. %1.0;0.9%"
               ["<robin --> bird>. %1.0;0.81%"])))          ;y

(deftest compound_decomposition_two_premises2
  (is (derived "<robin --> swimmer>. %0.0;0.9%"
               "<robin --> (-,mammal,swimmer)>. %0.0;0.9%"
               ["<robin --> mammal>. %0.0;0.81%"])))        ;y

(deftest set_operations
  (is (derived "<planetX --> {Mars,Pluto,Venus}>. %0.9;0.9%"
               "<planetX --> {Pluto,Saturn}>. %0.7;0.9%"
               ["<planetX --> {Saturn,Mars,Venus,Pluto}>. %0.97;0.81%"
                "<planetX --> {Pluto}>. %0.63;0.81%"])))    ;y

(deftest set_operations2
  (is (derived "<planetX --> {Mars,Pluto,Venus}>. %0.9;0.9%"
               "<planetX --> {Pluto,Saturn}>. %0.1;0.9%"
               ["<planetX --> {Saturn,Mars,Venus,Pluto}>. %0.91;0.81%"
                "<planetX --> {Mars,Venus}>. %0.81;0.81%"]))) ;y

(deftest set_operations3
  (is (derived "<planetX --> [marsy,earthly,venusy]>. %1.0;0.9%"
               "<planetX --> [earthly,saturny]>. %0.1;0.9%"
               ["<planetX --> [marsy,earthly,saturny,venusy]>. %0.1;0.81%"
                "<planetX --> [marsy,venusy]>. %0.90;0.81%"]))) ;y

(deftest set_operations4
  (is (derived "<[marsy,earthly,venusy] --> planetX>. %1.0;0.9%"
               "<[earthly,saturny] --> planetX>. %0.1;0.9%"
               ["<[marsy,earthly,saturny,venusy] --> planetX>. %1.0;0.81%"
                "<[marsy,venusy] --> planetX>. %0.90;0.81%"]))) ;y

(deftest set_operations5
  (is (derived "<{Mars,Pluto,Venus} --> planetX>. %1.0;0.9%"
               "<{Pluto,Saturn} --> planetX>. %0.1;0.9%"
               ["<{Saturn,Mars,Venus,Pluto} --> planetX>. %0.1;0.81%"
                "<{Mars,Venus} --> planetX>. %0.90;0.81%"]))) ;y

(deftest composition_on_both_sides_of_a_statement
  (is (derived "<(&,bird,swimmer) --> (&,animal,swimmer)>?"
               "<bird --> animal>. %0.9;0.9%"
               ["<(&,bird,swimmer) --> (&,animal,swimmer)>. %0.90;0.73%"]))) ;y

(deftest composition_on_both_sides_of_a_statement_2
  (is (derived "<(|,bird,swimmer) --> (|,animal,swimmer)>?"
               "<bird --> animal>. %0.9;0.9%"
               ["<(|,bird,swimmer) --> (|,animal,swimmer)>. %0.90;0.73%"]))) ;y

(deftest composition_on_both_sides_of_a_statement2
  (is (derived "<(-,swimmer,animal) --> (-,swimmer,bird)>?"
               "<bird --> animal>. %0.9;0.9%"
               ["<(-,swimmer,animal) --> (-,swimmer,bird)>. %0.90;0.73%"]))) ;y

(deftest composition_on_both_sides_of_a_statement2_2
  (is (derived "<(~,swimmer,animal) --> (~,swimmer,bird)>?"
               "<bird --> animal>. %0.9;0.9%"
               ["<(~,swimmer,animal) --> (~,swimmer,bird)>. %0.90;0.73%"]))) ;y

(deftest compound_composition_one_premise
  (is (derived "<swan --> (|,bird,swimmer)>?"
               "<swan --> bird>. %0.9;0.9%"
               ["<swan --> (|,bird,swimmer)>. %0.90;0.73%"]))) ;y

(deftest compound_composition_one_premise2
  (is (derived "<(&,swan,swimmer) --> bird>?"
               "<swan --> bird>. %0.9;0.9%"
               ["<(&,swan,swimmer) --> bird>. %0.90;0.73%"]))) ;y

(deftest compound_composition_one_premise3
  (is (derived "<swan --> (-,swimmer,bird)>?"
               "<swan --> bird>. %0.9;0.9%"
               ["<swan --> (-,swimmer,bird)>. %0.10;0.73%"]))) ;y

(deftest compound_composition_one_premise4
  (is (derived "<(~,swimmer, swan) --> bird>?"
               "<swan --> bird>. %0.9;0.9%"
               ["<(~,swimmer, swan) --> bird>. %0.10;0.73%"]))) ;y

(deftest compound_decomposition_one_premise
  (is (derived "<robin --> (-,bird,swimmer)>. %0.9;0.9%"
               "robin?"                              ;TODO just use question for single premise as second
               ["<robin --> bird>. %0.90;0.73%"])))         ;y

(deftest compound_decomposition_one_premise2
  (is (derived "<(|, boy, girl) --> youth>. %0.9;0.9%"
               "youth."
               ["<boy --> youth>. %0.90;0.73%"])))          ;y

(deftest compound_decomposition_one_premise3
  (is (derived "<(~, boy, girl) --> [strong]>. %0.9;0.9%"
               "[strong]."
               ["<boy --> [strong]>. %0.90;0.73%"])))       ;y

;NAL4 testcases:

(deftest structural_transformation1
  (is (derived "<(acid,base) --> reaction>. %1.0;0.9%"
               "acid."
               ["<acid --> (/,reaction,_,base)>. %1.0;0.81%"]))) ;y

(deftest structural_transformation1_2
  (is (derived "<(acid,base) --> reaction>. %1.0;0.9%"
               "base."
               ["<base --> (/,reaction,acid,_)>. %1.0;0.81%"]))) ;y

(deftest structural_transformation2
  (is (derived "<acid --> (/,reaction,_,base)>. %1.0;0.9%"
               "reaction."
               ["<(acid,base) --> reaction>. %1.0;0.81%"]))) ;y

(deftest structural_transformation3
  (is (derived "<base --> (/,reaction,acid,_)>. %1.0;0.9%"
               "reaction."
               ["<(acid,base) --> reaction>. %1.0;0.81%"]))) ;y

(deftest structural_transformation4
  (is (derived "<neutralization --> (acid,base)>. %1.0;0.9%"
               "acid."
               ["<(\\,neutralization,_,base) --> acid>. %1.0;0.81%"]))) ;y

(deftest structural_transformation4_2
  (is (derived "<neutralization --> (acid,base)>. %1.0;0.9%"
               "base."
               ["<(\\,neutralization,acid,_) --> base>. %1.0;0.81%"]))) ;y

(deftest structural_transformation5
  (is (derived "<(\\,neutralization,_,base) --> acid>. %1.0;0.9%"
               "neutralization."
               ["<neutralization --> (acid,base)>. %1.0;0.81%"]))) ;y

(deftest structural_transformation6
  (is (derived "<(\\,neutralization,acid,_) --> base>. %1.0;0.9%"
               "neutralization."
               ["<neutralization --> (acid,base)>. %1.0;0.81%"]))) ;y

(deftest composition_on_both_sides_of_a_statement
  (is (derived "<(bird,plant) --> ?x>?"
               "<bird --> animal>. %1.0;0.9%"
               ["<(bird,plant) --> (animal,plant)>. %1.0;0.81%"]))) ;y

(deftest composition_on_both_sides_of_a_statement_2
  (is (derived "<(*,bird,plant) --> (*,animal,plant)>?"
               "<bird --> animal>. %1.0;0.9%"
               ["<(*,bird,plant) --> (*,animal,plant)>. %1.0;0.81%"]))) ;y

(deftest composition_on_both_sides_of_a_statement2
  (is (derived "<(\\,neutralization,acid,_) --> ?x>?"
               "<neutralization --> reaction>. %1.0;0.9%"
               ["<(\\,neutralization,acid,_) --> (\\,reaction,acid,_)>. %1.0;0.81%"]))) ;y

(deftest composition_on_both_sides_of_a_statement2_2
  (is (derived "<(\\,neutralization,acid,_) --> (\\,reaction,acid,_)>?"
               "<neutralization --> reaction>. %1.0;0.9%"
               ["<(\\,neutralization,acid,_) --> (\\,reaction,acid,_)>. %1.0;0.81%"]))) ;y

(deftest composition_on_both_sides_of_a_statement3
  (is (derived "<(/,neutralization,_,base) --> ?x>?"
               "<soda --> base>. %1.0;0.9%"
               ["<(/,neutralization,_,base) --> (/,neutralization,_,soda)>. %1.0;0.81%"]))) ;y

;NAL5 testcases:

;this one is local inference!!
;(deftest revision
;  (is (derived "<<robin --> [flying]> ==> <robin --> bird>>."
;               "<<robin --> [flying]> ==> <robin --> bird>>. %0.00;0.60%"
;               ["<<robin --> [flying]> ==> <robin --> bird>>. %0.86;0.91%"])))

(deftest deduction
  (is (derived "<<robin --> bird> ==> <robin --> animal>>."
               "<<robin --> [flying]> ==> <robin --> bird>>."
               ["<<robin --> [flying]> ==> <robin --> animal>>. %1.00;0.81%"]))) ;y

(deftest exemplification
  (is (derived "<<robin --> [flying]> ==> <robin --> bird>>."
               "<<robin --> bird> ==> <robin --> animal>>."
               ["<<robin --> animal> ==> <robin --> [flying]>>. %1.00;0.45%"]))) ;y

(deftest induction
  (is (derived "<<robin --> bird> ==> <robin --> animal>>."
               "<<robin --> bird> ==> <robin --> [flying]>>. %0.8;0.9%"
               ["<<robin --> [flying]> ==> <robin --> animal>>. %1.00;0.39%"
                "<<robin --> animal> ==> <robin --> [flying]>>. %0.80;0.45%"]))) ;y

(deftest abduction
  (is (derived "<<robin --> bird> ==> <robin --> animal>>."
               "<<robin --> [flying]> ==> <robin --> animal>>. %0.8;0.9%"
               ["<<robin --> bird> ==> <robin --> [flying]>>. %1.00;0.39%"
                "<<robin --> [flying]> ==> <robin --> bird>>. %0.80;0.45%"]))) ;y

(deftest detachment
  (is (derived "<<robin --> bird> ==> <robin --> animal>>."
               "<robin --> bird>."
               ["<robin --> animal>. %1.00;0.81%"])))       ;y

(deftest detachment2
  (is (derived "<<robin --> bird> ==> <robin --> animal>>. %0.70;0.90%"
               "<robin --> animal>."
               ["<robin --> bird>. %1.00;0.36%"])))         ;y

(deftest comparison
  (is (derived "<<robin --> bird> ==> <robin --> animal>>."
               "<<robin --> bird> ==> <robin --> [flying]>>. %0.8;0.9%"
               ["<<robin --> animal> <=> <robin --> [flying]>>. %0.80;0.45%"]))) ;y

(deftest comparison2
  (is (derived "<<robin --> bird> ==> <robin --> animal>>. %0.7;0.9%"
               "<<robin --> [flying]> ==> <robin --> animal>>."
               ["<<robin --> bird> <=> <robin --> [flying]>>. %0.70;0.45%"]))) ;y

(deftest analogy
  (is (derived "<<robin --> bird> ==> <robin --> animal>>."
               "<<robin --> bird> <=> <robin --> [flying]>>. %0.80;0.9%"
               ["<<robin --> [flying]> ==> <robin --> animal>>. %0.80;0.65%"]))) ;y

(deftest analogy2
  (is (derived "<robin --> bird>."
               "<<robin --> bird> <=> <robin --> [flying]>>. %0.80;0.9%"
               ["<robin --> [flying]>. %0.80;0.65%"])))     ;y

(deftest resemblance
  (is (derived "<<robin --> animal> <=> <robin --> bird>>."
               "<<robin --> bird> <=> <robin --> [flying]>>. %0.9;0.9%"
               ["<<robin --> animal> <=> <robin --> [flying]>>. %0.90;0.81%"]))) ;y

;derives nothing currently TODO lookat
(deftest conversions_between_Implication_and_Equivalence
  (is (derived "<<robin --> [flying]> ==> <robin --> bird>>. %0.9;0.9%"
               "<<robin --> bird> ==> <robin --> [flying]>>. %0.9;0.9%"
               ["<<robin --> bird> <=> <robin --> [flying]>>. %0.81;0.81%"]))) ;y

(deftest compound_composition_two_premises
  (is (derived "<<robin --> bird> ==> <robin --> animal>>."
               "<<robin --> bird> ==> <robin --> [flying]>>. %0.9;0.9%"
               ["<<robin --> bird> ==> (&&,<robin --> animal>,<robin --> [flying]>)>. %0.90;0.81%"
                "<<robin --> bird> ==> (||,<robin --> animal>,<robin --> [flying]>)>. %1.00;0.81%"]))) ;y

(deftest compound_composition_two_premises2
  (is (derived "<<robin --> bird> ==> <robin --> animal>>."
               "<<robin --> [flying]> ==> <robin --> animal>>. %0.9;0.9%"
               ["<(&&,<robin --> bird>, <robin --> [flying]>) ==> <robin --> animal>>. %1.00;0.81%"
                "<(||,<robin --> bird>, <robin --> [flying]>) ==> <robin --> animal>>. %0.90;0.81%"]))) ;y

(deftest compound_decomposition_two_premises1
  (is (derived "<<robin --> bird> ==> <robin --> [flying]>>."
               "<<robin --> bird> ==> (&&,<robin --> [flying]>,<robin --> animal>)>. %0.0;0.9%"
               ["<<robin --> bird> ==> <robin --> animal>>. %0.00;0.81%"]))) ;y

;structural inference!! (one premise) TODO derived-structural
(deftest compound_decomposition_two_premises2
  (is (derived "(&&,<robin --> [flying]>,<robin --> swimmer>). %0.0;0.9%"
               "<robin --> [flying]>."                      ;double premise!!
               ["<robin --> swimmer>. %0.00;0.81%"])))      ;y

;structural inference!!
(deftest compound_decomposition_two_premises3
  (is (derived "(||,<robin --> [flying]>,<robin --> swimmer>)."
               "<robin --> swimmer>. %0.0;0.9%"             ;double premise!
               ["<robin --> [flying]>. %1.00;0.81%"])))     ;y

;question driven forward inference!
(deftest compound_composition_one_premises
  (is (derived "(||,<robin --> [flying]>,<robin --> swimmer>)?"
               "<robin --> [flying]>."
               ["(||,<robin --> swimmer>,<robin --> [flying]>). %1.00;0.81%"]))) ;y

;structural inference!!
(deftest compound_decomposition_one_premises
  (is (derived "(&&,<robin --> swimmer>,<robin --> [flying]>). %0.9;0.9%"
               "<robin --> swimmer>?"                       ;it is termlink term only not a real premise!!
               ["<robin --> swimmer>. %0.9;0.73%"])))      ;y


(deftest compound_decomposition_one_premises2
  (is (derived "(&&,<robin --> swimmer>,<robin --> [flying]>). %0.9;0.9%"
               "<robin --> [flying]>?"                       ;it is termlink term only not a real premise!!
               ["<robin --> [flying]>. %0.9;0.73%"])))      ;y

;structural inference!!
(deftest compound_decomposition_one_premises_2
  (is (derived "(&&,<robin --> swimmer>,<robin --> [flying]>). %0.9;0.9%"
               "<robin --> [flying]>?"                      ;single prem;it is termlink term only not a real premise!!
               ["<robin --> [flying]>. %0.9;0.73%"])))      ;y

;structural inference!! this is forward inference, question used as termlink term could also be @ or whatever
(deftest negation
  (is (derived "(--,<robin --> [flying]>). %0.1;0.9%"
               "<robin --> [flying]>?"                      ;
               ["<robin --> [flying]>. %0.90;0.90%"])))     ;y     ;we want a termlink term only not a premise..

;structural inference!! while this is backward driven forward inference because the task is a question
(deftest negation2
  (is (derived "(--,<robin --> [flying]>)?"
               "<robin --> [flying]>. %0.9;0.9%"
               ["(--,<robin --> [flying]>). %0.10;0.90%"]))) ;y

(deftest contraposition
  (is (derived "<(--,<robin --> bird>) ==> <robin --> [flying]>>. %0.1;0.9%"
               "<robin --> [flying]>."
               ["<(--,<robin --> [flying]>) ==> <robin --> bird>>. %0.00;0.47%"]))) ;y

;TODO lookat
(deftest conditional_deduction
  (is (derived "<(&&,<robin --> [flying]>,<robin --> [withWings]>) ==> <robin --> bird>>."
               "<robin --> [flying]>."
               ["<<robin --> [withWings]> ==> <robin --> bird>>. %1.00;0.81%"]))) ;y

(deftest conditional_deduction2
  (is (derived "<(&&,<robin --> [flying]>,<robin --> [chirping]>,<robin --> [withWings]>) ==> <robin --> bird>>."
               "<robin --> [flying]>."
               ["<(&&,<robin --> [chirping]>,<robin --> [withWings]>) ==> <robin --> bird>>. %1.00;0.81%"]))) ;y

;TODO lookat
(deftest conditional_deduction3
  (is (derived "<(&&,<robin --> bird>,<robin --> [living]>) ==> <robin --> animal>>."
               "<<robin --> [flying]> ==> <robin --> bird>>."
               ["<(&&,<robin --> [flying]>,<robin --> [living]>) ==> <robin --> animal>>. %1.00;0.81%"]))) ;y

(deftest conditional_abduction
  (is (derived "<(&&,<robin --> swimmer>,<robin --> [flying]>) ==> <robin --> bird>>."
               "<<robin --> [flying]> ==> <robin --> bird>>."
               ["<robin --> swimmer>. %1.00;0.45%"])))      ;y
; TODO https://github.com/opennars/opennars2/issues/35 #R[((&& M U) ==> C) (U ==> C) |- M :post (:t/abduction :order-for-all-same)]

(deftest conditional_abduction2
  (is (derived "<(&&,<robin --> [withWings]>,<robin --> [chirping]>) ==> <robin --> bird>>."
               "<(&&,<robin --> [flying]>,<robin --> [withWings]>,<robin --> [chirping]>) ==> <robin --> bird>>."
               ["<robin --> [flying]>. %1.00;0.45%"])))     ;y

(deftest conditional_abduction3
  (is (derived "<(&&,<robin --> [flying]>,<robin --> [withWings]>) ==> <robin --> [living]>>. %0.9;0.9%"
               "<(&&,<robin --> [flying]>,<robin --> bird>) ==> <robin --> [living]>>."
               ["<<robin --> bird> ==> <robin --> [withWings]>>. %1.00;0.42%"
                "<<robin --> [withWings]> ==> <robin --> bird>>. %0.90;0.45%"]))) ;y

;(A ==> M) ((&& A :list/A) ==> C) |- ((&& M :list/A) ==> C) :post (:t/abduction :order-for-all-same :seq-interval-from-premises)
(deftest conditional_abduction4
  (is (derived "<<robin --> [flying]> ==> <robin --> [withBeak]>>. %0.9;0.9%"
               "<(&&,<robin --> [flying]>,<robin --> [chirping]>) ==> <robin --> bird>>."
               ["<(&&,<robin --> [withBeak]>,<robin --> [chirping]>) ==> <robin --> bird>>. %0.9;0.45%"]))) ;y

;TODO so far only basic conditional reasoning testing, TODO multiconditional syllogism detailed testing rule by rule

;NAL6 testcases:

;
; revision, this is local inference!!
;(deftest variable_unification1
;  (is (derived "<<$x --> bird> ==> <$x --> flyer>>."
;               "<<$x --> bird> ==> <$x --> flyer>>. %0.00;0.70%"
;              ["<<$x --> bird> ==> <$x --> flyer>>. %0.79;0.92%"])))

(deftest variable_unification2
  (is (derived "<<$1 --> bird> ==> <$1 --> animal>>."
               "<<$1 --> robin> ==> <$1 --> bird>>."
               ["<<$1 --> robin> ==> <$1 --> animal>>. %1.00;0.81%"
                "<<$1 --> animal> ==> <$1 --> robin>>. %1.00;0.45%"]))) ;y

(deftest variable_unification3
  (is (derived "<<$1 --> swan> ==> <$1 --> bird>>. %1.00;0.80%"
               "<<$1 --> swan> ==> <$1 --> swimmer>>. %0.80;0.9%"
               ["<<$1 --> swan> ==> (||,<$1 --> bird>,<$1 --> swimmer>)>. %1.00;0.72%"
                "<<$1 --> swan> ==> (&&,<$1 --> bird>,<$1 --> swimmer>)>. %0.80;0.72%"
                "<<$1 --> swimmer> ==> <$1 --> bird>>. %1.00;0.37%"
                "<<$1 --> bird> ==> <$1 --> swimmer>>. %0.80;0.42%"
                "<<$1 --> bird> <=> <$1 --> swimmer>>. %0.80;0.42%"]))) ;y

(deftest variable_unification4
  (is (derived "<<bird --> $1> ==> <robin --> $1>>."
               "<<swimmer --> $1> ==> <robin --> $1>>. %0.70;0.90%"
               ["<(&&,<bird --> $1>,<swimmer --> $1>) ==> <robin --> $1>>. %1.00;0.81%"
                "<(||,<bird --> $1>,<swimmer --> $1>) ==> <robin --> $1>>. %0.70;0.81%"
                "<<bird --> $1> ==> <swimmer --> $1>>. %1.00;0.36%"
                "<<swimmer --> $1> ==> <bird --> $1>>. %0.70;0.45%"
                "<<swimmer --> $1> <=> <bird --> $1>>. %0.70;0.45%"]))) ;y

(deftest variable_unification5
  (is (derived "<(&&,<$1 --> flyer>,<$1 --> [chirping]>) ==> <$1 --> bird>>."
               "<<$1 --> [withWings]> ==> <$1 --> flyer>>."
               ["<(&&,<$1 --> [withWings]>,<$1 --> [chirping]>) ==> <$1 --> bird>>. %1.00;0.81%"]))) ;y

;TODO analyze this case:
(deftest variable_unification6
  (is (derived "<(&&,<$1 --> flyer>,<$1 --> [chirping]>, <($1, worms) --> food>) ==> <$1 --> bird>>."
               "<(&&,<$1 --> [chirping]>,<$1 --> [withWings]>) ==> <$1 --> bird>>."
               ["<(&&,<$1 --> flyer>,<($1, worms) --> food>) ==> <$1 --> [withWings]>>. %1.00;0.45%"
                "<<$1 --> [withWings]> ==> (&&,<$1 --> flyer>,<($1, worms) --> food>)>. %1.00;0.45%"]))) ;n

(deftest variable_unification7
  (is (derived "<(&&,<$1 --> flyer>,<($1,worms) --> food>) ==> <$1 --> bird>>."
               "<<$1 --> flyer> ==> <$1 --> [withWings]>>."
               ["<(&&,<$1 --> [withWings]>,<($1,worms) --> food>) ==> <$1 --> bird>>. %1.00;0.45%"]))) ;y

(deftest variable_elimination
  (is (derived "<<$1 --> bird> ==> <$1 --> animal>>."
               "<robin --> bird>."
               ["<robin --> animal>. %1.00;0.81%"])))       ;y

(deftest variable_elimination2
  (is (derived "<<$1 --> bird> ==> <$1 --> animal>>."
               "<tiger --> animal>."
               ["<tiger --> bird>. %1.00;0.45%"])))         ;y

(deftest variable_elimination3
  (is (derived "<<$1 --> animal> <=> <$1 --> bird>>."
               "<robin --> bird>."
               ["<robin --> animal>. %1.00;0.81%"])))       ;y

(deftest variable_elimination4
  (is (derived  "<swan --> bird>. %0.90;0.90%"
                "(&&,<#1 --> bird>,<#1 --> swimmer>)."
                ["<swan --> swimmer>. %0.90;0.38%"])))       ;y

(deftest variable_elimination5
  (is (derived "<{Tweety} --> [withWings]>."
               "<(&&,<$1 --> [withWings]>, <$1 --> [chirping]>) ==> <$1 --> bird>>."
               ["<<{Tweety} --> [chirping]> ==> <{Tweety} --> bird>>. %1.00;0.81%"]))) ;y

(deftest variable_elimination6
  (is (derived "<(&&,<$1 --> flyer>,<$1 --> [chirping]>, <($1, worms) --> food>) ==> <$1 --> bird>>."
               "<{Tweety} --> flyer>."
               ["<(&&,<{Tweety} --> [chirping]>,<({Tweety},worms) --> food>) ==> <{Tweety} --> bird>>. %1.00;0.81%"]))) ;y

(deftest multiple_variable_elimination
  (is (derived "<(&&,<$2 --> lock>,<$1 --> key>) ==> <$2 --> (/,open,$1,_)>>."
               "<{lock1} --> lock>."
               ["<<$1 --> key> ==> <{lock1} --> (/,open,$1,_)>>. %1.00;0.81%"]))) ;y

(deftest multiple_variable_elimination2
  (is (derived "<<$1 --> lock> ==> (&&,<#2 --> key>,<$1 --> (/,open,#2,_)>)>."
               "<{lock1} --> lock>."
               ["(&&,<#2 --> key>,<{lock1} --> (/,open,#2,_)>). %1.00;0.81%"]))) ;y

(deftest multiple_variable_elimination3
  (is (derived "<{lock1} --> lock>."
               "(&&,<#1 --> lock>,<<$2 --> key> ==> <#1 --> (/,open,$2,_)>>)."
               ["<<$2 --> key> ==> <{lock1} --> (/,open,$2,_)>>. %1.00;0.43%"]))) ;y

;B (&& A :list/A) |- (&& :list/A) :pre (:belief? (:substitute-if-unifies "#" A B)) :post (:t/anonymous-analogy :d/strong :order-for-all-same :seq-interval-from-premises)]
(deftest multiple_variable_elimination4
  (is (derived "<{lock1} --> lock>."
               "(&&,<#1 --> lock>,<#1 --> (/,open,#2,_)>,<#2 --> key>)."
               ["(&&,<{lock1} --> (/,open,#2,_)>,<#2 --> key>). %1.00;0.43%"]))) ;y

(deftest variable_introduction
  (is (derived "<swan --> bird>."
               "<swan --> swimmer>. %0.80;0.9%"
               ["<<$1 --> bird> ==> <$1 --> swimmer>>. %0.80;0.45%"
                "<<$1 --> swimmer> ==> <$1 --> bird>>. %1.00;0.39%"
                "<<$1 --> bird> <=> <$1 --> swimmer>>. %0.80;0.45%"
                "(&&, <#1 --> bird>, <#1 --> swimmer>). %0.80;0.81%"]))) ;y

(deftest variable_introduction2
  (is (derived "<gull --> swimmer>."
               "<swan --> swimmer>. %0.80;0.9%"
               ["<<gull --> $1> ==> <swan --> $1>>. %0.80;0.45%"
                "<<swan --> $1> ==> <gull --> $1>>. %1.00;0.39%"
                "<<swan --> $1> <=> <gull --> $1>>. %0.80;0.45%"
                "(&&,<gull --> #1>,<swan --> #1>). %0.80;0.81%"]))) ;y

(deftest variables_introduction
  (is (derived "<{key1} --> (/,open,_,{lock1})>."
               "<{key1} --> key>."
               ["<<$1 --> key> ==> <$1 --> (/,open,_,{lock1})>>. %1.00;0.45%"
                "(&&,<#1 --> key>,<#1 --> (/,open,_,{lock1})>). %1.00;0.81%"]))) ;y

(deftest multiple_variables_introduction
  (is (derived "<<$1 --> key> ==> <{lock1} --> (/,open,$1,_)>>."
               "<{lock1} --> lock>."
               ["(&&,<#2 --> lock>,<<$1 --> key> ==> <#2 --> (/,open,$1,_)>>). %1.00;0.81%"
                "<(&&,<$1 --> key>,<$2 --> lock>) ==> <$2 --> (/,open,$1,_)>>. %1.00;0.45%"]))) ;y

(deftest multiple_variables_introduction2
  (is (derived "(&&,<#1 --> key>,<{lock1} --> (/,open,#1,_)>)."
               "<{lock1} --> lock>."
               ["(&&,<#2 --> lock>,<#1 --> key>,<#2 --> (/,open,#1,_)>). %1.00;0.81%"
                "<<$2 --> lock> ==> (&&,<$2 --> (/,open,#1,_)>,<#1 --> key>)>. %1.00;0.45%"]))) ;y

;this one is not so important, will find a solution for it when there is time
;(A --> K) (&& (#X --> L) (($Y --> K) ==> A)) |- (&& (#X --> L) A) :pre ((:substitute $Y A)) :post (:t/deduction)
(deftest second_level_variable_unification
  (is (derived "<{key1} --> key>. %1.00;0.90%"
               "(&&,<#1 --> lock>,<<$2 --> key> ==> <#1 --> (/,open,$2,_)>>). %1.00;0.90%"
               ["(&&,<#1 --> lock>,<#1 --> (/,open,{key1},_)>). %1.00;0.81%"]))) ;n

;this one is not so important, will find a solution for it when there is time
;#R[(A --> K) (($X --> L) ==> (&& (#Y --> K) :list/A)) |- (($X --> L) ==> (&& :list/A)) :pre ((:substitute #Y A)) :post (:t/anonymous-analogy)]
(deftest second_level_variable_unification2
  (is (derived "<{key1} --> key>. %1.00;0.90%"
               "<<$X --> lock> ==> (&&,<#Y --> key>,<$X --> (/,open,#Y,_)>)>. %1.00;0.90%"
               ["<<$X --> lock> ==> <$X --> (/,open,{key1},_)>>. %1.00;0.42%"]))) ;n

(deftest second_variable_introduction_induction
  (is (derived "<<lock1 --> (/,open,$1,_)> ==> <$1 --> key>>."
               "<lock1 --> lock>."
               ["<(&&,<#2 --> lock>,<#2 --> (/,open,$1,_)>) ==> <$1 --> key>>. %1.00;0.45%"]))) ;y

(deftest variable_elimination_deduction
  (is (derived "<lock1 --> lock>. %1.00;0.90%"
               "<(&&,<#1 --> lock>,<#1 --> (/,open,$2,_)>) ==> <$2 --> key>>. %1.00;0.90%"
               ["<<lock1 --> (/,open,$2,_)> ==> <$2 --> key>>. %1.00;0.81%"]))) ;y

(deftest abduction_with_variable_elimination
  (is (derived "<<lock1 --> (/,open,$1,_)> ==> <$1 --> key>>. %1.00;0.90%"
               "<(&&,<#2 --> lock>,<#2 --> (/,open,$1,_)>) ==> <$1 --> key>>. %1.00;0.90%" ;TODO other varname support!! doesnt have to be $1 here!!
               ["<lock1 --> lock>. %1.00;0.45%"])))         ;y

(deftest strong_unification
  (is (derived "<<(*,$a,is,$b) --> sentence> ==> <$a --> $b>>. %1.00;0.90%"
               "<(*,bmw,is,car) --> sentence>. %1.00;0.90%"
               ["<bmw --> car>. %1.00;0.81%"])))            ;y

(deftest strong_elimination
  (is (derived "<(&&,<(*,$a,is,cat) --> test>,<(*,$a,is,$b) --> sentence>) ==> <$a --> $b>>."
               "<(*,tim,is,cat) --> test>."
               ["<<(*,tim,is,$b) --> sentence> ==> <tim --> $b>>. %1.00;0.81%"]))) ;y

;NAL7 testcases: ;1. see if the bootstrap of the lower level works as it should:

(deftest temporal_nal5_exemplification_with_var
  (is (derived "<<($x, room) --> enter> =\\> <($x, door) --> open>>. %0.9;0.9%"
               "<<($x, door) --> open> =\\> <($x, key) --> hold>>. %0.8;0.9%"
               ["<<(*,$x,key) --> hold> =/> <(*,$x,room) --> enter>>. %1.00;0.37%"])))

(deftest temporal_nal5_deduction_with_var
  (is (derived "<<($x, room) --> enter> =\\> <($x, door) --> open>>. %0.9;0.9%"
               "<<($x, door) --> open> =\\> <($x, key) --> hold>>. %0.8;0.9%"
               ["<<(*,$x,room) --> enter> =\\> <(*,$x,key) --> hold>>. %0.72;0.58%"])))

(deftest temporal_nal5_induction_comparison_with_var
  (is (derived "<<(*, $x, door) --> open> =/> <(*, $x, room) --> enter>>. %0.9;0.9%"
               "<<(*, $x, door) --> open> =\\> <(*, $x, key) --> hold>>. %0.8;0.9%"
               ["<<(*,$x,key) --> hold> =/> <(*,$x,room) --> enter>>. %0.9;0.39%"
                "<<(*,$x,room) --> enter> =\\> <(*,$x,key) --> hold>>. %0.8;0.42%"
                "<<(*,$x,key) --> hold> </> <(*,$x,room) --> enter>>. %0.73;0.44%"])))

(deftest temporal_nal5_analogy_with_var
  (is (derived "<<(*, $x, door) --> open> =/> <(*, $x, room) --> enter>>. %0.95;0.9%"
               "<<(*, $x, room) --> enter> <|> <(*, $x, corridor_100) --> leave>>. %1.0;0.9%"
               ["<<(*, $x, door) --> open> =/> <(*, $x, corridor_100) --> leave>>. %0.95;0.81%"])))

;2. NAL7-only inference, taking intervals and occurrence times into account
(deftest temporal_induction_concurrent
  (is (derived "<(John,door) --> open>. :|:"
               "<(John,room) --> enter>. :|:"
               ["(&|,<(John,room) --> enter>,<(John,door) --> open>). :|: %1.00;0.81%"
                "<<(John,room) --> enter> =|> <(John,door) --> open>>. :|: %1.00;0.45%"
                "<<(John,room) --> enter> <|> <(John,door) --> open>>. :|: %1.00;0.45%"
                "<<(John,door) --> open> =|> <(John,room) --> enter>>. :|: %1.00;0.45%"])))

(deftest temporal_induction_after
  (is (derived "<(John,room) --> enter>. :|64|:"
               "<(John,door) --> open>. :|:"
               ["(&/,<(John,door) --> open>,i64,<(John,room) --> enter>). :|64|: %1.00;0.81%"
                "<(&/,<(John,door) --> open>,i64) =/> <(John,room) --> enter>>. :|64|: %1.00;0.45%"
                "<(&/,<(John,door) --> open>,i64) </> <(John,room) --> enter>>. :|64|: %1.00;0.45%"])))

(deftest inference_on_tense
  (is (derived "<(&/,<($x, key) --> hold>,i64) =/> <($x, room) --> enter>>. :|:"
               "<(John, key) --> hold>. :|:"
               ["<(John,room) --> enter>. :|64|: %1.0;0.81% "])))

(deftest inference_on_tense_nonvar
  (is (derived "<(&/,<(John, key) --> hold>,i64) =/> <(John, room) --> enter>>. :|:"
               "<(John, key) --> hold>. :|:"
               ["<(John,room) --> enter>. :|64|: %1.0;0.81%"])))


(deftest inference_on_tense_2
  (is (derived "<(&/,<($x, key) --> hold>,i64) =/> <($x, room) --> enter>>. :|:"
               "<(John,room) --> enter>. :|:"
               ["<(John, key) --> hold>. :|-64|: %1.0;0.45%"])))

;if there is a temporal difference then it is encoded in an interval, so =/> always comes with interval
;=|> occurs if both events are inside the cognitive duration
; TOOD taking into account that a sequence event can have a custom duration for refinement.

(deftest inference_on_tense_2_nonvar
  (is (derived "<(John,room) --> enter>. :|:"
                "<<(John,key) --> hold> =/> <(John,room) --> enter>>."
               ["<(John,key) --> hold>. :|0|: %1.00;0.45%"])))    ;just 0.30 because of eternalization

(deftest inference_on_tense_2_nonvar_2
  (is (derived "<(John,key) --> hold>. :|:"
               "<<(John,key) --> hold> =/> <(John,room) --> enter>>."
               ["<(*,John,room) --> enter>. :|0|: %1.0;0.81%"]))) ;just 0.43 because of eternalization

(deftest temporal_var_introduction_concurrent
  (is (derived "<John --> (/,open,_,door)>. :|:"
               "<John --> (/,enter,_,room)>. :|:"
               ["<<$1 --> (/,open,_,door)> =|> <$1 --> (/,enter,_,room)>>. :|: %1.0;0.45%"])))

(deftest temporal_var_introduction_after
  (is (derived "<John --> (/,enter,_,room)>. :|50|:"
               "<John --> (/,open,_,door)>. :|:"
               ["<(&/,<$1 --> (/,open,_,door)>,i50) =/> <$1 --> (/,enter,_,room)>>. :|50|: %1.0;0.45%"])))

(deftest temporal_var_elimination_on_events
  (is (derived "(&|,<(*,{t002},#2) --> on>,<(*,SELF,#2) --> at>). :|:"
               "<(&|,<(*,$1,#2) --> on>,<(*,SELF,#2) --> at>) =|> <(*,SELF,$1) --> reachable>>. :|:"
               ["<(*,SELF,{t002}) --> reachable>. :|: %1.0;0.81%"])))

(deftest temporal_order
  (is (derived "<<m --> M> =/> <p --> P>>."
               "<<s --> S> <|> <m --> M>>. %0.9;0.9%"
               ["<<s --> S> =/> <p --> P>>. %0.9;0.73%"])))

(deftest interval_preserve_shift_occurrence
  (is (derived "(&/,<s --> S>,i64,<y --> Y>,i8,<z --> Z>)!"
               "<s --> S>. :|10|:"
               ["(&/, <y --> Y>, i8, <z --> Z>)! :|74|: %1.0;0.81%"])))

(deftest interval_preserve_shift_occurrence_2
  (is (derived "(&/,<s --> S>,i64,<z --> Z>)! :|:"
               "<s --> S>. :|:"
               ["<z --> Z>! :|64|: %1.0;0.81%"])))

(deftest sub_condition_detachment
  (is (derived "<(&/,<(SELF,{t002}) --> reachable>,<({t002}) --> op_pick>) =/> <(SELF,{t002}) --> hold>>."
               "<(SELF,{t002}) --> reachable>. :|:"
               ;just to simulate single prem termlink selection
               ["<<({t002}) --> op_pick> =/> <(SELF, {t002}) --> hold>>. :|0|: %1.0;0.81%"]))) ;just 0.43 because of eternalization

;NAL8 testcases:

(deftest subgoal-deduction_1
  (is (derived "(&/,<(SELF,{t002}) --> hold>, <(SELF,{t001}) --> at>, <({t001}) --> op_open>)! :|:"
               "<(SELF,{t002}) --> hold>?"                  ;just to simulate single prem termlink selection
               ["<(SELF,{t002}) --> hold>! :|: %1.0;0.81%"])))

(deftest subgoal-deduction_2
  (is (derived "(&/,<(SELF,{t002}) --> reachable>, i16, <({t002}) --> op_pick>)! :|:"
               "<(SELF,{t002}) --> reachable>. :|:"
               ["<({t002}) --> op_pick>! :|16|: %1.0;0.81%"])))

(deftest subgoal-deduction_3
  (is (derived "(&/, <(SELF,{t002}) --> hold>, i16, <(SELF,{t001}) --> at>, <({t001}) --> op_open>)! :|:"
               "<(SELF,{t002}) --> hold>. :|:"
               ["(&/,<(SELF,{t001}) --> at>,<({t001}) --> op_open>)! :|16|: %1.0;0.81%"])))

(deftest goal-deduction-var
  (is (derived "<(SELF,{t001}) --> at>!"
               "<<($1) --> op_goto> =/> <(SELF,$1) --> at>>."
               ["<({t001}) --> op_goto>! %1.0;0.81%"])))

(deftest goal-deduction-non-var
  (is (derived "<(SELF,{t001}) --> at>!"
               "<<({t001}) --> op_goto> =/> <(SELF,{t001}) --> at>>."
               ["<({t001}) --> op_goto>! %1.0;0.81%"])))

(deftest goal-deduction-var
  (is (derived "<(SELF,{t001}) --> at>!"
               "<<($1) --> op_goto> =/> <(SELF,$1) --> at>>."
               ["<({t001}) --> op_goto>! %1.0;0.81%"])))

(deftest goal-abduction-var
  (is (derived "<({t001}) --> op_goto>!"
               "<<($1) --> op_goto> =/> <(SELF,$1) --> at>>."
               ["<(SELF,{t001}) --> at>! %1.0;0.45%"])))

(deftest goal-abduction-non-var
  (is (derived "<({t001}) --> op_goto>!"
               "<<({t001}) --> op_goto> =/> <(SELF,{t001}) --> at>>."
               ["<(SELF,{t001}) --> at>! %1.0;0.45%"])))
