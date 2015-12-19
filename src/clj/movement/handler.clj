(ns movement.handler
  (:import java.util.Date)
  (:require [clojure.java.io :as io]
            [compojure.core :refer [GET POST HEAD ANY defroutes]]
            [compojure.route :refer [not-found resources]]
            [compojure.response :refer [render]]
            [ring.util.response :refer [redirect]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [ring.middleware.anti-forgery :refer [*anti-forgery-token*]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.x-headers :refer [wrap-frame-options]]
            [ring.middleware.edn :refer [wrap-edn-params]]
            [selmer.parser :refer [render-file]]
            [prone.middleware :refer [wrap-exceptions]]
            [environ.core :refer [env]]
            [datomic.api :as d]
            [clojure.string :as str]
            [clojure.set :refer [rename-keys]]
            [clj-time.core :as time :refer [from-now hours]]
            [buddy.sign.jws :as jws]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.token :refer [jws-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [buddy.hashers :as hashers]
            [hiccup.core :refer [html]]
            [taoensso.timbre :refer [info error]]

            [movement.db :refer [tx update-tx-conn! update-tx-db!] :as db]
            [movement.pages.landing :refer [landing-page]]
            [movement.pages.signup :refer [signup-page payment-page]]
            [movement.pages.contact :refer [contact-page]]
            [movement.pages.session :refer [view-session-page view-sub-activated-page]]
            [movement.activation :refer [generate-activation-id send-email send-activation-email]]
            [movement.templates :refer [add-standard-templates-to-user]]))

(selmer.parser/set-resource-path! (clojure.java.io/resource "templates"))

(defn response [data & [status]]
  {:status  (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body    (pr-str data)})

;;;;;; login ;;;;;;

(defn valid-user? [user password]
  (hashers/check password (:user/password user)))

(def secret "mysupersecret")

(def jws-auth-backend (jws-backend {:secret secret :options {:alg :hs512}}))

(defn jws-login
  [username password]
  (let [user (db/find-user username)]
    (cond
      (nil? (:db/id user)) (response {:message "Unknown user"} 400)
      (false? (:user/activated? user)) (response {:message "Email has not been activated. Check your inbox for an activation code."} 400)
      (false? (:user/valid-subscription? user)) (response {:message "This account is not paying for a subscription."} 400)
      (valid-user? user password) (let [claims {:user (keyword username)
                                                :exp  (-> 3 hours from-now)}
                                        token (jws/sign claims secret {:alg :hs512})]
                                    (response {:token token
                                               :user  (:user/email (dissoc user :user/password :db/id))}))
      :else (response {:message "Incorrect password"} 401))))

;;;;;;;;;;;

(defn add-session! [req]
  (let [session (:session (:params req))
        user (:user (:params req))]
    (if (nil? user)
      (response {:message "User email lacking from client data" :session session} 400)
      (try
        (db/transact-session! user session)
        (catch Exception e
          (error e (str "error transacting session: user: " user " session: " session)))
        (finally (do (update-tx-db!)
                     (response {:session session :message "Session stored successfully"})))))))

(defn add-template! [req]
  (let [user (:user (:params req))
        template (:template (:params req))]
    (if (nil? user)
      (response "User email lacking from client data" 400)
      (if (db/new-unique-template? user (:title template))
        (try
          (db/transact-template! user template)
          (catch Exception e
            (response (str "Exception: " e)))
          (finally (do (update-tx-db!)
                       (response "New template stored successfully."))))
        (response "You already have a template with this title. Please choose a unique title for your template." 400)))))

(defn add-user! [email password]
  (if (nil? (:db/id (db/find-user email)))
    (do
      (let [activation-id (generate-activation-id)]
        (db/transact-new-user! email password activation-id)
        (send-activation-email email activation-id)
        (update-tx-db!)
        (str "An activation email has been sent to your email address.")))
    (signup-page (str email " is already registered as a user."))))

(defn activate-user! [id]
  (let [user (db/entity-by-lookup-ref :user/activation-id id)]
    (if-not (nil? (:db/id user))
      (do
        (db/transact-activated-user! (:user/email user))
        (add-standard-templates-to-user (:user/email user))
        {:status  302
         :headers {"Location" (str "/activated/" (:user/email user))}
         :body    ""})
      "<h1>This activation-id is invalid.</h1>")))

(defn change-password! [req]
  (let [email (:username (:params req))
        password (:password (:params req))
        new-password (:new-password (:params req))
        user (db/find-user email)]
    (if (valid-user? user password)
      (try
        (response (db/transact-new-password! user new-password))
        (catch Exception e
          (error e "error changing password: ")
          (response "Error changing password" 500))
        (finally (update-tx-db!)))
      (response "Wrong old password" 400))))

(defn subscription-activated! [subscription-data]
  (let [email (:SubscriptionReferrer subscription-data)]
    (db/transact-subscription-status! email true)))

(defn subscription-deactivated! [subscription-data]
  (let [email (:SubscriptionReferrer subscription-data)]
    (db/transact-subscription-status! email false)))

;;;;;;;;;;;;;;;;;;;;;;;;

(defroutes routes
           (HEAD "/" [] "")
           (GET "/" [] (landing-page))
           (GET "/blog" [] (redirect "/blog/index.html"))
           (GET "/contact" [] (contact-page))
           (GET "/signup" [] (signup-page))
           (POST "/signup" [email password] (do
                                              (when (nil? (:conn @tx))
                                                (update-tx-conn!))
                                              (update-tx-db!)
                                              (add-user! email password)))
           (GET "/activate/:id" [id] (do
                                       (when (nil? (:conn @tx))
                                         (update-tx-conn!))
                                       (when (nil? (:db @tx))
                                         (update-tx-db!))
                                       (activate-user! id)))
           (GET "/activated/:user" [user] (payment-page user "Account successfully activated!"))

           (GET "/subscription-activated" req (subscription-activated! (:params req)))
           (GET "/subscription-deactivated" req (subscription-deactivated! (:params req)))

           (GET "/app" [] (render-file "app.html" {:dev        (env :dev?)
                                                   :csrf-token *anti-forgery-token*}))
           (POST "/login" [username password] (do
                                                (when (nil? (:conn @tx))
                                                  (update-tx-conn!))
                                                (update-tx-db!)
                                                (jws-login username password)))

           (POST "/store-session" req (if-not (authenticated? req)
                                        (throw-unauthorized)
                                        (add-session! req)))
           (POST "/template" req (if-not (authenticated? req)
                                   (throw-unauthorized)
                                   (add-template! req)))
           (POST "/change-password" req (if-not (authenticated? req)
                                          (throw-unauthorized)
                                          (change-password! req)))
           (GET "/sessions" req (if-not (authenticated? req)
                                  (throw-unauthorized)
                                  (response (db/retrieve-sessions req))))
           (GET "/session/:url" [url] (view-session-page (db/get-session url)))
           (GET "/template" req (if-not (authenticated? req)
                                  (throw-unauthorized)
                                  (let [template-name (:template-name (:params req))
                                        user (:user (:params req))]
                                    (response (db/create-session template-name user)))))
           (GET "/templates" req (if-not (authenticated? req)
                                   (throw-unauthorized)
                                   (response (db/all-template-titles (str (:user (:params req)))))))
           (GET "/movement" req (if-not (authenticated? req)
                                  (throw-unauthorized)
                                  (response (db/entity-by-movement-name (:name (:params req))))))
           (GET "/movement-by-id" req (if-not (authenticated? req)
                                        (throw-unauthorized)
                                        (response (db/entity-by-id (read-string (:entity (:params req)))))))
           (GET "/movements" req (if-not (authenticated? req)
                                   (throw-unauthorized)
                                   (response (db/all-movement-names))))
           (GET "/equipment" req (if-not (authenticated? req)
                                   (throw-unauthorized)
                                   (response (db/all-equipment-names))))
           (GET "/equipment-session" req (if-not (authenticated? req)
                                           (throw-unauthorized)
                                           (let [equipment (:equipment (:params req))]
                                             (response (db/create-equipment-session equipment 5)))))
           (GET "/movement-from-equipment" req (let [e (:equipment (:params req))]
                                                 (if-not (authenticated? req)
                                                   (throw-unauthorized)
                                                   (response (db/get-movement-from-equipment e)))))
           (GET "/singlemovement" req
             (let [categories (vec (vals (:categories (:params req))))]
               (if-not (authenticated? req)
                 (throw-unauthorized)
                 (response (db/get-n-movements-from-categories 1 categories {})))))
           (GET "/categories" req (if-not (authenticated? req)
                                    (throw-unauthorized)
                                    (response (db/all-category-names))))
           (resources "/")
           (not-found "Not Found"))

(def app
  (let [handler (-> routes
                    (wrap-authentication jws-auth-backend)
                    (wrap-authorization jws-auth-backend)
                    (wrap-edn-params)
                    (wrap-params)
                    (wrap-session)
                    (wrap-defaults site-defaults)
                    (wrap-frame-options {:allow-from "http://www.movementsession.com"}))]
    (if (env :dev?)
      (wrap-reload (wrap-exceptions handler))
      handler)))