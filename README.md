# http-jdk
Clojure conveniences for JDK http server 

The package [com.sun.net.httpserver](https://docs.oracle.com/javase/8/docs/jre/api/net/httpserver/spec/com/sun/net/httpserver/package-summary.html) has been available since Java 6. 
It is not deprecated (as of 2026).

## Motivation
Occasionally, you need a no-frills, small footprint (depedency-wise), HTTP 1.1 web server.
This library provides http server functionality with only a runtime dependency on the JRE and clojure. The intended use case for this library is for small applications and testing tools.

## Quick Start

Require the server namespace, create a server, add routes, and start it:

```clojure
(require '[http-jdk.server :as http])

(def server
	(http/mk-http-server :host "localhost"
											 :port 8080))

(server :add-route "/hello"
				(fn [_request]
					{:status 200
					 :body "Hello, world!"}))

(server :start)
```

The server is now available at `http://localhost:8080/hello`. Stop it when you
are finished:

```clojure
(server :stop)
```

### GET routes

A route function receives one request map. For example, this route reads query
parameters from `/search?q=clojure`:

```clojure
(server :add-route "/search"
				(fn [{:keys [query-params]}]
					{:status 200
					 :body (str "Searching for: " (:q query-params))}))
```

Path parameters are declared with a route template passed as the third argument
to `:add-route`:

```clojure
(server :add-route
				"/users"
				(fn [{:keys [path-params]}]
					{:status 200
					 :body (str "User: " (:user-id path-params))})
				"/users/{user-id}")
```

The request `GET /users/42` produces `{:user-id "42"}` in `:path-params`.

### POST routes

The request body is available as a string in `:body`. This route echoes a POST
body back to the client:

```clojure
(server :add-route "/echo"
				(fn [{:keys [body]}]
					{:status 200
					 :body body}))
```

For example:

```sh
curl -X POST http://localhost:8080/echo \
	-H 'Content-Type: text/plain' \
	--data 'hello from POST'
```

### Request and response maps

The route function receives a map with these keys:

| Key | Description |
| --- | --- |
| `:method` | HTTP method as a keyword, such as `:get` or `:post` |
| `:uri` | The request `java.net.URI` |
| `:uri-path` | The request path as a string |
| `:context-path` | The path registered with the JDK HTTP server |
| `:headers` | Request headers |
| `:query-params` | Query parameters as keyword-to-string values |
| `:path-params` | Template parameters as keyword-to-string values |
| `:body` | Request body as a string |
| `:exchange` | The underlying `HttpExchange`, for lower-level access |

The route function should return a response map containing:

```clojure
{:status 200
 :body "response text"
 :headers ""}
```

`:status` is the HTTP status code and `:body` must be a string. `:headers` is
accepted for compatibility with the response map shape; responses currently
use the library's `text/plain; charset=UTF-8` content type.

# TODO
This is barely functional at the moment, but meets my initial use case.
Minor work is needed for header processing. 