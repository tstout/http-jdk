(ns http-jdk.server-test
  (:require [clojure.test :refer [deftest is are use-fixtures run-tests]]
            [http-jdk.server :as server]
            [http-jdk.client :refer [post-request http-tx]]))

(def ^:dynamic *test-server* nil)

(defn with-test-server [f]
  (binding [*test-server* (server/mk-http-server :host "localhost" :port 8080)]
    (*test-server* :add-route "/health" (fn [_req]
                                          {:status  200
                                           :body    "ok"
                                           :headers ""}))
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


;;
;; These tests generally follow the pattern of 
;; 1) add a route to the test server
;; 2) make a request to that route
;; 3) check the response and the request map captured by the route handler
;;
(deftest basic-route
  (let [route                                              "/v1/service/health"
        root-url                                           "http://localhost:8080"
        request                                            (atom {})
        _                                                  (*test-server* :add-route route (fn [req]
                                                                                             (reset! request req)
                                                                                             {:status  200
                                                                                              :body    "ok"
                                                                                              :headers ""}))
        response                                           (slurp (str root-url route))
        {:keys [method uri-path path-params query-params]} @request]
    (is (= "ok" response))
    (is (= "GET" method))
    (is (= route uri-path))
    (is (= {} path-params))
    (is (= {} query-params))
    #_(is (= "" context-path))))

(deftest path-param-route
  (let [route                                              "/users/"
        route-template                                     "/users/{user-id}/posts/{post-id}"
        root-url                                           "http://localhost:8080"
        request                                            (atom {})
        _                                                  (*test-server* :add-route
                                                                          route
                                                                          (fn [req]
                                                                            (reset! request req)
                                                                            {:status  200
                                                                             :body    "ok"
                                                                             :headers ""})
                                                                          route-template)
        response                                           (slurp (str root-url "/users/42/posts/99"))
        {:keys [method uri-path path-params query-params]} @request]
    (is (= "ok" response))
    (is (= "GET" method))
    (is (= "/users/42/posts/99" uri-path))
    (is (= {:user-id "42"
            :post-id "99"} path-params))
    (is (= {} query-params))))

(deftest query-param-route
  (let [request                       (atom {})
        _                             (*test-server* :add-route
                                                     "/search"
                                                     (fn [req]
                                                       (reset! request req)
                                                       {:status  200
                                                        :body    "ok"
                                                        :headers ""}))
        response                      (slurp "http://localhost:8080/search?q=clojure&sort=desc")
        {:keys [query-params method]} @request]
    (is (= "ok" response))
    (is (= "GET" method)) 
    (is (= {:q    "clojure"
            :sort "desc"} query-params))))

(deftest post-route
  (let [request          (atom {})
        _                (*test-server* :add-route
                                        "/echo-post-body"
                                        (fn [req]
                                          (reset! request req)
                                          {:status  200
                                           :body    (:body req)
                                           :headers ""}))
        response         (-> 
                          "http://localhost:8080/echo-post-body" 
                          (post-request "body-text")
                          http-tx)
        {:keys [method]} @request]
    (is (= "body-text" (:body response)))
    (is (= "POST" method))))

(comment
  *e
  (run-tests)
  ;;
  )