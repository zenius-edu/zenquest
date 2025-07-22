(ns app.plumbing.routes
  (:require
   [reitit.ring :as ring]
   [app.utils :as u]))

(defn api-check
  "Helper function for testing api"
  [request]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    {:status  "ok"
             :message "API is running fine"}})


(defn health-check []
  ["/health" {:get api-check}])

(defn create-routes
  "Creates the whole routes for the system"
  [db openai]
  (ring/router
   [["/" {:get api-check}]
    (health-check)]))
