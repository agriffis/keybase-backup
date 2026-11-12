(ns kbb.config-test
  (:require [clojure.test :refer [deftest is]]
            [kbb.config :as config]))

(deftest selected?-test
  (let [cfg {:include ["**" "dm/*"] :exclude ["team/big/**"]}]
    (is (config/selected? cfg "team/acme/general"))
    (is (config/selected? cfg "dm/alice,bob"))
    (is (not (config/selected? cfg "team/big/general"))))
  (is (not (config/selected? {:include [] :exclude []} "private/alice")))
  (is (config/selected? {:include ["private/*"]} "private/alice"))
  (is (not (config/selected? {:include ["private/*"]} "team/acme"))))
