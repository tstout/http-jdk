(ns http-jdk.server
  (:require [clojure.string :as string])
  (:import [com.sun.net.httpserver HttpServer HttpHandler HttpExchange]
           [java.net InetSocketAddress]
           [java.util.concurrent Executors]))

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

(defn query-params
  "Gets query parameters from an exchange.
   
   Args:
     exchange - HttpExchange instance
   
   Returns: map of query parameters"
  [exchange]
  (-> exchange
      .getRequestURI
      .getQuery
      parse-query-params))


(defn request-path
  "Gets the request path from an exchange.
   Args:
     exchange - HttpExchange instance
   Returns: path as string (e.g., /users/123)"
  [exchange]
  (-> exchange
      .getRequestURI
      .getPath))

#_(defn extract-path-params
  "Extracts path parameters by comparing request path against a pattern.
   For example, if context is /user and request is /users/123/posts/456,
   returns the remaining path /123/posts/456.
   Args:
     exchange - HttpExchange instance
     context-path - the context path prefix (e.g., /users)
   Returns: remaining path as string"
  [exchange context-path]
  (let [request-path (request-path exchange)]
    (if (.startsWith request-path context-path)
      (subs request-path (count context-path))
      "")))

(defn path-params-map
  "Builds a map of path parameters from the request path and context path.

   The function infers parameter names from a route pattern when the context
   path contains placeholder segments such as :id or :section. For example,
   a request to /users/42/profile with context /users/:id/:section will
   produce {:id \"42\" :section \"profile\"}.

   Args:
     exchange - HttpExchange instance
     context-path - the context path prefix (e.g., /users/:id/:section)

   Returns: map of inferred parameter names to values"
  [exchange context-path]
  (let [request-path (request-path exchange)
        context-segments (->> (string/split context-path #"/")
                              (remove string/blank?)
                              vec)
        request-segments (->> (string/split request-path #"/")
                              (remove string/blank?)
                              vec)
        remaining-segments (drop (count context-segments) request-segments)
        param-names (->> context-segments
                         (filter #(and (string? %)
                                       (re-matches #"\{[^}]+\}" %)))
                         (map #(keyword (subs % 1 (dec (count %)))))
                         vec)]
    (reduce-kv (fn [m idx segment]
                 (assoc m (get param-names idx (keyword (str "p" (inc idx)))) segment))
               {}
               (vec remaining-segments))))

(defn xf-exchange
  "Given an exchange, transform it into a map of request details for easier handling.
   Args:
     exchange - HttpExchange instance
   Returns: map containing method, uri, headers, query-params, path-params, body"
  [exchange]
  (let [context-path (-> exchange
                         .getHttpContext
                         .getPath)]
    (try
      {:method       (.getRequestMethod exchange)
       :uri          (.getRequestURI exchange)
       :headers      (.getRequestHeaders exchange)
       :query-params (query-params exchange)
       :uri-path     (request-path exchange)
       :context-path context-path
       :exchange     exchange
       :path-params  (path-params-map exchange context-path)
       :body         (slurp (.getRequestBody exchange))}
      (catch Exception e
        (println "Error processing exchange:" (.getMessage e))
        {}))))

;; This could be private
(defn send-resp
  "Sends a response with given status and body.
   Args:
     exchange - HttpExchange instance
     status - HTTP status code (e.g., 200)
     body - response body as string
  "
  [exchange m]
  (let [{:keys [body status headers]} m
        response-bytes (.getBytes body "UTF-8")
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
        (->> exchange
             xf-exchange
             handler-fn
             (send-resp exchange)) 
        (catch Exception e
          (println "Handler error:" (.getMessage e))
          (.printStackTrace e)
          (try
            (send-resp exchange 
                       {:headers "" :status 500 :body (str "Server error: " (.getMessage e))})
            (catch Exception _
              (println "Failed to send error response"))))))))

;; For routes, want to provide a map to the hander containting:
;; {:method "GET"
;; :path "/users/123"
;; :query-params {"q" "search term"}
;; :path-params {"id" "123"}
;; :headers {"User-Agent" "curl/7.64.1"}
;; :body "(InputStream or string)}


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
      :or   {host    "localhost" 
             port    8080 
             backlog 0}}] 
  (let [addr       (InetSocketAddress. host port)
        server     (HttpServer/create addr backlog)
        state      (atom :idle)
        _          (when executor
                     (.setExecutor server executor))
        server-ops {:start     (fn []
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
          (apply args)))))


(defn get-request-uri
  "Gets the request URI from an exchange.
   Args:
     exchange - HttpExchange instance
   Returns: java.net.URI"
  [exchange]
  (.getRequestURI exchange))

;; TODO - 
;; Change add-route to expect a fn that accepts a map created
;; from xf-exchange

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
  *e
  
  (def server (mk-http-server :host "localhost" :port 8080)) 

  (def request (atom {}))

  ;; Simple route
  (server :add-route "/hello" (fn [req] 
                                (reset! request req) 
                                {:status  200
                                 :body    "Hello, World!"
                                 :headers ""}))
  
  (server :add-route
          "/users" 
          (fn [req]
            (reset! request req)
            {:status  200
             :body    "Profile item"
             :headers ""}))
  
  (slurp "http://localhost:8080/users/1/posts/42")
  
  @request
  ;; Route with query parameters: GET /search?q=clojure&limit=10
  ;; (server :add-route "/search" (fn [exchange]
  ;;                                (let [params (query-params exchange)]
  ;;                                  (send-resp exchange 200 
  ;;                                             (str "Search query: " (params "q") 
  ;;                                                  ", limit: " (params "limit"))))))
  ;; 
  ;; Route with path parameters: GET /users/123
  ;; The context path is the prefix, remaining path can be parsed manually
  ;; (server :add-route "/users" (fn [exchange]
  ;;                               (let [remaining-path (extract-path-params exchange "/users")
  ;;                                     user-id (if (> (count remaining-path) 0)
  ;;                                               (subs remaining-path 1) ; remove leading /
  ;;                                               "none")]
  ;;                                 (send-resp exchange 200 
  ;;                                            (str "User ID: " user-id)))))
  
  ;; Route with both path and query: GET /posts/42?format=json
  ;; (server :add-route "/posts" (fn [exchange]
  ;;                               (let [remaining-path (extract-path-params exchange "/posts")
  ;;                                     post-id (if (> (count remaining-path) 0)
  ;;                                               (subs remaining-path 1)
  ;;                                               "none")
  ;;                                     query-params (get-query-params exchange)
  ;;                                     format (get query-params "format" "html")]
  ;;                                 (send-resp exchange 200 
  ;;                                            (str "Post ID: " post-id ", Format: " format)))))
  ;; 
  (server :start)      ; starts listening (server :info)       ; {:host "localhost" :port 8080 ...}
  (server :server)     ; the HttpServer object
  (server :stop) 
  server  

  ;;
  )