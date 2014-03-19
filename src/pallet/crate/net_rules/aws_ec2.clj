(ns pallet.crate.net-rules.aws-ec2
  "Implementation of net-rules for ec2.  You will need a dependency on
pallet-jclouds 1.7.0 or greater to use this namespace."
 (:require
  [clojure.tools.logging :refer [debugf]]
  [pallet.crate :refer [group-name targets-in-group targets-with-role target]]
  [pallet.crate.net-rules :refer [configure-net-rules install-net-rules]]
  [pallet.node :as node]
  [com.palletops.jclouds.ec2.security-group2 :as sg2]
  [com.palletops.jclouds.ec2.core :refer [get-region]]))

(defmethod install-net-rules :aws-ec2
  [_ _])

(defn sg-name [group]
  (str "jclouds#" (name group)))

(defn target-region []
  (get-region (:node (target))))

(defn compute-service []
  (node/compute-service (:node (target))))

(defn authorize
  "Wrapper for sg2/authorize that adds logging an exception handling."
  [compute sg-name port & {:keys [protocol ip-range region]}]
  (debugf "authorize %s %s %s" sg-name port ip-range)
  (try
    (sg2/authorize compute sg-name port :protocol protocol :ip-range ip-range
                   :region (target-region))
    (catch IllegalStateException e
      (if (re-find #"has already been authorized" (.getMessage e))
        (debugf "Already authorized")
        (throw e)))))

(defn authorize-targets
  "Authorize a port to a sequence of nodes"
  [compute sg-name region {:keys [port protocol]} targets]
  (doseq [target-map targets
          :let [ip-range (str (or (node/private-ip (:node target-map))
                                  (node/primary-ip (:node target-map)))
                              "/32")]]
    (authorize compute sg-name port :protocol protocol :ip-range ip-range
               :region (target-region))))

(defmethod configure-net-rules :aws-ec2
  [_ permissions]
  (let [compute (compute-service)
        sg-name (sg-name (group-name))
        region (target-region)]
    (doseq [{:keys [cidr group role port protocol] :as permission} permissions]
      (cond
       cidr (authorize compute sg-name port :protocol protocol
                       :ip-range cidr :region region)
       group (authorize-targets compute sg-name region permission
                                (targets-in-group group))
       role (authorize-targets compute sg-name region permission
                               (targets-with-role role))))))
