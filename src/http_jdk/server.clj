(ns http-jdk.server
  (:import [com.sun.net.httpserver HttpServer HttpHandler HttpExchange]
           [java.net InetSocketAddress]
           [java.util.concurrent Executors]))

(defn send-resp
  "Sends a response with given status and body.
   
   Args:
     exchange - HttpExchange instance
     status - HTTP status code (e.g., 200)
     body - response body as string
  "
  [exchange status body]
  (let [response-bytes (.getBytes body "UTF-8")
        response-len (count response-bytes)]
    (.set (.getResponseHeaders exchange) "Content-Type" "text/plain; charset=UTF-8")
    (.sendResponseHeaders exchange status response-len)
    (with-open [os (.getResponseBody exchange)]
      (.write os response-bytes)
      (.flush os))))

(defn mk-handler
  [handler-fn]
  (reify HttpHandler
    (handle [_this exchange]
      (try
        (handler-fn exchange)
        (catch Exception e
          (println "Handler error:" (.getMessage e))
          (.printStackTrace e)
          (try
            (send-resp exchange 500 (str "Server error: " (.getMessage e)))
            (catch Exception _
              (println "Failed to send error response"))))))))

(defn mk-http-server
  "Creates a JDK HTTP server.
   
   Supports operations:
     :start      (start server listening on configured address/port)
     :stop       (stop the server from listening)
     :add-route  (add a route with a handler expects args: path handler)
     :server     (return the underlying java HttpServer object)
     :info       (return a map of server details)
   
   Args:
     host - hostname or ip address to bind to (e.g., \"localhost\", \"0.0.0.0\")
     port - port number to listen on
     backlog - number of pending connections (default: 0)
     executor - optional Executor for handling requests (default: cached thread pool)
   
   Returns:
     function that accepts (operation) and optional args"
  [& {:keys [host port backlog executor]
      :or {host "localhost" port 8080 backlog 0}}]
  (let [addr (InetSocketAddress. host port)
        server (HttpServer/create addr backlog)
        state (atom :idle)]
    (when executor
      (.setExecutor server executor))
    (let [server-ops {:start     (fn []
                                   (when (= :idle @state)
                                     (reset! state :running)
                                     (println "Starting server")
                                     (.start server)))
                      :stop      (fn []
                                   (when (= :running @state)
                                     (reset! state :idle)
                                     (.stop server 0)))
                      :server    (fn [] server)
                      :add-route (fn [path handler]
                                   (.createContext server path (mk-handler handler)))
                      :info      (fn [] {:host    host
                                         :port    port
                                         :backlog backlog
                                         :state   @state})}]
      (fn [operation & args]
        (-> (server-ops operation)
            (apply args))))))


(defn get-request-uri
  "Gets the request URI from an exchange.
   
   Args:
     exchange - HttpExchange instance
   
   Returns: java.net.URI"
  [exchange]
  (.getRequestURI exchange))

(defn get-request-path
  "Gets the request path from an exchange.
   
   Args:
     exchange - HttpExchange instance
   
   Returns: path as string (e.g., \"/users/123\")"
  [exchange]
  (-> exchange get-request-uri .getPath))

(defn parse-query-params
  "Parses query parameters from a URI query string.
   
   Args:
     query-string - the query string (e.g., \"name=John&age=30\")
   
   Returns: map of parameters (e.g., {\"name\" \"John\" \"age\" \"30\"})"
  [query-string]
  (if (nil? query-string)
    {}
    (let [pairs (.split query-string "&")]
      (into {}
            (map (fn [pair]
                   (let [[key val] (.split pair "=" 2)]
                     [key (if (nil? val) "" val)]))
                 pairs)))))

(defn get-query-params
  "Gets query parameters from an exchange.
   
   Args:
     exchange - HttpExchange instance
   
   Returns: map of query parameters"
  [exchange]
  (let [uri (get-request-uri exchange)
        query (.getQuery uri)]
    (parse-query-params query)))

(defn extract-path-params
  "Extracts path parameters by comparing request path against a pattern.
   
   For example, if context is \"/users\" and request is \"/users/123/posts/456\",
   returns the remaining path \"/123/posts/456\".
   
   Args:
     exchange - HttpExchange instance
     context-path - the context path prefix (e.g., \"/users\")
   
   Returns: remaining path as string"
  [exchange context-path]
  (let [request-path (get-request-path exchange)]
    (if (.startsWith request-path context-path)
      (subs request-path (count context-path))
      "")))

(defn set-response-header
  "Sets a response header.
   
   Args:
     exchange - HttpExchange instance
     name - header name
     value - header value
   
   Returns: nil"
  [exchange name value]
  (.set (.getResponseHeaders exchange) name value))





(comment
  (def server (mk-http-server :host "localhost" :port 8080)) 

  ;; Simple route
  (server :add-route "/hello" (fn [exchange]
                                (send-resp exchange 200 "Hello, World!")))
  
  ;; Route with query parameters: GET /search?q=clojure&limit=10
  (server :add-route "/search" (fn [exchange]
                                 (let [params (get-query-params exchange)]
                                   (send-resp exchange 200 
                                              (str "Search query: " (params "q") 
                                                   ", limit: " (params "limit"))))))
  
  ;; Route with path parameters: GET /users/123
  ;; The context path is the prefix, remaining path can be parsed manually
  (server :add-route "/users" (fn [exchange]
                                (let [remaining-path (extract-path-params exchange "/users")
                                      user-id (if (> (count remaining-path) 0)
                                                (subs remaining-path 1) ; remove leading /
                                                "none")]
                                  (send-resp exchange 200 
                                             (str "User ID: " user-id)))))
  
  ;; Route with both path and query: GET /posts/42?format=json
  (server :add-route "/posts" (fn [exchange]
                                (let [remaining-path (extract-path-params exchange "/posts")
                                      post-id (if (> (count remaining-path) 0)
                                                (subs remaining-path 1)
                                                "none")
                                      query-params (get-query-params exchange)
                                      format (get query-params "format" "html")]
                                  (send-resp exchange 200 
                                             (str "Post ID: " post-id ", Format: " format)))))

  (server :start)      ; starts listening
  (server :info)       ; {:host "localhost" :port 8080 ...}
  (server :server)     ; the HttpServer object
  (server :stop)   

  ;;
  )