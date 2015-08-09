(ns pallet.crate.net-rules.pallet-ec2
  "Implementation of net-rules for pallet-ec2 provider.  You will need
a dependency on pallet-aws 0.2.0 or greater to use this
namespace."
  (:require
   [clojure.stacktrace :refer [root-cause]]
   [clojure.tools.logging :refer [debugf tracef]]
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

(defn sg-info [compute vpc-id sg-name]
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
    {:group-id (:group-id matching-group)
     :ip-permissions
     (->> (:ip-permissions matching-group)
          (filter :ip-ranges)
          (mapv #(select-keys
                 % [:to-port :from-port :ip-protocol :ip-ranges])))}))

(defn authorize
  "Authorize a cidr"
  [compute sg-id permissions]
  {:pre [sg-id permissions]}
  (debugf "authorize %s %s" sg-id permissions)
  (try
    (pallet-ec2/execute
     compute
     ec2/authorize-security-group-ingress-map
     {:group-id sg-id
      :ip-permissions permissions})
    (catch Exception e
      (let [c (or (root-cause e) e)]
        (if (and (instance? com.amazonaws.AmazonServiceException c)
                 (= (.getErrorCode ^com.amazonaws.AmazonServiceException c)
                    "InvalidPermission.Duplicate"))
          (debugf "Already authorized")
          (throw (ex-info (format
                           "Failed to authorize net-rules permissions: %s %s"
                           (type c)
                           (try (bean c) (catch Exception _)))
                          {:type (type c)
                           :bean (try (bean c) (catch Exception _))}
                          e)))))))

(defn revoke
  "Revoke permissions"
  [compute sg-id permissions]
  {:pre [sg-id permissions]}
  (debugf "revoke %s %s" sg-id permissions)
  (try
    (pallet-ec2/execute
     compute
     ec2/revoke-security-group-ingress-map
     {:group-id sg-id
      :ip-permissions permissions})
    (catch Exception e
      (let [c (or (root-cause e) e)]
        (throw (ex-info (format
                         "Failed to revoke net-rules permissions: %s %s"
                         (type c)
                         (try (bean c) (catch Exception _)))
                        {:type (type c)
                         :bean (try (bean c) (catch Exception _))}
                        e))))))

(defn ip-permissions
  "Convert a spec to an EC2 Ip permissions map."
  [{:keys [port protocol ip-range]}]
  {:from-port port
   :to-port port
   :ip-protocol (name protocol)
   :ip-ranges [ip-range]})

(defn target-cidr
  "Convert a target map to a cidr string"
  [target-map]
  (str (or (node/private-ip (:node target-map))
           (node/primary-ip (:node target-map)))
       "/32"))

(defn target-permissions
  "Authorize a port to a sequence of nodes"
  [{:keys [port protocol] :as permission} targets]
  (debugf "target-permissions %s %s" port (count targets))
  (vec
   (for [target-map targets
         :let [ip-range (target-cidr target-map)]]
     (ip-permissions (assoc permission :ip-range ip-range)))))

(defn permission->ip-permission
  "Return a sequence of ip permissions"
  [{:keys [cidr group role port protocol] :as permission}]
  (cond
    cidr [(ip-permissions
           (assoc (select-keys permission [:port :protocol]) :ip-range cidr))]
    group (target-permissions permission (targets-in-group group))
    role (target-permissions permission (targets-with-role role))))

(def ip-perm-keys [:to-port :from-port :ip-protocol :ip-ranges])

(defn matching-permission
  "Predicate for a target permission already having been set.  Returns
  the matching permission."
  [ip-permissions permission]
  (debugf "matching-permission %s %s" ip-permissions permission)
  (some (fn [ip-permission]
          (and
           (= (select-keys ip-permission ip-perm-keys)
              (select-keys permission ip-perm-keys))
           ip-permission))
        ip-permissions))

(defmethod configure-net-rules :pallet-ec2
  [_ permissions]
  (let [compute (compute-service)
        sg-name (sg-name (group-name))
        region (target-region)
        vpc-id (:vpc-id (.info (:node (target))))

        {:keys [group-id ip-permissions]}
        (sg-info compute vpc-id sg-name)

        target-perms (mapcat permission->ip-permission permissions)
        part (reduce
              (fn [[ip-permissions add-permissions] permission]
                (if-let [p (matching-permission ip-permissions permission)]
                  [(seq (remove #(= p %) ip-permissions))
                   add-permissions]
                  [ip-permissions
                   (conj add-permissions permission)]))
              [ip-permissions nil]
              target-perms)]
    (debugf "configure-net-rules vpc-id: %s sg-name: %s sg-id: %s"
            vpc-id sg-name group-id)
    (tracef "configure-net-rules %s" (pr-str permissions))
    (tracef "configure-net-rules %s" (pr-str part))
    (when-let [permissions (second part)]
      (authorize compute group-id permissions))
    (when-let [permissions (first part)]
      (revoke compute group-id permissions))))
