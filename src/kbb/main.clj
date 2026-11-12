(ns kbb.main
  "CLI entry point."
  (:require [babashka.cli :as cli]
            [kbb.chat :as chat]
            [kbb.config :as config]
            [kbb.files :as files]
            [kbb.git :as git]
            [kbb.keybase :as kb]))

(def ^:private sections
  {"chat" {:backup chat/backup! :candidates (fn [_] (map :rel (chat/candidates)))}
   "files" {:backup files/backup! :candidates #(map :rel (files/candidates (or (:mount %) (kb/kbfs-mount))))}
   "git" {:backup git/backup! :candidates (fn [_] (map :rel (git/candidates)))}})

(defn- dry-run
  "Prints each candidate in `section`, marked + if selected and - if not."
  [section cfg]
  (doseq [rel ((get-in sections [section :candidates]) cfg)]
    (println (if (config/selected? cfg rel) "+" "-") section rel)))

(defn -main
  "Runs the named sections (default: every section with a non-empty :include)."
  [& args]
  (let [{:keys [opts args]} (cli/parse-args args {:coerce {:dry-run :boolean}})
        cfg (config/load-config (or (:config opts) config/default-path))
        target (or (:target opts) (:target cfg))
        names (or (seq args) (filter #(seq (get-in cfg [(keyword %) :include])) ["chat" "files" "git"]))]
    (when-let [bad (seq (remove sections names))]
      (binding [*out* *err*] (println "unknown section(s):" bad))
      (System/exit 2))
    (when-not (or target (:dry-run opts))
      (binding [*out* *err*] (println "no target: set :target in" (or (:config opts) config/default-path) "or pass --target"))
      (System/exit 2))
    (let [failures (reduce + (for [s names]
                               (if (:dry-run opts)
                                 (do (dry-run s (cfg (keyword s))) 0)
                                 ((get-in sections [s :backup]) target (cfg (keyword s))))))]
      (when (pos? failures)
        (binding [*out* *err*] (println failures "item(s) failed"))
        (System/exit 1)))))
