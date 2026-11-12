(ns kbb.chat-test
  (:require [clojure.test :refer [deftest is]]
            [kbb.chat :as chat]))

(defn- pager
  "Fake fetch-page over ids newest first, `n` per page."
  [ids n]
  (let [pages (vec (partition-all n (map (fn [i] {:msg {:id i}}) (sort > ids))))]
    (fn [cursor]
      (let [i (or cursor 0)]
        {:messages (get pages i [])
         :pagination {:next (inc i) :last (>= (inc i) (count pages))}}))))

(defn- ids [entries] (map chat/entry-id entries))

(deftest fetch-new-test
  (is (= (range 1 11) (ids (chat/fetch-new (pager (range 1 11) 3) 0))))
  (is (= [8 9 10] (ids (chat/fetch-new (pager (range 1 11) 3) 7))))
  (is (= [6 7 8 9 10] (ids (chat/fetch-new (pager (range 1 11) 3) 5))))
  (is (= [] (ids (chat/fetch-new (pager (range 1 11) 3) 10))))
  (is (= [] (ids (chat/fetch-new (pager [] 3) 0)))))

(deftest rel-path-test
  (is (= "team/acme/general" (chat/rel-path {:name "acme" :members_type "team" :topic_name "general"})))
  (is (= "dm/alice,bob" (chat/rel-path {:name "alice,bob" :members_type "impteamnative"}))))
