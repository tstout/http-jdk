(ns http-jdk.response)

;; TODO - need to add more common responses here, e.g. 200 OK, 400 Bad Request, 404 Not Found, etc.

(defn created
  "Returns a HTTP 201 created response." 
  ([url] (created url nil))
  ([url body]
   {:status  201
    :headers {"Location" url}
    :body    body}))


