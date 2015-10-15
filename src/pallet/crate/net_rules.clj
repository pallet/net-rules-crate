(ns pallet.crate.net-rules
  "Control network port access"
  (:require
   [clojure.string :as string]
   [clojure.tools.logging :refer [debugf warnf]]
   [pallet.action :refer [with-action-options]]
   [pallet.actions
    :refer [exec-checked-script packages remote-directory remote-file]]
   [pallet.api :as api :refer [plan-fn]]
   [pallet.common.context :refer [throw-map]]
   [pallet.compute :refer [service-properties]]
   [pallet.crate
    :refer [assoc-settings defmethod-plan defplan get-settings nodes-with-role
            target update-settings]]
   [pallet.crate-install :as crate-install]
   [pallet.node :refer [compute-service]]
   [pallet.utils :refer [apply-map deep-merge]]
   [pallet.version-dispatch
    :refer [defmethod-version-plan defmulti-version-plan]]
   [pallet.versions :refer [version-string as-version-vector]]
   [schema.core :as schema]))

(def facility ::net-rules)

;;; # Settings
(defplan settings
  "Capture settings for nodejs"
  [{:keys [instance-id]
    :as settings}]
  (let [compute (-> (target) :node compute-service)
        settings (merge
                  (if compute
                    {:implementation
                     (-> compute service-properties :provider)}
                    (warnf (str "No default net-rules implementation."
                                "  No compute service for target node.")))
                  (dissoc settings :instance-id))]
    (debugf "net-rules settings %s" settings)
    (when-let [impl (:implementation settings)]
      (try
        (require (symbol (str "pallet.crate.net-rules." (name impl))))
        (catch java.io.FileNotFoundException e
          (warnf (str "No net-rules net-rules applied.  "
                      "No implementation for " impl "."))
          ;; (throw
          ;;  (ex-info (str "No net-rules implementation found for "
          ;;                (name (:implementation settings)))
          ;;           {:type :net-rules/no-such-implementation
          ;;            :implementation (:implementation settings)}
          ;;           e))
          )))
    (assoc-settings facility settings {:instance-id instance-id})))

;;; # Install
(defmulti install-net-rules
  (fn [impl settings] impl))

(defmethod install-net-rules :default
  [impl settings])


(defplan install
  [{:keys [instance-id] :as options}]
  (let [settings (get-settings facility options)]
    ))

;;; # Configure
;;; Set up the network restrictions/permissions

(defmulti configure-net-rules
  (fn [impl permissions] impl))

(defmethod configure-net-rules :default
  [impl settings]
  (warnf (str "No net-rules configuration applied.")))

(defplan configure
  "Write net-rules configuration for a node."
  [{:keys [instance-id] :as options}]
  (let [{:keys [implementation allow]} (get-settings facility options)]
    (configure-net-rules implementation allow)))

;;; # Remove
;;; Remove net rules
(defmulti remove-group-net-rules
  (fn [impl compute] impl))

(defmethod remove-group-net-rules :default
  [impl compute]
  (warnf (str "No net-rules group removed.")))

(defplan remove-group
  "Remove net-rules configuration for a group."
  [{:keys [instance-id] :as options}]
  (debugf "remove-group %s" (target))
  (let [compute (:compute (target))
        implementation (-> compute service-properties :provider)]
    (remove-group-net-rules implementation compute)))

;;; # Network rules
(def PermissionBase
  {:port schema/Int
   :protocol schema/Keyword})

(def GroupPermission (assoc PermissionBase :group schema/Keyword))
(def RolePermission (assoc PermissionBase :role schema/Keyword))
(def CidrPermission (assoc PermissionBase :cidr schema/Str))

(def Permission
  (schema/either GroupPermission RolePermission CidrPermission))

(defn permit
  [permission {:keys [instance-id] :as options}]
  {:pre [(schema/validate Permission permission)]}
  (update-settings facility (select-keys options [:instance-id])
                   update-in [:allow] (comp vec distinct (fnil conj []))
                   permission))

(defplan permit-group
  "Grant a group permission to access a port"
  [group port {:keys [instance-id protocol] :or {protocol :tcp} :as options}]
  {:pre [(keyword? group)
         (or (keyword? protocol) (number? protocol))
         (or (nil? options) (map? options))]}
  (permit {:group group :port port :protocol protocol}
          (select-keys options [:instance-id])))

(defplan permit-role
  "Grant a role permission to access a port"
  [role port {:keys [instance-id protocol] :or {protocol :tcp} :as options}]
  {:pre [(keyword? role) (or (keyword? protocol) (number? protocol))
         (or (nil? options) (map? options))]}
  (warnf "permit-role permissions %s" (select-keys options [:instance-id]))
  (permit {:role role :port port :protocol protocol}
          (select-keys options [:instance-id])))

(defplan permit-source
  "Grant an IP source range permission to access a port"
  [cidr port {:keys [instance-id protocol] :or {protocol :tcp} :as options}]
  {:pre [(string? cidr) (or (keyword? protocol) (number? protocol))
         (or (nil? options) (map? options))]}
  (permit {:cidr cidr :port port :protocol protocol}
          (select-keys options [:instance-id])))

;;; # Server spec
(defn server-spec
  "Returns a service-spec for controlling network port access."
  [{:keys [instance-id] :as settings}]
  (api/server-spec
   :phases {:settings (plan-fn
                       (pallet.crate.net-rules/settings settings))
            :install (plan-fn
                      (install {:instance-id instance-id}))
            :configure (plan-fn
                        (configure {:instance-id instance-id}))
            :destroy-group (plan-fn
                            (remove-group {:instance-id instance-id}))}
   :default-phases [:install :configure]))
