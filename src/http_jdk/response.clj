(ns http-jdk.response)


(defn created
  "Returns a HTTP 201 created response." 
  ([url] (created url nil))
  ([url body]
   {:status  201
    :headers {"Location" url}
    :body    body}))