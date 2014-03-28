{:provided
 {:dependencies [[com.palletops/pallet-jclouds "1.7.0-alpha.2"]
                 [com.palletops/pallet-aws "0.2.1"]
                 [org.slf4j/jcl-over-slf4j "1.7.5"]]}
 :dev
 {:dependencies [[org.clojure/clojure "1.5.1"]
                 [com.palletops/pallet "0.8.0-RC.9" :classifier "tests"]
                 [com.palletops/crates "0.1.1"]
                 [ch.qos.logback/logback-classic "1.0.9"]]
  :plugins [[com.palletops/pallet-lein "0.8.0-alpha.1"]
            [com.palletops/lein-pallet-crate "0.1.0"]
            [lein-set-version "0.3.0"]
            [lein-resource "0.3.2"]
            [codox/codox.leiningen "0.6.4"]
            [lein-marginalia "0.7.1"]]
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
 :doc {:dependencies [[com.palletops/pallet-codox "0.1.0"]]
       :plugins [[codox/codox.leiningen "0.6.4"]
                 [lein-marginalia "0.7.1"]]
       :codox {:writer codox-md.writer/write-docs
               :output-dir "doc/0.8/api"
               :src-dir-uri "https://github.com/pallet/net-rules-crate/blob/develop"
               :src-linenum-anchor-prefix "L"}
       :aliases {"marg" ["marg" "-d" "doc/0.8/annotated"]
                 "codox" ["doc"]
                 "doc" ["do" "codox," "marg"]}}
 :release
 {:set-version
  {:updates [{:path "README.md" :no-snapshot true}]}}
 :jclouds {:dependencies [[com.palletops/pallet-jclouds "1.7.0-alpha.2"]
                          [org.apache.jclouds/jclouds-allblobstore "1.7.1"]
                          [org.apache.jclouds/jclouds-allcompute "1.7.1"]
                          [org.apache.jclouds.driver/jclouds-slf4j "1.7.1"
                           :exclusions [org.slf4j/slf4j-api]]
                          [org.apache.jclouds.driver/jclouds-sshj "1.7.1"]]}
 :pallet-aws {:dependencies [[com.palletops/pallet-aws "0.2.1"]
                             [org.slf4j/jcl-over-slf4j "1.7.5"]]}
 :vmfest {:dependencies [[com.palletops/pallet-vmfest "0.3.0-beta.2"]]}}
