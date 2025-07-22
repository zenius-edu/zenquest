(ns app.plumbing.openai
  (:require
   [com.stuartsierra.component :as component]
   [app.utils :as u]
   [clj-http.client :as http]
   [cheshire.core :as json]))

(declare get-api-config base-request generate models generate-image)

;; this is the component openai with basic setting of openai
;; returns the generator function

(defn get-api-config [model openai-config deepseek-config]
  (if (or (= model "deepseek-chat")
          (= model "deepseek-reasoner"))
    deepseek-config
    openai-config))

(defrecord Openai [openai-url openai-key deepseek-url deepseek-key dbase]
  component/Lifecycle
  (start [this]
    (u/info "Setting up the openai component")
    (u/info "Openai URL: " openai-url)
    (assoc this
           :openai (fn [{:keys [model temperature messages]}]
                     (u/info "Generating from openai")
                     (let [api-config (get-api-config model
                                                      {:url openai-url :key openai-key}
                                                      {:url deepseek-url :key deepseek-key})
                           send-to-openai {:model      model
                                           :openai-url  (str (:url api-config))
                                           :messages    messages
                                           :temperature temperature
                                           :openai-key  (str (:key api-config))}
                           db            (:db dbase)]
                       ;(u/pres send-to-openai)
                       ;(u/pres db)
                       ;(u/pres dbase)
                       (generate send-to-openai db)))
           :openai-image (fn [{:keys [model prompt size quality]}]
                           (u/info "Generating image from OpenAI")
                           (let [url "https://api.openai.com/v1/images/generations"  ; Fixed URL
                                 data {:model model
                                       :prompt prompt
                                       :size size
                                       :quality (or quality "standard")
                                       :n 1
                                       :response_format "url"}
                                 headers {"Authorization" (str "Bearer " openai-key)
                                          "Content-Type" "application/json"}]
                             (try
                               (let [response (http/post url
                                                         {:headers headers
                                                          :body (json/generate-string data)})
                                     body (json/parse-string (:body response) true)]
                                 {:success true
                                  :images (mapv :url (:data body))})
                               (catch Exception e
                                 {:success false
                                  :error (str "Error: " (.getMessage e))
                                  :details (ex-data e)}))))))
  (stop [this]
    (u/info "Openai stopped")
    this))

(defn create-openai-component
  "Openai component constructor"
  [{:keys [openai-url openai-key deepseek-url deepseek-key]}]
  (map->Openai {:openai-url openai-url
                :openai-key openai-key
                :deepseek-url deepseek-url
                :deepseek-key deepseek-key}))

(defn base-request
  [api-token]
  {:accept       :json
   :content-type :json
   :headers      {"Authorization" (str "Bearer " api-token)}})


(def new-models
  {"gpt-4o"      {:req-enum "gpt-4o-2024-08-06"             ;;can later be replaced to "gpt-4o"
                  :max-tokens 16384}
   "gpt-4o-mini" {:req-enum "gpt-4o-mini"
                  :max-tokens 16384}
   "o3-mini" {:req-enum "o3-mini"
              :max-tokens 100000}
   "deepseek-chat" {:req-enum "deepseek-chat"
                    :max-tokens 8192}
   "deepseek-reasoner" {:req-enum "deepseek-reasoner"
                        :max-tokens 8192}})

(def image-models
  {"dall-e-3" {:sizes ["1024x1024" "1024x1792" "1792x1024"]
               :quality ["standard" "hd"]}
   "dall-e-2" {:sizes ["256x256" "512x512" "1024x1024"]
               :quality ["standard"]}})

(defn extract-json-from-markdown
  "Extract JSON content from markdown code blocks"
  [content]
  (if-let [json-match (re-find #"```(?:json)?\n([\s\S]*?)\n```" content)]
    (second json-match)  ; Get the captured JSON content
    content))  ; Return original content if no code block found

(defn generate
  "Just call this one to generate the response from openAI"
  [{:keys [model openai-url messages openai-key temperature] :as send-to-openai} db]
  (u/info "Getting into generate function inside openai component with these to send to openai: ")
  (let [get-model (new-models model)
        base-data (merge {:model           (:req-enum get-model)
                          :messages        messages
                          :temperature (or temperature 0.21)
                          :n               1}
                         (when-let [max-tokens (:max-tokens get-model)]
                           {(if (= model "o3-mini")
                              :max_completion_tokens
                              :max_tokens) max-tokens}))
        data      (if (= model "deepseek-reasoner")
                    base-data
                    (assoc base-data :response_format {:type "json_object"}))
        ;; Remove temperature if model is o3-mini
        data      (if (= model "o3-mini")
                    (dissoc data :temperature)
                    data)]
    (println "\nSending request to OpenAI with data:")
    (u/pres data)
    (let [resp (try (->> data
                         (json/generate-string)
                         (assoc (base-request openai-key) :body)
                         (http/post openai-url))
                    (catch Exception e
                      (println "\nError calling OpenAI:")
                      (u/error e)))]
      (let [resp1  (-> (:body resp)
                       (json/parse-string true))]
        (println "\nReceived response from OpenAI:")
        (u/pres resp)
        (u/pres resp1)
        (let [model  (:model resp1)
              usage  (:usage resp1)
              content (get-in resp1 [:choices 0 :message :content])
              _ (println "\nContent:")
              parsed-content (if (= model "deepseek-reasoner")
                               (-> content
                                   extract-json-from-markdown
                                   (json/parse-string true))
                               (json/parse-string content true))
              result (-> (select-keys resp1 [:usage])
                         (assoc :result parsed-content))]
          (println "\nParsed result:")
          (u/pres result)
          result)))))

(defn generate-image
  "Generate image from OpenAI"
  [{:keys [model prompt size quality openai-url openai-key]}]
  (u/info "Getting into generate-image function inside openai component")
  (let [data {:model model
              :prompt prompt
              :size size
              :quality (or quality "standard")
              :n 1
              :response_format "url"}
        url (str openai-url "/images/generations")]
    (try
      (let [resp (-> data
                     (json/generate-string)
                     (assoc (base-request openai-key) :body)
                     (http/post url))
            body (-> resp :body (json/parse-string true))]
        {:success true
         :images (mapv :url (:data body))})
      (catch Exception e
        {:success false
         :error (.getMessage e)}))))

(defn gen-image
  [openai {:keys [prompt model size quality]}]
  (let [image-fn (:openai-image openai)
        result (-> (image-fn {:model model
                              :prompt prompt
                              :size (or size "1024x1024")
                              :quality (or quality "standard")})
                   (try (catch Exception e
                          (u/pres e)))
                   u/let-pres)]
    (if (:success result)
      (first (:images result))
      (u/error "Image generation failed:" (:error result)))))
