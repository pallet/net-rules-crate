;;; Pallet project configuration file

(require
 '[pallet.crate.net-rules-test :refer [test-spec]]
 '[pallet.crates.test-nodes :refer [node-specs]])

(defproject net-rules-crate
  :provider node-specs                  ; supported pallet nodes
  :groups [(group-spec "net-rules-live-test"
             :extends [with-automated-admin-user
                       test-spec]
             :roles #{:live-test :default})])
