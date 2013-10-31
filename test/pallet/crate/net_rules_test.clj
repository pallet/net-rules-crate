(ns pallet.crate.net-rules-test
  (:require
   [clojure.test :refer :all]
   [pallet.build-actions :refer [build-actions]]
   [pallet.compute.jclouds :as jclouds]
   [pallet.compute.node-list :as node-list]
   [pallet.crate.net-rules :as net-rules]
   [pallet.live-test :as live-test]))

(deftest invoke-test
  (is (build-actions {}
        (net-rules/settings {})
        (net-rules/install {})
        (net-rules/permit-group :group 80 {})
        (net-rules/permit-role :role 80 {})
        (net-rules/permit-source "0.0.0.0/0" 80 {})
        (net-rules/configure {}))
      "no compute service")

  (is (build-actions
       {:server {:node (jclouds/make-node
                        "b"
                        :operating-system
                        {:family (jclouds/jvm-os-family-map "Linux")
                         :version "13.04"
                         :arch "x86_64"
                         :is_64bit true})}}
        (net-rules/settings {})
        (net-rules/install {})
        (net-rules/permit-group :group 80 {})
        (net-rules/permit-role :role 80 {})
        (net-rules/permit-source "0.0.0.0/0" 80 {})
        (net-rules/configure {}))
      "jclouds")

  (is (build-actions
       {:server {:node (node-list/make-node "b" "g" "127.0.0.0" :ubuntu
                                            :os-version "13.04")}}
        (net-rules/settings {})
        (net-rules/install {})
        (net-rules/permit-group :group 80 {})
        (net-rules/permit-role :role 80 {})
        (net-rules/permit-source "0.0.0.0/0" 80 {})
        (net-rules/configure {}))
      "nod-list"))
