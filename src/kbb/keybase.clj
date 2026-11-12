(ns kbb.keybase
  "Thin wrappers around the keybase CLI."
  (:require [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]))

(defn chat-api
  "Sends request map `m` to `keybase chat api` and returns its `:result`.
  Throws on an `:error` reply, since the CLI exits 0 even then."
  [m]
  (let [r (-> (p/shell {:out :string :in (json/generate-string m)} "keybase" "chat" "api")
              :out
              (json/parse-string true))]
    (when-let [e (:error r)]
      (throw (ex-info (str "keybase chat api " (:method m) ": " (:message e)) {:error e})))
    (:result r)))

(defn kbfs-mount
  "Returns the KBFS mount directory from `keybase status --json`."
  []
  (-> (p/shell {:out :string} "keybase" "status" "--json")
      :out
      (json/parse-string true)
      (get-in [:KBFS :Mount])))

(defn git-repos
  "Parses `keybase git list` into maps of `:rel` (e.g. \"team/foo/repo\") and `:url`."
  []
  (->> (str/split-lines (:out (p/shell {:out :string} "keybase" "git" "list")))
       (keep #(re-matches #"\s+(\S+)\s+(keybase://(personal|team)/\S+)" %))
       (map (fn [[_ name url kind]]
              {:rel (str kind "/" (if (= kind "personal") (last (str/split name #"/")) name))
               :url url}))))
