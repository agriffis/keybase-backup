(ns kbb.files
  "KBFS backup via rsync from the FUSE mount."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [kbb.config :as config]
            [kbb.keybase :as kb]))

(defn candidates
  "Top-level KBFS folders (e.g. \"private/alice\", \"team/acme\"), each as `{:rel :src}`."
  [mount]
  (for [kind ["private" "public" "team"]
        d (fs/list-dir (fs/path mount kind))
        :when (fs/directory? d)]
    {:rel (str kind "/" (fs/file-name d)) :src (str d)}))

(defn backup!
  "Rsyncs selected KBFS folders to `target`/files. Never deletes from the backup.
  Returns the number of failures."
  [target cfg]
  (let [mount (or (:mount cfg) (kb/kbfs-mount))]
    (count
     (for [{:keys [rel src]} (candidates mount)
           :when (config/selected? cfg rel)
           :let [dest (str (fs/path target "files" rel))
                 _ (fs/create-dirs dest)
                 {:keys [exit]} (p/shell {:continue true} "rsync" "-a" "--partial" (str src "/") (str dest "/"))]
           :when (do (println "files" rel (if (zero? exit) "ok" (str "FAILED (rsync exit " exit ")")))
                     (not (zero? exit)))]
       rel))))
