(ns kbb.render-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is]]
            [kbb.render :as render]))

(defn- msg [id user type content]
  {:msg {:id id :sender {:username user} :sent_at_ms 0 :content {:type type (keyword type) content}}})

(def entries
  [(msg 1 "alice" "text" {:body "hello"})
   (msg 2 "bob" "text" {:body "typo"})
   (msg 3 "bob" "edit" {:messageID 2 :body "fixed"})
   (msg 4 "carol" "text" {:body "oops"})
   (msg 5 "carol" "delete" {:messageIDs [4]})
   (msg 6 "bob" "reaction" {:m 1 :b ":+1:"})
   (msg 7 "carol" "reaction" {:m 1 :b ":+1:"})
   (msg 8 "carol" "reaction" {:m 1 :b ":joy:"})
   (msg 9 "carol" "delete" {:messageIDs [8]})
   (msg 10 "alice" "attachment" {:title "" :object {:filename "pic.png" :mimeType "image/png"}})])

(deftest transcript-test
  (let [md (render/transcript {:name "acme" :topic_name "general"} entries)]
    (is (str/starts-with? md "# acme #general\n"))
    (is (str/includes? md "hello"))
    (is (str/includes? md "fixed _(edited)_"))
    (is (not (str/includes? md "typo")))
    (is (str/includes? md "_[deleted]_"))
    (is (not (str/includes? md "oops")))
    (is (str/includes? md "> :+1: bob, carol\n"))
    (is (not (str/includes? md ":joy:")))
    (is (str/includes? md "![pic.png](<attachments/10-pic.png>)"))))
