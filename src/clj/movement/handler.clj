(ns movement.handler
  (:require [clojure.java.io :as io]
            [compojure.core :refer [GET POST ANY defroutes]]
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
            [clj-time.core :as time :refer [from-now hours]]
            [buddy.sign.jws :as jws]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.token :refer [jws-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]

            [movement.db]
            [buddy.hashers :as hashers]))

(def uri "datomic:dev://localhost:4334/movement10")
(def conn (d/connect uri))
(def db (d/db conn))
(selmer.parser/set-resource-path!  (clojure.java.io/resource "templates"))

(defn generate-response [data & [status]]
  {:status  (or status 200)
   :headers {"Content-Type" "application/edn"}
   :body    (pr-str data)})

(defn all-movement-names []
  "Returns the names of all movements in the database."
  (let [movements (d/q '[:find [?n ...]
                         :where
                         [_ :movement/name ?n]]
                       db)]
    (generate-response movements)))

(defn all-category-names []
  "Returns the names of all categories in the database."
  (let [categories (d/q '[:find [?n ...]
                          :where
                         [_ :category/name ?n]]
                       db)]
    (generate-response categories)))

(defn movement [name]
  "Returns the whole entity of a named movement."
  (let [movement (d/pull db '[*] [:movement/name name])]
    movement))

(defn get-movements [n categories]
  "Get n random movement entities drawn from param list of categories."
  (let [movements (for [c categories]
                    (d/q '[:find (pull ?m [*]) :in $ ?cat
                           :where [?c :category/name ?cat] [?m :movement/category ?c]]
                         db c))
        m (->> movements flatten set shuffle (take n))]
    m))

(defn all-template-titles []
  (let [db (d/db conn)
        templates (d/q '[:find [?title ...]
                         :in $ ?email
                         :where
                         [?e :user/email ?email]
                         [?e :user/template ?t]
                         [?t :template/title ?title]]
                       db
                       "bob@bob.com")]
    (generate-response templates)))

