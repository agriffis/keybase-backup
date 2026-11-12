(ns kbb.config
  "Config loading and include/exclude matching."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn])
  (:import (java.nio.file FileSystems Path)))

(def default-path
  "Default config file location."
  (str (fs/path (fs/xdg-config-home) "keybase-backup" "config.edn")))

(def defaults
  "Defaults merged under the user's config. Files are opt-in because KBFS can be huge."
  {:chat {:include ["**"] :exclude [] :attachments true}
   :files {:include [] :exclude []}
   :git {:include ["**"] :exclude []}})

(defn load-config
  "Reads the EDN config at `path` (if it exists) and deep-merges it over `defaults`."
  [path]
  (let [user (when (fs/exists? path) (edn/read-string (slurp path)))]
    (merge-with #(if (map? %1) (merge %1 %2) %2) defaults user)))

(defn- glob-matches?
  "True if `rel-path` matches the java.nio glob `pattern`."
  [pattern rel-path]
  (.matches (.getPathMatcher (FileSystems/getDefault) (str "glob:" pattern))
            (Path/of rel-path (make-array String 0))))

(defn selected?
  "True if `rel-path` matches any `:include` glob and no `:exclude` glob."
  [{:keys [include exclude]} rel-path]
  (and (some #(glob-matches? % rel-path) include)
       (not (some #(glob-matches? % rel-path) exclude))))
