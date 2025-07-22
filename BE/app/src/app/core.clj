(ns app.core
  (:require [app.system :refer [create-system]]
            [com.stuartsierra.component :as component]))

(defonce system-ref (atom nil))

(defn start []
  (println "🔧 Starting system...")
  (let [system (create-system)]
    (reset! system-ref (component/start-system system))
    (println "✅ System started.")))

(defn stop []
  (when @system-ref
    (println "🛑 Stopping system...")
    (component/stop-system @system-ref)
    (reset! system-ref nil)
    (println "✅ System stopped.")))

(defn -main [& _args]
  (start))

