(ns pallet.crate.net-rules.aws-ec2-test
  (:require
   [clojure.test :refer :all]
   [pallet.crate.net-rules.aws-ec2 :refer :all]))

(deftest authorize-targets-test
  ;; TODO actually test something other than just compilation
  (is authorize-targets))
