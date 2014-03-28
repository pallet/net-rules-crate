(ns pallet.crate.net-rules.pallet-ec2
  "Implementation of net-rules for pallet-ec2 provider.  You will need
a dependency on pallet-aws 0.2.0 or greater to use this
namespace."
 (:require
  [clojure.tools.logging :refer [debugf]]
  [com.palletops.awaze.ec2 :as ec2]
  [pallet.compute.ec2 :as pallet-ec2]
  [pallet.crate :refer [group-name targets-in-group targets-with-role target]]
  [pallet.crate.net-rules :refer [configure-net-rules install-net-rules]]
  [pallet.node :as node]))

(defmethod install-net-rules :pallet-ec2
  [_ _])

(defn sg-name [group]
  (pallet-ec2/security-group-name {:group-name group}))

(defn target-region []
  (:location (.info (:node (target)))))

(defn compute-service []
  (node/compute-service (:node (target))))

(defn authorize
  "Authorize a cidr"
  [compute sg-name port & {:keys [protocol ip-range region]}]
  (debugf "authorize %s %s %s" sg-name port ip-range)
  (try
    (pallet-ec2/execute
     compute
     ec2/authorize-security-group-ingress-map
     {:group-name sg-name
      :from-port port
      :to-port port
      :ip-protocol (name protocol)
      :cidr-ip ip-range})
    (catch Exception e
      (if (re-find #"has already been authorized" (.getMessage e))
        (debugf "Already authorized")
        (throw e)))))

(defn authorize-targets
  "Authorize a port to a sequence of nodes"
  [compute sg-name region {:keys [port protocol]} targets]
  (debugf "authorize-targets %s %s %s" sg-name port (count targets))
  (doseq [target-map targets
          :let [ip-range (str (or (node/private-ip (:node target-map))
                                  (node/primary-ip (:node target-map)))
                              "/32")]]
    (try
      (pallet-ec2/execute
       compute
       ec2/authorize-security-group-ingress-map
       {:group-name sg-name
        :from-port port
        :to-port port
        :ip-protocol (name protocol)
        :cidr-ip ip-range})
      (catch com.amazonaws.AmazonServiceException e
        (debugf e "Auth ingress failed")
        (throw e)))))

(defmethod configure-net-rules :pallet-ec2
  [_ permissions]
  (let [compute (compute-service)
        sg-name (sg-name (group-name))
        region (target-region)]
    (debugf "configure-net-rules %s" (pr-str permissions))
    (doseq [{:keys [cidr group role port protocol] :as permission} permissions]
      (cond
       cidr (authorize compute sg-name port :protocol protocol
                       :ip-range cidr :region region)
       group (authorize-targets compute sg-name region permission
                                (targets-in-group group))
       role (authorize-targets compute sg-name region permission
                               (targets-with-role role))))))
