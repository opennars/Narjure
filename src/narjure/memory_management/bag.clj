(ns narjure.memory_management.bag
  (:require [avl.clj :as avl]
            [co.paralleluniverse.pulsar
             [actors :refer [shutdown!]]
             [core :refer [join]]]))

(defprotocol Bag
  (add-element
    ; Adds element to bag, removes element with lowest priority if bag is full.
    [_ element])
  (get-by-index
    ; Returns tuple of element and updated bag. If element with such index
    ; doesn't exist throws IndexOutOfBoundsException.
    [_ index])
  (lookup-by-index
    ; Returns element without changing bag. If element with such index
    ; doesn't exist throws IndexOutOfBoundsException.
    [_ index])
  (get-by-id
    ; Returns tuple of element and updated bag. If element with such id
    ; doesn't exist returns tuple of nil and bag without any changes.
    [_ id])
  (pop-element
    ; Returns tuple of element and updated bag. If bag is empty returns tuple
    ; of nil and bag without any changes.
    [_])
  (update-element
    ; Replaces entrie with the same id.
    [_ element])
  (count-elements [_])
  (exists?
    ; Returns true if bag contains element with id.
    [_ id]))

(defn el [id priority]
  {:id       id
   :priority priority})

;; DefaultBag consist of the next elements:
;; 1) priority-index. Sortet set which contains entries like
;; {:id 1 :priority 0.9}, sotred by priority. It provides access by index,
;; and is used to find id of entry by its index
;; 2) elements-map contains all elements mapped by their ids
;; 3) capacity. Limit of elemts inside the bag, when bag is full elements
;; with lowest priority will be removed on addition
(defrecord DefaultBag [priority-index elements-map capacity]
  Bag
  (add-element [bag {:keys [id priority] :as element}]
    "Adds a bag element to the bag"
    (let [cnt (count priority-index)]
      (if (exists? bag id)
        (update-element bag element)
        (if (>= cnt capacity)
          (if (<= (:priority (nth priority-index (dec cnt)));if same priority, still prefer the new one.
                  priority)                                ;if new element has lower priority than the lowest,
            (let [[_ bag'] (pop-element bag)]               ;then don't even attempt to add the new element.
              (add-element bag' element))
            bag)
          (let [priority-index' (conj priority-index (el id priority))
                element-map' (assoc elements-map id element)]
            (->DefaultBag priority-index' element-map' capacity))))))

  (get-by-index [_ index]
    "Returns an element by its index"
    (let [{:keys [id] :as element} (nth priority-index index)
          priority-index' (disj priority-index element)
          element' (elements-map id)
          element-map' (dissoc elements-map id)]
      [element' (->DefaultBag priority-index' element-map' capacity)]))

  (lookup-by-index [_ index]
    (let [{:keys [id] :as element} (nth priority-index index)
          element' (elements-map id)]
      [element']))

  (get-by-id [bag id]
    "returns an element by the element id"
    (if-let [{:keys [priority] :as element'} (elements-map id)]
      (let [priority-index' (disj priority-index (el id priority))
            element-map' (dissoc elements-map id)]
        [element' (->DefaultBag priority-index' element-map' capacity)])
      [nil bag]))

  (pop-element [bag]
    "Removes the priority-wise worst element in the bag (returning a new bag of course)."
    (let [cnt (count priority-index)]
      (if (pos? cnt)
        (let [{:keys [id] :as element} (nth priority-index (dec cnt))
              priority-index' (disj priority-index element)
              element' (elements-map id)
              ref (:ref element')
              elements-map' (dissoc elements-map id)]
          (elements-map id)
          (when ref ;TODO disable logging for this actor so that this can be enabled again
            (shutdown! ref))             ;shutdown concept actor
          [element' (->DefaultBag priority-index' elements-map' capacity)])
        [nil bag])))

  (update-element [_ {priority' :priority
                      :keys     [id]
                      :as       element'}]
    "Updating an existing item in the bag."
    (let [priority (get-in elements-map [id :priority])
          elements-map' (assoc elements-map id element')]
      (if (= priority' priority)
        (->DefaultBag priority-index elements-map' capacity)
        (let [priority-index' (-> priority-index
                                  (disj (el id priority))
                                  (conj (el id priority')))]
          (->DefaultBag priority-index' elements-map' capacity)))))

  (count-elements [_] (count priority-index))

  (exists? [_ id] (contains? elements-map id)))

(defn compare-elements
  "Compares elements. If priority of elements is equal, compares the hashes of
  the ids. It is done to allow bag to contain elements with equal priority."
  [{p1 :priority id1 :id}
   {p2 :priority id2 :id}]
  (if (= p1 p2)
    (> (hash id1) (hash id2))
    (and (> p1 p2) (not= id1 id2))))

(defn default-bag
  ([] (default-bag 50))
  ([capacity]
   (->DefaultBag (avl/sorted-set-by compare-elements) {} capacity)))

