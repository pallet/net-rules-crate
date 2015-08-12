{:provided
 {:dependencies [[com.palletops/pallet-jclouds "1.7.0-alpha.2"]
                 [com.palletops/pallet-aws "0.2.1"]
                 [org.slf4j/jcl-over-slf4j "1.7.5"]]}
 :dev
 {:dependencies [[org.clojure/clojure "1.5.1"]
                 [com.palletops/pallet "0.8.4" :classifier "tests"]
                 [com.palletops/crates "0.1.1"]
                 [ch.qos.logback/logback-classic "1.0.9"]]
  :plugins [[com.palletops/pallet-lein "0.8.0-alpha.1"]
            [com.palletops/lein-pallet-crate "0.1.0"]
            [lein-resource "0.3.2"]
            [lein-pallet-release "RELEASE"]]
  :aliases {"live-test-up"
            ["pallet" "up"
             ;; "--phases" "install,configure,test"
             ;; "--selector" "live-test"
             ]
            "live-test-down" ["pallet" "down"]
            "live-test" ["do" "live-test-up," "live-test-down"]}
  :test-selectors {:default (complement :live-test)
                   :live-test :live-test
                   :all (constantly true)}
  :checkout-deps-shares ^:replace [:source-paths :test-paths
                                   :compile-path]}
 :no-checkouts {:checkout-deps-shares ^{:replace true} []}}
