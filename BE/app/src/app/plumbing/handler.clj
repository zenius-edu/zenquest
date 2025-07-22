(ns app.plumbing.handler
  (:require
   [app.utils :as u] 
   [app.plumbing.routes :as routes]
   [com.stuartsierra.component :as component]
   [reitit.ring :as ring]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
   [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.session :refer [wrap-session]]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [jumblerg.middleware.cors :as jcors]))

;(defn create-handler [db openai]
;  (-> (routes/create-routes db openai)
;      (ring/ring-handler)
;      (jcors/wrap-cors #".*")
;      wrap-params
;      (wrap-json-body {:keywords? true :bigdecimals? true})
;      wrap-cookies
;      wrap-session
;      wrap-json-response
;      ;; https://github.com/steffan-westcott/clj-otel/blob/master/doc/guides.adoc#work-with-http-client-and-server-spans
;      ;; looks like wrap-route is used when having more spesific use case
;      ;; trace-http/wrap-route
;      ;; (trace-http/wrap-server-span {:create-span? false})
;      ;; false, we use agent for creating span
;      ;; trace-http/wrap-exception-event
;      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))))

(defn not-found-handler [_]
  {:status 404
   :body {:status "error"
          :message "Not Found"}})


(defn create-handler [db openai server-secret]
  (-> (routes/create-routes db openai)
      (ring/ring-handler
       (ring/create-default-handler
        {:not-found not-found-handler}))
      (jcors/wrap-cors #".*")
      wrap-params
      (wrap-json-body {:keywords? true :bigdecimals? true})
      wrap-cookies
      (wrap-session
       {:store (cookie-store)
        :set-cookies? true
        ;:cookie-attrs {:http-only true
        ;               :secure false ; Set to false for local development
        ;               :same-site :lax}  ; Change to :lax for better compatibility
        :cookie-attrs {:http-only true
                       :secure true
                       :same-site :strict}
        })
      wrap-json-response
      ;; https://github.com/steffan-westcott/clj-otel/blob/master/doc/guides.adoc#work-with-http-client-and-server-spans
      ;; looks like wrap-route is used when having more spesific use case
      ;; trace-http/wrap-route
      ;; (trace-http/wrap-server-span {:create-span? false})
      ;; false, we use agent for creating span
      ;; trace-http/wrap-exception-event
      (wrap-defaults (assoc-in site-defaults [:security :anti-forgery] false))))

(defrecord Handler [dbase openai vod server-secret]
  component/Lifecycle
  (start [this]
    (assoc this :handler (create-handler dbase openai server-secret)))
  (stop [this]
    this))

(defn create-handler-component [config]
  (map->Handler config))
