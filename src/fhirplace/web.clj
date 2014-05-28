(ns fhirplace.web
  (:use compojure.core)
  (:require [fhirplace.interactions.resource :as res-int]
            [fhirplace.interactions.system :as sys-int]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [compojure.response :as response]
            [ring.util.response :as resp]
            [ring.middleware.stacktrace :refer :all]
            [ring.middleware.file :refer :all]
            [cheshire.core :as json]
            [clojure.xml :as xml]
            [fhirplace.resources.conversion :as conversion]
            [ring.adapter.jetty :as jetty]))

(def uuid-regexp
  #"[0-f]{8}-([0-f]{4}-){3}[0-f]{12}")

;; TODO: Handle non-existed resource types
(defroutes main-routes
  (GET    "/"                                      []                 sys-int/conformance)
  (GET    "/info"                                  []                 sys-int/info)
  (GET    "/metadata"                              []                 sys-int/conformance)
  (POST   "/:resource-type"                        [resource-type]    res-int/create)
  (GET    "/:resource-type/_search"                [resource-type]    res-int/search)
  (GET    ["/:resource-type/:id", :id uuid-regexp] [resource-type id] res-int/read)
  (GET    "/:resource-type/:id/_history/:vid"      [resource-type id vid] res-int/vread)
  (GET    "/:resource-type/:id/_history"           [resource-type id] sys-int/history)
  (DELETE "/:resource-type/:id"                    [resource-type id] res-int/delete)
  (PUT    "/:resource-type/:id"                    [resource-type id] res-int/update)
  (POST   "/:resource-type/_validate"              [resource-type id] sys-int/validate)


  (route/not-found "Not Found"))

(defn wrap-with-system
  [handler system]
  (fn [request]
    (handler (assoc request :system system))))

(def response-serializers
  "Map of serializer fns to use in `wrap-with-body-serialization'."
  {:json (fn [body]
           (json/generate-string body))
   :xml (fn [x]
          (let [xml-clj (conversion/json->xml x)]
            (with-out-str (xml/emit xml-clj))))})

(defn- determine-format
  "Determines request format (:xml or :json)."
  [request]
  (let [format (get-in request [:params :_format])]
    (if format
      ({"application/json" :json
        "application/xml"  :xml} format)
      :html)))

(defn wrap-with-format
  "Middleware for determining format of incoming request.
  Creates :format key in `response' object with value either :xml or :json."
  [handler]
  (fn [request]
    (if-let [format (determine-format request)]
      (handler (assoc request :format format))
      {:status 500
       :body "Could not determine request format."})))

(defn wrap-with-response-serialization
  "Serialize response body to JSON or XML, if it has non-string value."
  [handler]
  (fn [request]
    (let [response (handler request)
          format (:format request)
          body (:body response)]
      (if (coll? body)
        (if (nil? format)
          (throw (Exception. "No request format specified."))
          (if-let [fmt (get response-serializers format)]
            (assoc response :body (fmt body))
            response))
        response))))

(defn wrap-copy-body
  "Because of body can be read only once,
  we should copy it for later use."
  [handler]
  (fn [request]
    (handler (if-let [body (:body request)]
               (assoc request :body-str (slurp (:body request)))
               request))))

(defn create-web-handler [system]
  (let [stacktrace-fn (if (= :dev (:env system)) (wrap-stacktrace) identity)]
    (stacktrace-fn (-> (wrap-with-response-serialization main-routes)
                       ;(wrap-stacktrace)
                       (wrap-with-format)
                       (handler/api)
                       (wrap-with-system system)
                       (wrap-copy-body)
                       (wrap-file "resources/public") ;<http://clojuredocs.org/ring/ring.middleware.file/wrap-file>
                       ))))

(defn start-server
  [handler port]
  {:pre [(not (nil? port))
         (not (nil? handler))]}
  (jetty/run-jetty handler {:port port :join? false}))

(defn stop-server
  [server]
  (.stop server))
