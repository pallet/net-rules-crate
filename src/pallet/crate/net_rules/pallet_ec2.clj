(ns pallet.crate.net-rules.pallet-ec2
  "Implementation of net-rules for pallet-ec2 provider.  You will need
a dependency on pallet-aws 0.2.0 or greater to use this
namespace."
  (:require
   [clojure.stacktrace :refer [root-cause]]
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

(defn sg-id-for [compute vpc-id sg-name]
  (let [filters [{:name "group-name" :values [sg-name]}]
        filters (if vpc-id
                  (conj filters {:name "vpc-id" :values [vpc-id]})
                  filters)
        {:keys [security-groups] :as sgs}
        (pallet-ec2/execute
         compute ec2/describe-security-groups-map {:filters filters})
        matching-group (->> security-groups
                            (filter #(and
                                      (= vpc-id (:vpc-id %))
                                      (= sg-name (:group-name %))))
                            first)]
    (debugf "sg-id-for %s %s %s" vpc-id sg-name (pr-str security-groups))
    (:group-id matching-group)))

(defn authorize
  "Authorize a cidr"
  [compute sg-id port & {:keys [protocol ip-range region]}]
  (debugf "authorize %s %s %s" sg-name port ip-range)
  (try
    (pallet-ec2/execute
     compute
     ec2/authorize-security-group-ingress-map
     {:group-id sg-id
      :ip-permissions [{:from-port port
                        :to-port port
                        :ip-protocol (name protocol)
                        :ip-ranges [ip-range]}]})
    (catch Exception e
      (let [c (or (root-cause e) e)]
        (if (and (instance? com.amazonaws.AmazonServiceException c)
                 (= (.getErrorCode ^com.amazonaws.AmazonServiceException c)
                    "InvalidPermission.Duplicate"))
          (debugf "Already authorized")
          (throw (ex-info (format
                           "Failed to configure net-rules: %s %s"
                           (type c)
                           (try (bean c) (catch Exception _)))
                          {:type (type c)
                           :bean (try (bean c) (catch Exception _))}
                          e)))))))

(defn authorize-targets
  "Authorize a port to a sequence of nodes"
  [compute sg-id region {:keys [port protocol]} targets]
  (debugf "authorize-targets %s %s %s" sg-id port (count targets))
  (doseq [target-map targets
          :let [ip-range (str (or (node/private-ip (:node target-map))
                                  (node/primary-ip (:node target-map)))
                              "/32")]]
    (try
      (let [r (pallet-ec2/execute
               compute
               ec2/authorize-security-group-ingress-map
               {:group-id sg-id
                :ip-permissions [{:from-port port
                                  :to-port port
                                  :ip-protocol (name protocol)
                                  :ip-ranges [ip-range]}]})]
        (debugf "authorize-targets %s" r))
      (catch Exception e
        (let [c (root-cause e)]
          (if (and (instance? com.amazonaws.AmazonServiceException c)
                   (= (.getErrorCode ^com.amazonaws.AmazonServiceException c)
                      "InvalidPermission.Duplicate"))
            (debugf "Already authorized")
            (ex-info "Failed to configure net-rules" {} e)))))))

(defmethod configure-net-rules :pallet-ec2
  [_ permissions]
  (let [compute (compute-service)
        sg-name (sg-name (group-name))
        region (target-region)
        vpc-id (:vpc-id (.info (:node (target))))
        sg-id (sg-id-for compute vpc-id sg-name)]
    (debugf "configure-net-rules %s vpc-id %s sg-name %s sg-id %s"
            (pr-str permissions) vpc-id sg-name sg-id)
    (doseq [{:keys [cidr group role port protocol] :as permission} permissions]
      (cond
       cidr (authorize compute sg-id
                       port :protocol protocol
                       :ip-range cidr :region region)
       group (authorize-targets compute sg-id region permission
                                (targets-in-group group))
       role (authorize-targets compute sg-id region permission
                               (targets-with-role role))))))
