(ns http-jdk.client
  (:import [java.net.http
            HttpClient
            HttpRequest
            HttpResponse$BodyHandlers
            HttpRequest$BodyPublishers]
           [java.net URI]))

(defn get-request [uri]
  (-> (HttpRequest/newBuilder)
      .GET
      (.uri (URI/create uri))
      (.setHeader "User-Agent" "Java 11+")
      .build))

(defn post-request 
  [uri body]
  (-> (HttpRequest/newBuilder)
      (.uri (URI/create uri))
      (.POST (HttpRequest$BodyPublishers/ofString (str body)))
      (.setHeader "User-Agent" "Java 11+")
      .build))

(defn http-tx
  "Transmit an http request. Returns a map with keys :body, :status, and :headers.
   
   Args:
     req - HttpRequest instance
   
   Returns: map"
  [req]
  (let [resp (-> (HttpClient/newHttpClient)
                 (.send req (HttpResponse$BodyHandlers/ofString)))]
    {:body    (.body resp)
     :status  (.statusCode resp)
     :headers (.map (.headers resp))}))

(comment
  (-> "https://httpbin.org/get?foo=bar&baz=quux"
      get-request
      http-tx)
  
  (-> "https://httpbin.org/post"
      (post-request {:key "value"})
      http-tx)
  
  ;;
  )