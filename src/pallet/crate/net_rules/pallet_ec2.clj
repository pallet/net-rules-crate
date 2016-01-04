(ns pallet.crate.net-rules.pallet-ec2
  "Implementation of net-rules for pallet-ec2 provider.  You will need
a dependency on pallet-aws 0.2.0 or greater to use this
namespace."
  (:require
   [clojure.set :as set]
   [clojure.stacktrace :refer [root-cause]]
   [clojure.tools.logging :refer [debugf tracef]]
   [com.palletops.awaze.ec2 :as ec2]
   [pallet.compute.ec2 :as pallet-ec2]
   [pallet.crate.net-rules
    :refer [configure-net-rules install-net-rules remove-group-net-rules]]
   [pallet.crate :refer [group-name targets-in-group targets-with-role target]]
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
    (debugf "sg-info %s %s %s" vpc-id sg-name (pr-str security-groups))
    (if matching-group
      {:group-id (:group-id matching-group)
       :ip-permissions
       (->> (:ip-permissions matching-group)
            (filter :ip-ranges)
            (mapv #(select-keys
                    % [:to-port :from-port :ip-protocol :ip-ranges])))})))

(defn subnet-info [compute subnet-id]
  (let [filters [{:name "subnet-id" :values [subnet-id]}]
        {:keys [subnets] :as sns}
        (pallet-ec2/execute
         compute ec2/describe-subnets-map {:filters filters})
        matching-sn (->> subnets
                         (filter #(= subnet-id (:subnet-id %)))
                         first)]
    (debugf "subnet-info %s %s %s %s"
            subnet-id (pr-str sns) (pr-str subnets) matching-sn)
    matching-sn))

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
      :ip-permissions (vec (distinct permissions))})
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
  (debugf "revoke %s %s" sg-id (vec permissions))
  (try
    (pallet-ec2/execute
     compute
     ec2/revoke-security-group-ingress-map
     {:group-id sg-id
      :ip-permissions (vec (distinct permissions))})
    (catch Exception e
      (let [c (or (root-cause e) e)]
        (throw (ex-info (format
                         "Failed to revoke net-rules permissions: %s %s"
                         (type c)
                         (try (bean c) (catch Exception _)))
                        {:type (type c)
                         :bean (try (bean c) (catch Exception _))}
                        e))))))

(defn remove-sg
  "Remove security group"
  [compute sg-id]
  {:pre [sg-id]}
  (debugf "remove-sg %s" sg-id)
  (try
    (pallet-ec2/execute compute ec2/delete-security-group-map {:group-id sg-id})
    (catch Exception e
      (let [c (or (root-cause e) e)
            dependents (and
                        (instance? com.amazonaws.AmazonServiceException c)
                        (= (.getErrorCode
                            ^com.amazonaws.AmazonServiceException c)
                           "DependencyViolation"))
            b (try (bean c) (catch Exception _))]
        (throw (ex-info (format
                         "Failed to remove security group: %s %s" (type c) b)
                        {:type (type c)
                         :bean b
                         :dependency-violation dependents}
                        e))))))

(defn remove-sg-retry-on-dependents
  "Remove security group, retrying if the group is still listed as
  having dependents."
  [compute sg-id {:keys [max-retries standoff]
                  :or {max-retries 5 standoff 10}}]
  {:pre [sg-id]}
  (debugf "remove-sg-retry-on-dependents %ss, %s retries" standoff max-retries)
  (letfn [(try-remove []
            (try
              (remove-sg compute sg-id)
              (catch Exception e
                (debugf
                 "remove-sg-retry-on-dependents exception data %s" (ex-data e))
                (if (:dependency-violation (ex-data e))
                  {::retry true
                   ::exception e}
                  {::exception e}))))]
    (loop [retries max-retries
           standoff standoff]
      (let [r (try-remove)]
        (debugf "remove-sg-retry-on-dependents try-remove %s", r)
        (cond
          (::retry r) (if (pos? retries)
                        (do
                          (debugf "Waiting %s on %s"
                                  standoff (::exception r))
                          (Thread/sleep (* standoff 1000))
                          (recur (dec retries) (* 1.5 standoff)))
                        (throw (::exception r)))
          (::exception r) (throw (::exception r))
          :else r)))))

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

(def ip-perm-keys [:to-port :from-port :ip-protocol])

(defn matching-permission
  "Predicate for a target permission already having been set.  Returns
  the matching permission."
  [ip-permissions permission]
  (debugf "matching-permission %s %s" (vec ip-permissions) permission)
  (let [res (some
             (fn [ip-permission]
               (and
                (= (select-keys ip-permission ip-perm-keys)
                   (select-keys permission ip-perm-keys))
                (let [range (first (:ip-ranges permission))]
                  (and
                   (some #(= range %) (:ip-ranges ip-permission))
                   [ip-permission
                    (update-in ip-permission [:ip-ranges]
                               (fn [ranges]
                                 (vec (remove #(= range %) ranges))))]))))
             ip-permissions)]
    (tracef "matching-permission result %s" res)
    res))

(defn classify-rules
  "Classify rules into those that match the target permissions, those
  that need removing, and those that need adding."
  [ip-permissions target-perms]
  (let [part (reduce
              (fn [[ip-permissions add-permissions] permission]
                (if-let [[p new-p] (matching-permission
                                    ip-permissions permission)]
                  [(->> ip-permissions
                        (map #(if (= p %) new-p %))
                        (filter #(seq (:ip-ranges %))))
                   add-permissions]
                  [ip-permissions
                   (conj add-permissions permission)]))
              [ip-permissions nil]
              target-perms)]
    (tracef "configure-net-rules %s" (pr-str part))
    part))

(defmethod configure-net-rules :pallet-ec2
  [_ permissions]
  (let [compute (compute-service)
        sg-name (sg-name (group-name))
        vpc-id (:vpc-id (.info (:node (target))))

        {:keys [group-id ip-permissions]}
        (sg-info compute vpc-id sg-name)

        target-perms (mapcat permission->ip-permission permissions)
        part (classify-rules ip-permissions target-perms)]
    (debugf "configure-net-rules vpc-id: %s sg-name: %s sg-id: %s"
            vpc-id sg-name group-id)
    (tracef "configure-net-rules %s" (pr-str permissions))
    (tracef "configure-net-rules %s" (pr-str part))
    (when-let [permissions (seq (second part))]
      (authorize compute group-id permissions))
    (when-let [permissions (seq (first part))]
      (revoke compute group-id permissions))))

(defmethod remove-group-net-rules :pallet-ec2
  [_ compute]
  (let [sg-name (str "pallet-" (name (:group-name (target))))
        subnet (if-let [subnet-id (-> (target)
                                      :provider :pallet-ec2 :subnet-id)]
                 (subnet-info compute subnet-id))
        vpc-id (:vpc-id subnet)
        {:keys [group-id] :as sg} (sg-info compute vpc-id sg-name)]
    (debugf "remove-group-net-rules subnet: %s vpc-id: %s sg: %s"
            subnet vpc-id sg)
    (when group-id
      (remove-sg-retry-on-dependents compute group-id {}))))
