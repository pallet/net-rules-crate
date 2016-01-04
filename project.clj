(defproject com.palletops/net-rules-crate "0.8.0-beta.1"
  :description "Crate for controlling network port access"
  :url "http://github.com/pallet/net-rules-crate"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.palletops/pallet "0.8.4"]
                 [prismatic/schema "0.2.1"]]
  :resource {:resource-paths ["doc-src"]
             :target-path "target/classes/pallet_crate/net_rules_crate/"
             :includes [#"doc-src/USAGE.*"]}
  :prep-tasks ["resource" "crate-doc"])
