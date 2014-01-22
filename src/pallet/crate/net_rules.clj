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
   [pallet.versions :refer [version-string as-version-vector]]))

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
  (warnf (str "No net-rules net-rules applied.")))

(defplan configure
  "Write net-rules configuration for a node."
  [{:keys [instance-id] :as options}]
  (let [{:keys [implementation allow]} (get-settings facility options)]
    (configure-net-rules implementation allow)))

;;; # Network rules
(defn permit
  [permission {:keys [instance-id] :as options}]
  {:pre [(map? permission)]}
  (update-settings facility (select-keys options [:instance-id])
                   update-in [:allow] (comp vec distinct (fnil conj []))
                   permission))

(defplan permit-group
  "Grant a group permission to access a port"
  [group port {:keys [instance-id protocol] :or {protocol :tcp} :as options}]
  {:pre [(keyword? group) (or (keyword? protocol) (number? protocol))]}
  (permit {:group group :port port :protocol protocol}
          (select-keys options [:instance-id])))

(defplan permit-role
  "Grant a role permission to access a port"
  [role port {:keys [instance-id protocol] :or {protocol :tcp} :as options}]
  {:pre [(keyword? role) (or (keyword? protocol) (number? protocol))]}
  (permit {:role role :port port :protocol protocol}
          (select-keys options [:instance-id])))

(defplan permit-source
  "Grant an IP source range permission to access a port"
  [cidr port {:keys [instance-id protocol] :or {protocol :tcp} :as options}]
  {:pre [(string? cidr) (or (keyword? protocol) (number? protocol))]}
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
                        (configure {:instance-id instance-id}))}))