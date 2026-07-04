(ns http-jdk.server-test
  (:require [clojure.test :refer [deftest is use-fixtures run-tests]]
            [http-jdk.server :as server]))

(def ^:dynamic *test-server* nil)

(defn with-test-server [f]
  (binding [*test-server* (server/mk-http-server :host "localhost" :port 8080)] 
    (*test-server* :add-route "/health" (fn [exchange]
                                          (server/send-resp exchange 200 "ok")))
    (*test-server* :start)
    (try
      (f)
      (finally
        (*test-server* :stop)))))

(use-fixtures :once with-test-server)

(deftest server-starts
  (let [{:keys [host port state]} (*test-server* :info)]
    (is (= "localhost" host))
    (is (= 8080 port))
    (is (= :running state))))

(deftest basic-route
  (let [route                      "/v1/service/health"
        root-url                   "http://localhost:8080"
        request                    (atom {})
        _                          (*test-server* :add-route route (fn [req]
                                                                     (reset! request req)
                                                                     {:status  200
                                                                      :body    "ok"
                                                                      :headers ""}))
        response                   (slurp (str root-url route))
        {:keys [body method uri-path context-path path-params query-params]} @request]
    (is (= "ok" response))
    (is (= "GET" method))
    (is (= route uri-path))
    (is (= "" path-params))
    (is (= {} query-params))
    #_(is (= "" context-path))))

(deftest path-params-map-infers-placeholder-values
  (let [exchange (proxy [Object] []
                   (getRequestURI [] (java.net.URI. "http://localhost/users/42/posts/99")))]
    (is (= {:user-id "42" :post-id "99"}
           (server/path-params-map exchange "/users/{user-id}/posts/{post-id}")))))


(comment
  *e
  (run-tests) 
  ;;
  )