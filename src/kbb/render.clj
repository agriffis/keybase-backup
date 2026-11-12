(ns kbb.render
  "Renders raw chat entries into a readable Markdown transcript."
  (:require [babashka.fs :as fs]
            [clojure.string :as str])
  (:import (java.time Instant ZoneId)
           (java.time.format DateTimeFormatter)))

(def ^:private fmt (DateTimeFormatter/ofPattern "yyyy-MM-dd HH:mm"))

(defn- timestamp
  "Formats epoch millis as local date and time."
  [ms]
  (.format fmt (.atZone (Instant/ofEpochMilli ms) (ZoneId/systemDefault))))

(defn attachment-file
  "Attachment path for `msg`, relative to the conversation dir."
  [msg]
  (let [fname (-> (get-in msg [:content :attachment :object :filename] "attachment")
                  fs/file-name
                  (str/replace #"^\.+" "_"))]
    (str "attachments/" (:id msg) "-" fname)))

(defn fold
  "Folds message summaries into `{:edits {id body} :deleted #{id} :reactions {id [[emoji user]]}}`."
  [msgs]
  (let [deleted (into #{} (mapcat #(get-in % [:content :delete :messageIDs])) msgs)]
    (reduce
     (fn [acc {:keys [id content sender]}]
       (case (:type content)
         "edit" (assoc-in acc [:edits (get-in content [:edit :messageID])] (get-in content [:edit :body]))
         "reaction" (if (deleted id)
                      acc
                      (update-in acc [:reactions (get-in content [:reaction :m])]
                                 (fnil conj []) [(get-in content [:reaction :b]) (:username sender)]))
         acc))
     {:edits {} :deleted deleted :reactions {}}
     msgs)))

(defn- body
  "Markdown body for a displayable message, or nil to omit it."
  [{:keys [id content] :as msg} {:keys [edits deleted]}]
  (let [c ((keyword (:type content)) content)]
    (cond
      (deleted id) "_[deleted]_"
      :else
      (case (:type content)
        "text" (if-let [e (edits id)] (str e " _(edited)_") (:body c))
        "attachment" (let [o (:object c)
                           path (attachment-file msg)
                           label (if (str/blank? (:title c)) (:filename o) (:title c))]
                       (if (str/starts-with? (str (:mimeType o)) "image/")
                         (str "![" label "](<" path ">)")
                         (str "[" label "](<" path ">)")))
        "headline" (str "_set the channel description: " (:headline c) "_")
        "metadata" (str "_set the conversation title: " (:conversationTitle c) "_")
        "join" "_joined_"
        "leave" "_left_"
        "system" (str "_system message (type " (:systemType c) ")_")
        nil))))

(defn transcript
  "Renders raw API `entries` (ascending) for `channel` as Markdown."
  [channel entries]
  (let [msgs (keep :msg entries)
        folded (fold msgs)]
    (str "# " (:name channel) (when-let [t (:topic_name channel)] (str " #" t)) "\n"
         (str/join
          (for [{:keys [id sender sent_at_ms] :as m} msgs
                :let [b (body m folded)]
                :when b]
            (str "\n**" (:username sender) "** · " (timestamp sent_at_ms) "\n"
                 b "\n"
                 (when-let [rs (seq (get-in folded [:reactions id]))]
                   (str "\n> " (str/join " · " (for [[emoji users] (group-by first rs)]
                                                 (str emoji " " (str/join ", " (map second users)))))
                        "\n"))))))))
