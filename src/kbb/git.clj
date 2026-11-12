(ns kbb.git
  "Mirror clones of Keybase-hosted git repos."
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [kbb.config :as config]
            [kbb.keybase :as kb]))

(defn candidates
  "Keybase git repos as `{:rel :url}`."
  []
  (kb/git-repos))

(defn backup!
  "Clones or updates selected repos as bare mirrors under `target`/git.
  Returns the number of failures."
  [target cfg]
  (count
   (for [{:keys [rel url]} (candidates)
         :when (config/selected? cfg rel)
         :let [dir (str (fs/path target "git" (str rel ".git")))
               {:keys [exit]} (if (fs/exists? dir)
                                (p/shell {:continue true} "git" "-C" dir "remote" "update" "--prune")
                                (do (fs/create-dirs (fs/parent dir))
                                    (p/shell {:continue true} "git" "clone" "--mirror" "--quiet" url dir)))]
         :when (do (println "git" rel (if (zero? exit) "ok" (str "FAILED (exit " exit ")")))
                   (not (zero? exit)))]
     rel)))
