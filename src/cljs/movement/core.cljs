(ns movement.core
  (:require [reagent.core :as reagent :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [cljsjs.react :as react]
            [clojure.string :as str]
            [movement.movements :refer [warmup mobility hanging equilibre strength
                                        locomotion bas sass leg-strength auxiliary
                                        movnat movnat-warmup
                                        m-styrke m-oppvarming m-kombinasjon]])
  (:import goog.History))

(enable-console-print!)

(def default-buttons {:ritual     "button"
                      :strength   "button"
                      :mobility   "button"
                      :locomotion "button"
                      :bas        "button"
                      :sass       "button"
                      :leg        "button"
                      :movnat     "button"
                      :maya       "button"})

(def buttons (atom default-buttons))

(defn button-selected! [button]
  (swap! buttons assoc button "button button-primary"))

(defn prep-name [kw]
  (str/replace (str/capitalize (name kw)) "-" " "))

(defn set-button-selected! [button]
  (do
    (reset! buttons default-buttons)
    (button-selected! button)))

(defonce session (atom {}))
(swap! session assoc :categories (sorted-map))
(swap! session assoc :movements (sorted-map))

(defonce m-counter (atom 0))
(defonce c-counter (atom 0))

(defn add-title! [title]
  (swap! session assoc :title title))

(defn add-timestamp! [t]
  (swap! session assoc :date t))

(defn add-category! [title category]
  (let [id (swap! c-counter inc)]
    (swap! session assoc-in [:categories id] {:id id :title title :category category})))

(defn add-movement! [title category-id]
  (let [id (swap! m-counter inc)]
    (swap! session assoc-in [:movements id] {:id id :title title :category-ref category-id
                                             :text "Tight core, retract shoulders, neutral head."
                                             :animation ". . . . ."})))

(defn update! [kw id title] (swap! session assoc-in [kw id :title] title))

(defn delete! [kw id] (swap! session update-in [kw] dissoc id))

(defn refresh! [id category] (update! :movements id (prep-name (first (take 1 (shuffle category))))))

(defn reset-session! []
  (do
    (reset! session {})
    (swap! session assoc :categories (sorted-map))
    (swap! session assoc :movements (sorted-map))
    (reset! c-counter 0)
    (reset! m-counter 0)))

(defn movement-input [{:keys [title on-save on-stop]}]
  (let [val (atom title)
        stop #(do (reset! val "")
                  (if on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
               (if-not (empty? v) (on-save v))
               (stop))]
    (fn [props]
      [:input (merge props
                     {:type "text" :value @val :on-blur save
                      :on-change #(reset! val (-> % .-target .-value))
                      :on-key-down #(case (.-which %)
                                     13 (save)
                                     27 (stop)
                                     nil)})])))

