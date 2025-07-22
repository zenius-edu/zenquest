(ns app.system
  (:require
   [com.stuartsierra.component :as component]
   [app.plumbing.db :as db]
   [app.utils :as u]
   [app.plumbing.server :as immut]
   [app.plumbing.handler :as http]
   [app.plumbing.openai :as openai]))

(defn create-system
  "It creates a system, and return the system, but not started yet"
  []
  (let [{:keys [server-path
                server-port
                server-host
                server-secret
                openai-url
                openai-url-image
                openai-key
                deepseek-url
                deepseek-key

                db-mongo-uri-zenbrain
                db-mongo-uri-zenquest
                db-mongo-uri-universal-skill

                db-mongo-port

                db-mongo-universal-skill
                db-mongo-zenbrain
                db-mongo-zenquest

                db-mongo-quiet
                db-mongo-debug]} (u/read-config-true-flat)
        server {:port server-port :path server-path :host server-host}
        db-mongo {:uri-zenbrain        db-mongo-uri-zenbrain
                  :uri-zenquest        db-mongo-uri-zenquest
                  :uri-universal-skill db-mongo-uri-universal-skill

                  :db-zenquest         db-mongo-zenquest
                  :db-zenbrain         db-mongo-zenbrain
                  :db-universal-skill  db-mongo-universal-skill

                  :port                db-mongo-port
                  :quiet               db-mongo-quiet
                  :debug               db-mongo-debug}
        openai-config {:openai-url       openai-url
                       :openai-url-image openai-url-image
                       :openai-key       openai-key
                       :deepseek-url     deepseek-url
                       :deepseek-key     deepseek-key}]
    (u/info "Preparing the system")
    (u/pres db-mongo)
    ;(u/pres openai-config)
    ;(u/pres server)
    (component/system-map
     :openai (-> (openai/create-openai-component openai-config)
                 (component/using [:dbase]))
     :dbase (db/create-database-component db-mongo)
     :handler (component/using (http/create-handler-component {:server-secret server-secret}) [:dbase :openai])
     :server (component/using (immut/create-server-component server) [:handler]))))
