(ns kbb.chat
  "Incremental chat backup: one directory per conversation holding an
  append-only messages.jsonl, attachments, and a rendered transcript.md."
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [clojure.string :as str]
            [kbb.config :as config]
            [kbb.keybase :as kb]
            [kbb.render :as render]))

(defn rel-path
  "Backup-relative path for a conversation's `channel` map."
  [{:keys [name members_type topic_name]}]
  (if (= members_type "team")
    (str "team/" name "/" topic_name)
    (str "dm/" name)))

(defn entry-id
  "Message id of a raw API entry, or nil for unboxing-error entries."
  [entry]
  (get-in entry [:msg :id]))

(defn fetch-new
  "Pages backward through a conversation via `(fetch-page cursor)`, which returns
  `{:messages [...] :pagination {...}}` newest first, until reaching `last-id`.
  Returns entries with id > `last-id`, ascending."
  [fetch-page last-id]
  (loop [cursor nil acc []]
    (let [{:keys [messages pagination]} (fetch-page cursor)
          ids (keep entry-id messages)
          acc (into acc (filter #(some-> (entry-id %) (> last-id))) messages)]
      (if (or (:last pagination) (empty? messages) (some #(<= % last-id) ids))
        (vec (sort-by entry-id acc))
        (recur (:next pagination) acc)))))

(defn- api-pager
  "Returns a fetch-page fn for `conv-id`. Uses peek so the backup doesn't mark messages read."
  [conv-id]
  (fn [cursor]
    (kb/chat-api {:method "read"
                  :params {:options {:conversation_id conv-id
                                     :peek true
                                     :pagination (cond-> {:num 500} cursor (assoc :next cursor))}}})))

(defn- read-entries
  "Reads all entries from a messages.jsonl file, or [] if absent."
  [file]
  (if (fs/exists? file)
    (mapv #(json/parse-string % true) (fs/read-all-lines file))
    []))

(defn- spit-atomic
  "Writes `content` to `file` via a temp file and rename."
  [file content]
  (let [tmp (str file ".tmp")]
    (spit tmp content)
    (fs/move tmp file {:replace-existing true :atomic-move true})))

(defn- download-missing!
  "Downloads attachments in `entries` that have neither a file nor a .failed marker.
  A failure writes the marker so a permanently gone attachment isn't retried every run.
  Returns the number downloaded."
  [conv-id dir entries]
  (fs/create-dirs (fs/path dir "attachments"))
  (count
   (for [{:keys [msg]} entries
         :when (= "attachment" (get-in msg [:content :type]))
         :let [f (str (fs/path dir (render/attachment-file msg)))
               part (str f ".part")]
         :when (not (or (fs/exists? f) (fs/exists? (str f ".failed"))))
         :when (try
                 (kb/chat-api {:method "download"
                               :params {:options {:conversation_id conv-id
                                                  :message_id (:id msg)
                                                  :output part}}})
                 (fs/move part f {:replace-existing true})
                 true
                 (catch Exception e
                   (spit (str f ".failed") (ex-message e))
                   (binding [*out* *err*] (println "  attachment" (:id msg) "failed:" (ex-message e)))
                   false))]
     f)))

(defn- existing-dirs
  "Maps conversation id to its current directory by scanning conv.json files."
  [chat-root]
  (into {}
        (for [f (fs/glob chat-root "**/conv.json")]
          [(:id (json/parse-string (slurp (str f)) true)) (str (fs/parent f))])))

(defn- place-dir!
  "Returns the directory for `conv`, moving an existing one if the conversation was renamed.
  Throws if the path is already used by a different conversation."
  [chat-root by-id {:keys [id channel]}]
  (let [dir (str (fs/path chat-root (rel-path channel)))
        old (by-id id)
        conv-json (fs/path dir "conv.json")]
    (cond
      (and old (not= old dir) (not (fs/exists? dir)))
      (do (fs/create-dirs (fs/parent dir))
          (fs/move old dir)
          (println "chat renamed" old "->" dir))

      (and (fs/exists? conv-json)
           (not= id (:id (json/parse-string (slurp (str conv-json)) true))))
      (throw (ex-info (str dir " belongs to a different conversation") {:id id})))
    dir))

(defn backup-conv!
  "Backs up one conversation incrementally. Returns a summary string."
  [chat-root by-id cfg {:keys [id channel] :as conv}]
  (let [dir (place-dir! chat-root by-id conv)
        jsonl (str (fs/path dir "messages.jsonl"))
        _ (fs/create-dirs dir)
        old (read-entries jsonl)
        last-id (or (some->> (seq (keep entry-id old)) (apply max)) 0)
        raw (fetch-new (api-pager id) last-id)
        new (filterv entry-id raw)
        all (into old new)]
    (when (< (count new) (count raw))
      (binding [*out* *err*] (println "  skipped" (- (count raw) (count new)) "undecryptable entries")))
    (spit (str (fs/path dir "conv.json"))
          (json/generate-string {:id id :channel channel} {:pretty true}))
    (when (seq new)
      (spit-atomic jsonl (str/join (map #(str (json/generate-string %) "\n") all))))
    (let [n-att (if (:attachments cfg) (download-missing! id dir all) 0)]
      (when (or (seq new) (pos? n-att) (not (fs/exists? (fs/path dir "transcript.md"))))
        (spit-atomic (str (fs/path dir "transcript.md")) (render/transcript channel all)))
      (format "+%d msgs, %d attachments" (count new) n-att))))

(defn candidates
  "Inbox conversations, each with its `:rel` path."
  []
  (for [c (:conversations (kb/chat-api {:method "list"}))]
    (assoc c :rel (rel-path (:channel c)))))

(defn backup!
  "Backs up selected conversations under `target`. Returns the number of failures."
  [target cfg]
  (let [chat-root (str (fs/path target "chat"))
        by-id (existing-dirs chat-root)]
    (count
     (for [c (candidates)
           :when (config/selected? cfg (:rel c))
           :let [ok? (try
                       (println "chat" (:rel c) (backup-conv! chat-root by-id cfg c))
                       true
                       (catch Exception e
                         (binding [*out* *err*] (println "chat" (:rel c) "FAILED:" (ex-message e)))
                         false))]
           :when (not ok?)]
       c))))