(def movement-edit (with-meta movement-input
                              {:component-did-mount #(.focus (reagent/dom-node %))}))

(defn movement-item []
  (let [editing (atom false)]
    (fn [{:keys [id title category-ref text animation]}]
      [:div.row {:class (str (if @editing "editing"))}
       [:label.three.columns {:on-double-click #(reset! editing true)} title]
       [:label.three.columns text]
       [:label.three.columns animation]
       [:button.one.column.button.button-primary
        {:on-click #(refresh! id (:category (get (:categories @session) category-ref)))}]
       [:button.one.column.destroy {:on-click #(delete! :movements id)}]
       (when @editing
         [movement-edit {:class   "edit" :title title
                         :on-save #(update! :movements id %)
                         :on-stop #(reset! editing false)}])])))

(defn category-item []
  (fn [{:keys [id title]} movements]
    [:div
     [:h3 title]
     (when (-> movements count pos?)
       (for [m movements]
         ^{:key (:id m)} [movement-item m]))
     [movement-input {:id          "new-movement"
                      :placeholder "Add movement.."
                      :on-save     #(add-movement! %1 id)}]]))

(defn home-page []
  [:div
   [:div.section.buttons
    [:div.container
     [:h1 "Movement Session"]
     [:p "Select a template and be inspired by randomly generated movements."]
     [:div.row
      [:div.three.columns
       {:type     "button"
        :class    (:ritual @buttons)
        :on-click #(do
                    (reset-session!)
                    (set-button-selected! :ritual)
                    (add-title! "Morning Ritual")
                    (let [date (js/Date.)]
                      (add-timestamp! (str (.getDate date) "/" (+ 1 (.getMonth date)))))
                    (add-category! "Warmup" warmup)
                    (dotimes [n 1] (add-movement! (prep-name (nth (take 1 (shuffle warmup)) n)) 1))
                    (add-category! "Mobility" mobility)
                    (dotimes [n 5] (add-movement! (prep-name (nth (take 5 (shuffle mobility)) n)) 2))
                    (add-category! "Hanging" hanging)
                    (dotimes [n 1] (add-movement! (prep-name (nth (take 1 (shuffle hanging)) n)) 3))
                    (add-category! "Equilibre" equilibre)
                    (dotimes [n 1] (add-movement! (prep-name (nth (take 1 (shuffle equilibre)) n)) 4))
                    (add-category! "Strength" strength)
                    (dotimes [n 1] (add-movement! (prep-name (nth (take 1 (shuffle strength)) n)) 5)))}
       "Morning ritual"]
      [:div.three.columns
       {:type     "button"
        :class    (:strength @buttons)
        :on-click #(do
                    (reset-session!)
                    (set-button-selected! :strength)
                    (add-category! "Warmup" warmup)
                    (dotimes [n 1] (add-movement! (prep-name (nth (take 1 (shuffle warmup)) n)) 1))
                    (add-category! "Mobility" mobility)
                    (dotimes [n 6] (add-movement! (prep-name (nth (take 6 (shuffle mobility)) n)) 2))
                    (add-category! "Strength" strength)
                    (dotimes [n 4] (add-movement! (prep-name (nth (take 4 (shuffle strength)) n)) 3)))
        }
       "Strength"]
      [:div.three.columns
       {:type     "button"
        :class    (:mobility @buttons)
        :on-click #(do
                    (reset-session!)
                    (set-button-selected! :mobility)
                    (add-category! "Warmup" warmup)
                    (dotimes [n 1] (add-movement! (prep-name (nth (take 1 (shuffle warmup)) n)) 1))
                    (add-category! "Mobility" mobility)
                    (dotimes [n 6] (add-movement! (prep-name (nth (take 6 (shuffle mobility)) n)) 2))
                    (add-category! "Prehab" mobility)
                    (dotimes [n 4] (add-movement! (prep-name (nth (take 4 (shuffle mobility)) n)) 3)))}
       "Mobility/Prehab"]
      [:div.three.columns
       {:type     "button"
        :class    (:locomotion @buttons)
        :on-click #(do
                    (reset-session!)
                    (set-button-selected! :locomotion)
                    (add-category! "Warmup" warmup)
                    (dotimes [n 1] (add-movement! (prep-name (nth (take 1 (shuffle warmup)) n)) 1))
                    (add-category! "Mobility" mobility)
                    (dotimes [n 6] (add-movement! (prep-name (nth (take 6 (shuffle mobility)) n)) 2))
                    (add-category! "Locomotion" locomotion)
                    (dotimes [n 6] (add-movement! (prep-name (nth (take 6 (shuffle locomotion)) n)) 3)))}
       "Locomotion"]]
     [:div.row
      [:div.three.columns
       {:type     "button"
        :class    (:bas @buttons)
        :on-click #(do
                    (reset-session!)
                    (set-button-selected! :bas)
                    (add-category! "Warmup" warmup)
                    (dotimes [n 1] (add-movement! (prep-name (nth (take 1 (shuffle warmup)) n)) 1))
                    (add-category! "Mobility" mobility)
                    (dotimes [n 6] (add-movement! (prep-name (nth (take 6 (shuffle mobility)) n)) 2))
                    (add-category! "Bent Arm Strength" bas)
                    (dotimes [n 5] (add-movement! (prep-name (nth (take 5 (shuffle bas)) n)) 3)))}
       "BAS"]
      [:div.three.columns
       {:type     "button"
        :class    (:sass @buttons)
        :on-click #(do
                    (reset-session!)
                    (set-button-selected! :sass)
                    (add-category! "Warmup" warmup)
                    (dotimes [n 1] (add-movement! (prep-name (nth (take 1 (shuffle warmup)) n)) 1))
                    (add-category! "Mobility" mobility)
                    (dotimes [n 6] (add-movement! (prep-name (nth (take 6 (shuffle mobility)) n)) 2))
                    (add-category! "Straight Arm Scapular Strength" sass)
                    (dotimes [n 4] (add-movement! (prep-name (nth (take 4 (shuffle sass)) n)) 3)))}
       "SASS"]
      [:div.three.columns
       {:type     "button"
        :class    (:leg @buttons)
        :on-click #(do
                    (reset-session!)
                    (set-button-selected! :leg)
                    (add-category! "Warmup" warmup)
                    (dotimes [n 1] (add-movement! (prep-name (nth (take 1 (shuffle warmup)) n)) 1))
                    (add-category! "Mobility" mobility)
                    (dotimes [n 6] (add-movement! (prep-name (nth (take 6 (shuffle mobility)) n)) 2))
                    (add-category! "Leg Strength" leg-strength)
                    (dotimes [n 3] (add-movement! (prep-name (nth (take 3 (shuffle leg-strength)) n)) 3))
                    (add-category! "Auxiliary" auxiliary)
                    (dotimes [n 2] (add-movement! (prep-name (nth (take 2 (shuffle auxiliary)) n)) 4)))}
       "Leg strength"]
      [:div.three.columns
       {:type     "button"
        :class    (:movnat @buttons)
        :on-click #(do
                    (reset-session!)
                    (set-button-selected! :movnat)
                    (add-category! "Warmup Mobility (3 rounds)" movnat-warmup)
                    (dotimes [n 3] (add-movement! (prep-name (nth (take 3 (shuffle movnat-warmup)) n)) 1))
                    (add-category! "Skill (30 reps)" movnat)
                    (dotimes [n 1] (add-movement! (prep-name (nth (take 1 (shuffle movnat)) n)) 2))
                    (add-category! "Combo (4 rounds)" movnat)
                    (dotimes [n 4] (add-movement! (prep-name (nth (take 4 (shuffle movnat)) n)) 3)))}
       "MovNat"]]
     [:div.row
      [:div.three.columns
       {:type     "button"
        :class    (:maya @buttons)
        :on-click #(do
                    (reset-session!)
                    (set-button-selected! :maya)
                    (add-category! "Oppvarming/Bevegelighet (2 runder rolig)" m-oppvarming)
                    (dotimes [n 3] (add-movement! (prep-name (nth (take 3 (shuffle m-oppvarming)) n)) 1))
                    (add-category! "Styrke/Ferdighet (30 reps)" m-styrke)
                    (dotimes [n 1] (add-movement! (prep-name (nth (take 1 (shuffle m-styrke)) n)) 2))
                    (add-category! "Kombinasjon (3 runder hurtig)" m-kombinasjon)
                    (dotimes [n 4] (add-movement! (prep-name (nth (take 4 (shuffle m-kombinasjon)) n)) 3)))}
       "Maya"]]]]
   [:div.section.movements
    [:div.container
     (let [session @session
           categories (vals (:categories session))
           movements (vals (:movements session))]
       (when (-> categories count pos?)
         [:div.row
          [:h2 (str (:title session) " - " (:date session))]
          (for [c categories]
            ^{:key (:id c)} [category-item
                             c
                             (filter
                               #(= (:id c) (:category-ref %))
                               movements)])]))]]
   [:div.footer
    [:div.container
     [:em "If you have suggestions for a new session template, some sorely missing movements
     or general improvements (such as adding users and allowing you to add your own
     templates): let your wishes be known by sending an email to movementsession@gmail.com"]]]])

(defn user-page []
  [:div
   [:div.container
    [:p "this will be the user page"]]])

;-------------
(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Client side routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
                    (session/put! :current-page #'home-page))

(secretary/defroute "/user" []
                    (session/put! :current-page #'user-page))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
      EventType/NAVIGATE
      (fn [event]
        (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn mount-root []
  (reagent/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (hook-browser-navigation!)
  (mount-root))


