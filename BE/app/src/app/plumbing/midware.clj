(ns app.plumbing.midware
  (:require [app.utils :as u]
            [monger.collection :as mc]))

(defn api-check
  [req]
  (u/info "Masuk ke API Check")
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    {:status  "ok"
             :message "API is working"}})

(defn backware-testing
  "Just a testing midware"
  [fun db openai request]
  (u/info "=======================================================================")
  (u/info "URI : " (:uri request))
  (merge {:status 200
          :headers {"Content-type" "application/json"}}
         (fun db openai request)))

(defn auth
  "Authenticating user request"
  [db request]
  (if (= :get (:request-method request))
    true
    (let [user (get-in request [:body :cred])]
      (when-let [db-user (mc/find-one-as-map (:db-zenleap-content db) "creds" {:email (:email user)})]
        (mc/update-by-id (:db-zenleap-content db)
                         "creds"
                         (:email db-user)
                         (assoc db-user :last-active (u/now)))
        (and (:approved db-user)
             (= (select-keys user [:email :token])
                (select-keys db-user [:email :token])))))))

(defn backware
  "Create a json response out of a function"
  ([fun db request]
   (u/info "=======================================================================")
   (u/info "URI : " (:uri request))
   (if (auth db request)
     (merge {:status  200
             :headers {"Content-Type" "application/json"}}
            (fun db request))
     {:status  401
      :headers {"Content-Type" "application/json"}
      :body    {:status  "error"
                :message "Failed to login, please relogin and refresh the app"}}))
  ([fun db openai request]
   (u/info "=======================================================================")
   (u/info "URI : " (:uri request))
   (if (auth db request)
     (merge {:status  200
             :headers {"Content-Type" "application/json"}}
            (fun db openai request))
     {:status  401
      :headers {"Content-Type" "application/json"}
      :body    {:status  "error"
                :message "Failed to login, please relogin and refresh the app"}})))