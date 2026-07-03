(ns http-jdk.server-test
  (:require [clojure.test :refer [deftest is use-fixtures run-tests]]
            [http-jdk.server :as server]))

(def ^:dynamic *test-server* nil)

(defn with-test-server [f]
  (binding [*test-server* (server/mk-http-server :host "localhost" :port 8080)]
    (let [s *test-server*]
      (s :add-route "/health" (fn [exchange]
                                 (server/send-resp exchange 200 "ok")))
      (s :start)
      (try
        (f)
        (finally
          (s :stop))))))

(use-fixtures :once with-test-server)

(deftest server-fixture-starts-and-stops
  (let [info (*test-server* :info)]
    (is (= "localhost" (:host info)))
    (is (= 8080 (:port info)))
    (is (= :running (:state info)))))

(comment
  *e
  (run-tests)
  ;;
)