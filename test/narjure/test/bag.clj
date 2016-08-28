(ns narjure.test.bag
  (:require [clojure.test :refer :all]
            [narjure.control.bag :as b]))

(deftest test-default-bag
  (let [n 10
        elements (mapv (fn [id] {:priority (rand)
                                 :id       id})
                       (range n))
        bag (reduce b/add-element (b/default-bag n) elements)
        min-priority (apply min (map :priority elements))
        max-priority (apply max (map :priority elements))
        max-idx (:id (last (sort-by :priority elements)))
        [element bag] (b/pop-element bag)]
    (is (= (:priority element) min-priority))
    (is (= (dec n) (b/count-elements bag)))
    (is (= (get-in (b/get-by-index bag 0) [0 :priority]) max-priority))
    (is (= (get-in (b/get-by-id bag max-idx) [0 :priority]) max-priority))
    (is (= 10 (b/count-elements (b/add-element bag {:id       1000
                                                    :priority (rand)}))))
    (let [bag (reduce b/add-element (b/default-bag) [{:id 1 :priority 1}
                                                     {:id 1 :priority 1}])]
      (is (= 1 (b/count-elements bag))))
    (let [bag (reduce b/update-element (b/default-bag) [{:id 1 :priority 1}
                                                        {:id 1 :priority 1}])]
      (is (= {:id 1 :priority 1}
             (first (b/get-by-id bag 1))
             (first (b/get-by-index bag 0)))))
    (let [bag (reduce b/add-element (b/default-bag) [{:id 1 :priority 1}
                                                     {:id 1 :priority 0.7}])]
      (is (= 1 (b/count-elements bag))))
    (let [bag (reduce b/add-element (b/default-bag) [{:id 1 :priority 0.7}
                                                     {:id 2 :priority 0.7}])]
      (is (= 2 (b/count-elements bag))))

    (let [id 123
          element {:id id :priority 1}
          bag (-> (b/pop-element bag)
                  second
                  (b/add-element {:id id :priority 0.9})
                  (b/update-element element))]
      (is (= (first (b/get-by-id bag 123)) element)))
    (is (thrown? IndexOutOfBoundsException
                 (b/get-by-index (b/default-bag) 1)))))


(comment
  (def db (b/default-bag))
  (b/update-element db {:id 1 :priority 1})

  (doseq [n [10 100 1000 10000 100000 1000000]]
    (time (mapv (fn [_]
                  (doall (b/update-element db {:id 1 :priority 1
                                               :ok (rand)})))
                (range 100000)))))
