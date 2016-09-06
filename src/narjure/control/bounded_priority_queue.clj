(ns narjure.control.bounded-priority-queue
  (:require [avl.clj :as avl]))

(defprotocol Bounded-Priority-Queue
  (push-element
    ; Adds element to queue, removes element with lowest priority if queue is full
    ; and returns tuple with removed element or nil if nothing is removed plus the new queue
    [_ element])
  (pop-element
    ; Returns tuple of element and new queue. If queue is empty returns tuple
    ; of nil and queue without any changes.
    [_])
  (count-elements [_]))

(defn el [id priority]
  {:id       id
   :priority priority})

(defrecord DefaultQueue [priority-index capacity]
  Bounded-Priority-Queue
  (push-element [_ element]
    "Adds an element to the queue and if over capcity removes lowest priority element.
     Returns tuple of element removed or nil and updated queue"
    ; add element to queue and get cnt
    (let [cnt (count priority-index)]
      (if (< cnt capacity)
        [nil (->DefaultQueue (persistent! (conj! (transient priority-index) element)) capacity)]
        (let [el-to-remove (nth priority-index (dec cnt))]
          (if (> (:priority el-to-remove) (:priority element))
            [nil (->DefaultQueue priority-index capacity)]
            (let [priority-index' (persistent! (disj! (transient priority-index) el-to-remove))]
              [el-to-remove (->DefaultQueue (persistent! (conj! (transient priority-index') element)) capacity)]))))))

  (pop-element [_]
    "Removes the highest priority element in the queue and returns
     a tuple with removed element and the udpated queue."
    (let [cnt (count priority-index)]
      (if (pos? cnt)
        (let [element (nth priority-index 0)
              priority-index' (persistent!
                                (disj!
                                  (transient priority-index) element))]
          [element ->DefaultQueue priority-index' capacity])
        [nil (->DefaultQueue priority-index capacity)])))

(count-elements [_] (count priority-index)))

(defn compare-elements
  "Compares elements. If priority of elements is equal, compares the hashes of
  the ids. It is done to allow bag to contain elements with equal priority."
  [{p1 :priority id1 :id}
   {p2 :priority id2 :id}]
  (if (= p1 p2)
    (> (hash id1) (hash id2))
    (> p1 p2)))

(defn default-queue
  ([] (default-queue 50))
  ([capacity]
   (->DefaultQueue (avl/sorted-set-by compare-elements) capacity)))

(defn test-fill [q n]
  (doseq [_ (range 0 50)]
    (reset! q (default-queue 1000))
    (doseq [i (range 0 n)]
      (let [[removed q'] (push-element @q (el (str "task" i) (rand)))]
        (reset! q q')))))
