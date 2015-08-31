(ns movement.generator
  (:require
    [reagent.session :as session]
    [reagent.core :refer [atom]]
    [clojure.string :as str]
    [cljs.reader :refer [read-string]]
    [cljs.core.async :refer [<! chan close!]]
    [movement.text :refer [text-edit-component]]
    [movement.menu :refer [menu-component]]
    [movement.state :refer [movement-session handler-fn log-session GET POST]])
  (:require-macros
    [cljs.core.async.macros :refer [go]]))

(defn equipment-symbol [equipment-name]
  "images/sketch-push-up.png")

(defn positions
  "Finds the positions of elements in a collection."
  [pred coll]
  (keep-indexed
    (fn [idx x]
      (when (pred x)
        idx))
    coll))

(defn get-new-movement [part-title]
  (go
    (let [movements (atom [])
          parts (session/get-in [:movement-session :parts])
          position-in-parts (first (positions #{part-title} (map :title parts)))
          categories (:categories (first (filter #(= part-title (:title %)) parts)))
          categories-prepped (mapv #(str/replace % " " "-") categories)]
      (doseq [c categories-prepped]
        (swap! movements conj (first (read-string (<! (GET (str "singlemovement/" c)))))))
      (session/update-in! [:movement-session :parts position-in-parts :movements]
                          conj (first (shuffle @movements))))))

(defn remove-movement [m part-title]
  (go
    (let [parts (session/get-in [:movement-session :parts])
          position-in-parts (first (positions #{part-title} (map :title parts)))
          movement-list (session/get-in [:movement-session :parts position-in-parts :movements])
          movement-list (remove #{m} movement-list)]
      (session/assoc-in! [:movement-session :parts position-in-parts :movements] movement-list))))

(defn movement-component []
  (let []
    (fn [{:keys [id category graphic animation equipment rep set distance duration] :as m} part-title]
      (let [name (:movement/name m)
            rep 10
            set 3
            graphic "images/sketch-push-up.png"]
        [:div.pure-u.movement

         [:div.pure-g
          [:div.pure-u-1-2.refresh {:on-click #()}]
          [:div.pure-u-1-2.destroy {:on-click #(remove-movement m part-title)}]]

         [:div.pure-g.title
          [:span.pure-u name]]

         [:div.pure-g
          [:img.pure-u.graphic.pure-img-responsive {:src graphic}]]


         [:div.pure-g

          [:div.pure-u-1-4.sw
           [:div.pure-g
            [:span.pure-u.rep-text "Rep"]]
           [:div.pure-g
            [:span.pure-u.rep rep]]]

          [:div.pure-u-1-2
           [:div.pure-g
            [:img.pure-u.icon {:src (equipment-symbol equipment)}]
            [:img.pure-u.icon {:src (equipment-symbol equipment)}]
            ]]

          [:div.pure-u-1-4.se
           [:div.pure-g
            [:span.pure-u.set-text "Set"]]
           [:div.pure-g
            [:span.pure-u.set set]]]]

         ]))))

(defn part-component []
  (let []
    (fn [{:keys [title categories movements]}]
      [:div
       [:h2 title]
       [:div.pure-g
        (for [m movements]
          ^{:key (str m (rand-int 10000))} [movement-component m title])]
       [:button {:type     "submit"
                 :on-click #(get-new-movement title)} "+"]])))


(defn session-component []
  (let [adding-description (atom false)]
    (fn [{:keys [title description parts]}]
      [:div#session
       [:div.pure-g
        [:h1.pure-u.pure-u-md-4-5 title]
        [:label.pure-u.pure-u-md-1-5 (str (.getDay (js/Date.)) "/" (.getMonth (js/Date.)))]]
       [:div description]
       (when @adding-description
         [text-edit-component {:class   "edit" :title description
                               :on-save #(handler-fn (session/assoc-in! [:movement-session :description] %))
                               :on-stop #(handler-fn (reset! adding-description false))}])
       [:button {:type     "submit"
                 :on-click #(handler-fn (reset! adding-description true))} "!"]
       (for [p parts]
         ^{:key p} [part-component p])
       [:div.pure-g
        [:button.pure-u-1-3 {:type     "submit"
                             :on-click log-session}
         "Log this movement session"]
        [:button.pure-u-1-3 {:on-click #()} "Share"]
        [:button.pure-u-1-3 {:on-click #()} "Make PDF"]]])))

(defn template-component []
  (let []
    (fn [template-name]
      [:div.pure-u-1-3.pure-u-md-1-8
       {:on-click #(go
                    (let [session (read-string
                                    (<! (GET (str "template/" (str/replace template-name " " "-")))))]
                      (session/put! :movement-session session)))}
       template-name])))

(defn generator-component []
  (let []
    (fn []
      [:div#layout {:class (str "" (when (session/get :active?) "active"))}

       [menu-component]

       [:div#main
        [:div.header
         [:h1 "Movement Session"]

         [:div.pure-g
          (doall
            (for [t (session/get :templates)]
              ^{:key t} [template-component t]))]]

        [:div.content
         (when (not (nil? (session/get :movement-session)))
           [session-component (session/get :movement-session)])]]])))