(defn create-session [title]
  (let [title-entity (ffirst (d/q '[:find (pull ?t [*])
                                    :in $ ?title ?email
                                    :where
                                    [?e :user/email ?email]
                                    [?e :user/template ?t]
                                    [?t :template/title ?title]]
                                  db
                                  title
                                  "bob@bob.com"))
        description (:template/description title-entity)
        part-entities (map #(d/pull db '[*] %) (vec (flatten (map vals (:template/part title-entity)))))
        parts
        (vec (for [p part-entities]
               (let [name (:part/name p)
                     n (:part/number-of-movements p)
                     c (flatten (map vals (:part/category p)))
                     category-names (vec (flatten (map vals (map #(d/pull db '[:category/name] %) c))))
                     movements (vec (get-movements n category-names))]
                 (if-let [regular-movements (vec (map #(d/pull db '[*] (:db/id %)) (:part/regular-movement p)))]
                   {:title      name
                    :categories category-names
                    :movements  (concat regular-movements movements)}
                   {:title      name
                    :categories category-names
                    :movements  movements}))))
        ]
    (generate-response {:title       title
                        :description description
                        :parts       parts})))



(defn new-unique-template? [user template-title]
  (empty? (d/q '[:find [?user ...]
                 :in $ ?user ?template-title
                 :where
                 [?e :user/email ?user]
                 [?e :user/template ?template]
                 [?template :template/title ?template-title]]
               db
               user
               template-title)))

(defn add-template! [user template]
  (let [title (:title template)
        description (:description template)
        parts (:parts template)
        categories (vec (flatten (for [p parts] (for [c (:categories p)] c))))
        regular-movements (vec (flatten (for [p parts] (for [c (:regular-movements p)] c))))
        part-temp-ids (vec (for [p parts] (d/tempid :db.part/user)))
        category-temp-ids (vec (for [p parts] (for [c (:categories p)] (d/tempid :db.part/user))))
        regular-movement-temp-ids (vec (for [p parts] (for [c (:regular-movements p)] (d/tempid :db.part/user))))
        flat-category-temp-ids (vec (flatten category-temp-ids))
        flat-regular-movement-temp-ids (vec (flatten regular-movement-temp-ids))
        user-template-data [{:db/id         #db/id[:db.part/user]
                             :user/email    user
                             :user/template [#db/id[:db.part/user -100]]}
                            {:db/id                #db/id[:db.part/user -100]
                             :template/title       title
                             :template/description description
                             :template/part        part-temp-ids}]
        part-data (for [i (range (count parts))
                        :let [p (get parts i)
                              cid (get category-temp-ids i)
                              pid (get part-temp-ids i)
                              rid (get regular-movement-temp-ids i)]]
                    {:db/id                    pid
                     :part/name                (:title p)
                     :part/category            (vec cid)
                     :part/number-of-movements (:n p)
                     :part/regular-movement    (vec rid)
                     })
        category-data (vec (for [i (range (count categories))
                                 :let [c (get categories i)
                                       id (get flat-category-temp-ids i)]]
                             {:db/id         id
                              :category/name c}))
        regular-movement-data (vec (for [i (range (count regular-movements))
                                         :let [m (get regular-movements i)
                                               id (get flat-regular-movement-temp-ids i)]]
                                     {:db/id         id
                                      :movement/name m}))
        tx-data (concat user-template-data part-data category-data regular-movement-data)]
    (d/transact conn tx-data)))

(defn store-new-template [req]
  (let [template (:params req)
        user (:user template)]
    (if (nil? user)
      (generate-response (str "User email lacking from client data. User not logged in?" " template: " template) 400)
      ;; todo: thorough check of correct template data on backend as well?
      (if (new-unique-template? user (:title template))
        (do
          (add-template! user template)
          (generate-response "New template stored successfully."))
        (generate-response "You already have a template with this title. Choose a unique title for your template." 400)))))


;;;;;;;;;;;;;;;;;;;;;;

(defn find-user [email]
  (let [db (d/db conn)]
    (d/pull db '[*] [:user/email email])))

(defn valid-user? [user password]
  (hashers/check password (:user/password user)))

(def secret "mysupersecret")

(defn jws-login
  [email password]
  (let [user (find-user email)]
    (if-not (nil? (:db/id user))
      (let [valid? (valid-user? user password)]
        (if valid?
          (let [claims {:user (keyword email)
                        :exp  (-> 3 hours from-now)}
                token (jws/sign claims secret {:alg :hs512})]
            (generate-response {:token token :user (:user/email (dissoc user :user/password :db/id))}))
          (generate-response {:message "wrong password"} 401)))
      (generate-response {:message "unknown email"} 400))))

(def jws-auth-backend (jws-backend {:secret secret :options {:alg :hs512}}))
;;;;;;

(defn add-user [email password]
  (let [tx-user-data [{:db/id #db/id[:db.part/user]
                       :user/email email
                       :user/password (hashers/encrypt password)}]]
    (d/transact conn tx-user-data)))

(defn add-user! [email password]
  (if (nil? (:db/id (find-user email)))
    (do
      (add-user email password)
      (generate-response {:message "user created!"}))
    (generate-response {:message "this email is already registered"} 400)))

;;;;;;;;;;;;;;;;;;;;;;;;

(defroutes routes

           (GET "/" [] (render-file "app.html" {:dev (env :dev?)
                                                :csrf-token *anti-forgery-token*}))

           (POST "/login" [username password] (jws-login username password))
           (POST "/signup" [username password] (add-user! username password))
           (POST "/template" req (store-new-template req))

           (GET "/categories" [] (all-category-names))
           (GET "/movements" [] (all-movement-names))
           (GET "/movement/:name" [name] (movement (str/replace name "-" " ")))
           (GET "/templates" [] (all-template-titles))



           (GET "/template/:title" [title] (create-session (str/replace title "-" " ")))
           (GET "/singlemovement" [categories]
             (generate-response (get-movements 1
                                               (if (not (vector? categories)) [categories]
                                                                              categories))))
           (resources "/")
           (not-found "Not Found")

           (GET "/raw" [] (render-file "indexraw.html" {:dev (env :dev?)}))

           )

(def app
  (let [handler (-> routes
                    (wrap-authentication jws-auth-backend)
                    (wrap-authorization jws-auth-backend)
                    (wrap-edn-params)
                    (wrap-params)
                    (wrap-session)
                    (wrap-defaults site-defaults)
                    (wrap-frame-options {:allow-from (or "http://movementsession.com"
                                                         "http://www.movementsession.com")}))]
    (if (env :dev?)
      (wrap-reload (wrap-exceptions handler))
      handler)))