(ns app.plumbing.db
  (:require
   [com.stuartsierra.component :as component]
   [app.utils :as u]
   [monger.core :as mg]))

(defrecord Dbase [db-mongo-config]
  component/Lifecycle
  (start [this]
    (u/info "Starting the database component")
    (let [_ (u/info (:uri-zenquest db-mongo-config))
          conn-zenquest (mg/connect (assoc db-mongo-config :uri
                                           (:uri-zenquest db-mongo-config)))
          db-zenquest (mg/get-db conn-zenquest (:db-zenquest db-mongo-config))]
      (u/info "Starting the database")
      (assoc this
             :db db-zenquest)))
  (stop [this]
    (when-let [conn (:conn this)]
      (mg/disconnect conn))
    (u/info "Database stopped")
    (dissoc this :conn)))

(defn create-database-component [db-mongo-config]
  (map->Dbase {:db-mongo-config db-mongo-config}))







