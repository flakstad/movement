(ns movement.generator
  (:import [goog.events EventType])
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [reagent.session :as session]
    [reagent.core :refer [atom]]
    [cljs.core.async :as async :refer [timeout <!]]
    [cljs.reader :as reader]
    [goog.events :as events]
    [clojure.string :as str]
    [movement.util :refer [handler-fn positions GET POST get-plans get-ongoing-plan
                           get-stored-sessions get-groups]]
    [movement.text :refer [text-edit-component text-input-component auto-complete-did-mount]]
    [movement.menu :refer [menu-component]]
    [movement.components.login :refer [footer]]))

(defonce m-counter (atom 0))

(defn image-url [name]
  (when-not (nil? name)
    (str "images/movements/" (str/replace (str/lower-case name) " " "-") ".png")))

(defn add-movement [part-title]
  (let [parts (session/get-in [:movement-session :parts])
        position-in-parts (first (positions #{part-title} (map :title parts)))
        part (session/get-in [:movement-session :parts position-in-parts])
        movements (:movements part)
        part (apply dissoc part (for [[k v] part :when (nil? v)] k))
        part (dissoc part :title :movements)]
    (GET "singlemovement"
         {:params        {:part part
                          :email (session/get :email)}
          :handler       #(let [id (swap! m-counter inc)
                                new-movement (assoc % :id id)
                                new-movements (assoc movements id new-movement)]
                           (session/assoc-in! [:movement-session :parts position-in-parts :movements] new-movements))
          :error-handler #(pr "error getting single movement through add.")})))

(defn refresh-movement
  ([m part-title]
   (let [parts (session/get-in [:movement-session :parts])
         position-in-parts (first (positions #{part-title} (map :title parts)))
         part (session/get-in [:movement-session :parts position-in-parts])
         movements (:movements part)
         part (apply dissoc part (for [[k v] part :when (nil? v)] k))
         part (dissoc part :title :movements)]
     (GET "singlemovement"
          {:params        {:part part
                           :email (session/get :email)}
           :handler       #(let [id (:id m)
                                 new-movement (assoc % :id id)
                                 new-movements (assoc movements id new-movement)]
                            (session/assoc-in! [:movement-session :parts position-in-parts :movements] new-movements))
           :error-handler #(pr "error getting single movement through refresh.")})))
  ([m part-title new-difficulty]
   (let [parts (session/get-in [:movement-session :parts])
         position-in-parts (first (positions #{part-title} (map :title parts)))
         movements (session/get-in [:movement-session :parts position-in-parts :movements])
         part (session/get-in [:movement-session :parts position-in-parts])
         part (apply dissoc part (for [[k v] part :when (nil? v)] k))
         part (dissoc part :title :movements)]
     (when-let [id (:db/id (first (shuffle (new-difficulty m))))]
       (GET "movement-by-id"
            {:params        {:email (session/get :email)
                             :id id
                             :part part}
             :handler       #(let [id (:id m)
                                   new-movement %
                                   new-movement (assoc new-movement :id id)
                                   new-movements (assoc movements id new-movement)]
                              (session/assoc-in! [:movement-session :parts position-in-parts :movements] new-movements))
             :error-handler #(pr (str "error: " %))})))))

(defn add-movement-from-search [part-title movement-name]
  (let [parts (session/get-in [:movement-session :parts])
        position-in-parts (first (positions #{part-title} (map :title parts)))
        movements (session/get-in [:movement-session :parts position-in-parts :movements])
        part (session/get-in [:movement-session :parts position-in-parts])
        part (apply dissoc part (for [[k v] part :when (nil? v)] k))
        part (dissoc part :title :movements)]
    (GET "movement"
         {:params        {:email (session/get :email)
                          :name (str movement-name)
                          :part part}
          :handler        #(let [id (swap! m-counter inc)
                                new-movement %
                                new-movement (assoc new-movement :id id)
                                new-movements (assoc movements id new-movement)]
                           (session/assoc-in! [:movement-session :parts position-in-parts :movements] new-movements))
          :error-handler #(pr "error getting single movement through add.")})))

(defn remove-movement [m part-title]
  (let [parts (session/get-in [:movement-session :parts])
        position-in-parts (first (positions #{part-title} (map :title parts)))
        movements (session/get-in [:movement-session :parts position-in-parts :movements])
        movements (dissoc movements (:id m))]
    (session/assoc-in! [:movement-session :parts position-in-parts :movements] movements)))

(defn list-to-sorted-map [list-of-movements]
  (let [movements (atom (sorted-map))]
    (doseq [m list-of-movements
            :let [id (swap! m-counter inc)]]
      (swap! movements assoc id (assoc m :id id)))
    @movements))

(defn add-session-handler [session]
  (let [new-parts (mapv #(assoc % :movements (list-to-sorted-map (:movements %)))
                        (:parts session))]
    (session/put! :movement-session (assoc session :parts new-parts))))

(defn create-session-from-template [id]
  (GET "template"
       {:params        {:template-id id
                        :email (session/get :email)}
        :handler       add-session-handler
        :error-handler (fn [r] (pr r))}))

(defn create-session-from-group [group]
  (GET "group"
       {:params        {:group group
                        :email (session/get :email)}
        :handler       add-session-handler
        :error-handler (fn [] (pr "error getting group session data from server."))}))

(defn create-session-from-equipment [equipment-name]
  (GET "equipment-session"
       {:params        {:equipment equipment-name
                        :user      (session/get :user)}
        :handler       add-session-handler
        :error-handler (fn [e] (pr (str "error getting session data from server: " e)))}))

(defn pick-random-template []
  (create-session-from-template (:db/id (first (shuffle (session/get :templates))))))

;;;;;; Components ;;;;;;
(defn slider-component []
  (let [data (atom 0)]
    (fn [position-in-parts id r min max step]
      [:div.pure-g
       [:div.pure-u-1-5 @data]
       [:input.pure-u-4-5
        {:type        "range" :value @data :min min :max max :step step
         :style       {:width "100%"}
         :on-mouse-up #(session/assoc-in!
                        [:movement-session :parts position-in-parts :movements id r] (int @data))
         :on-change   #(reset! data (-> % .-target .-value))}]])))

(defn movement-component
  [{:keys [id unique name category measurement easier harder description zone
           rep set distance duration weight rest practical] :as m}
   title categories]
  (let [name (if (nil? unique) name unique)
        graphic (image-url name)
        parts (session/get-in [:movement-session :parts])
        position-in-parts (first (positions #{title} (map :title parts)))
        rep-clicked? (atom false)
        set-clicked? (atom false)
        distance-clicked? (atom false)
        duration-clicked? (atom false)
        weight-clicked? (atom false)
        rest-clicked? (atom false)]
    (fn []
      [:div.pure-u.movement.center {:id (str "m-" id)}
       [:div.pure-g
        [:div.pure-u-1-5.refresh
         [:i.fa.fa-random {:on-click #(refresh-movement m title) :title "Swap with another movement"}]]
        [:div.pure-u-1-5.refresh
         (when easier
           [:i.fa.fa-arrow-down {:on-click #(refresh-movement m title :easier) :title "Swap with easier progression"}])]
        [:div.pure-u-1-5.refresh
         (when harder
           [:i.fa.fa-arrow-up {:on-click #(refresh-movement m title :harder) :title "Swap with harder progression"}])]
        [:div.pure-u-1-5]
        [:div.pure-u-1-5.destroy
         [:i.fa.fa-remove {:on-click #(remove-movement m title) :title "Remove movement"}]]]
       [:div.pure-g
        [:div.pure-u-1-5]
        [:h3.pure-u-3-5 name]]
       [:div.pure-g
        [:div.pure-u-1.center {:style {:margin-top -10 :color 'gray :opacity 0.8}}
         (cond
           (= :zone/one zone) [:div {:title "You're still in the learning phase with this movement"}
                               [:i.fa.fa-star] [:i.fa.fa-star-o] [:i.fa.fa-star-o]]
           (= :zone/two zone) [:div {:title "You know this movement well, but it is not perfected. You're effective, but not efficient."}
                               [:i.fa.fa-star] [:i.fa.fa-star] [:i.fa.fa-star-o]]
           (= :zone/three zone) [:div {:title "You have mastered this movement. You are both effective and efficient."}
                                 [:i.fa.fa-star] [:i.fa.fa-star] [:i.fa.fa-star]])]]
       [:div.pure-g
        [:div.pure-u-1
         [:div.center
          [:img.graphic.pure-img-responsive {:src graphic :title name :alt name}]]]]

       [:div {:style {:cursor 'pointer}}
        [:div.pure-g
         [:div.pure-u-1-3.center {:on-click  #(handler-fn (reset! rep-clicked? (not @rep-clicked?)))
                                  :className (str (when-not (and rep (< 0 rep)) " no-data")
                                                  (when @rep-clicked? " selected"))} "Reps"]
         [:div.pure-u-1-3.center {:on-click  #(handler-fn (reset! set-clicked? (not @set-clicked?)))
                           :className (str (when-not (and set (< 0 set)) " no-data")
                                           (when @set-clicked? " selected"))} "Set"]
         [:div.pure-u-1-3.center {:on-click  #(handler-fn (reset! rest-clicked? (not @rest-clicked?)))
                                  :className (str (when-not (and rest (< 0 rest)) " no-data")
                                                  (when @rest-clicked? " selected"))} "Rest"]]
        [:div.pure-g
         [:div.pure-u-1-3.rep-set.center {:on-click #(handler-fn (reset! rep-clicked? (not @rep-clicked?)))}
          (when (and rep (< 0 rep)) rep)]
         [:div.pure-u-1-3.rep-set.center {:on-click #(handler-fn (reset! set-clicked? (not @set-clicked?)))}
          (when (and set (< 0 set)) set)]
         [:div.pure-u-1-3.rep-set.center {:on-click #(handler-fn (reset! rest-clicked? (not @rest-clicked?)))}
          (when (and rest (< 0 rest)) rest)]]]
       [:div {:style {:cursor 'pointer :margin-bottom 10}}
        [:div.pure-g
         [:div.pure-u-1-3.center {:on-click  #(handler-fn (reset! distance-clicked? (not @distance-clicked?)))
                                  :className (str (when-not (and distance (< 0 distance)) " no-data"))} "Meters"]
         [:div.pure-u-1-3.center {:on-click  #(handler-fn (reset! duration-clicked? (not @duration-clicked?)))
                                  :className (str (when-not (and duration (< 0 duration)) " no-data"))} "Seconds"]
         [:div.pure-u-1-3.center {:on-click  #(handler-fn (reset! weight-clicked? (not @weight-clicked?)))
                                  :className (str (when-not (and weight (< 0 weight)) " no-data"))} "Weight"]]
        [:div.pure-g

         [:div.pure-u-1-3.rep-set.center {:on-click #(handler-fn (reset! distance-clicked? (not @distance-clicked?)))}
          (when (and distance (< 0 distance)) distance)]
         [:div.pure-u-1-3.rep-set.center {:on-click #(handler-fn (reset! duration-clicked? (not @duration-clicked?)))}
          (when (and duration (< 0 duration)) duration)]
         [:div.pure-u-1-3.rep-set.center {:on-click #(handler-fn (reset! weight-clicked? (not @weight-clicked?)))}
          (when (and weight (< 0 weight)) weight)]]]
       (when @rep-clicked?
         [slider-component position-in-parts id :rep 0 50 1])
       (when @set-clicked?
         [slider-component position-in-parts id :set 0 20 1])
       (when @rest-clicked?
         [slider-component position-in-parts id :rest 0 240 10])
       (when @distance-clicked?
         [slider-component position-in-parts id :distance 0 400 5])
       (when @duration-clicked?
         [slider-component position-in-parts id :duration 0 1800 10])
       (when @weight-clicked?
         [slider-component position-in-parts id :weight 0 200 2.5])])))

(defn add-movement-component []
  (let [show-search-input? (atom false)]
    (fn [title i]
      [:div.pure-u.movement.search
       [:div.pure-g.add-movement
        [:div.pure-u-2-5]
        [:div.pure-u-1-5
         [:i.fa.fa-plus.fa-2x
          {:on-click #(add-movement title)
           :style    {:cursor 'pointer}}]]]
       (if @show-search-input?
         [:div.pure-g.add-movement
          [:div.pure-u
           (let [id (str "mtags" i)
                 movements-ac-comp (with-meta text-input-component
                                              {:component-did-mount #(auto-complete-did-mount
                                                                      (str "#" id)
                                                                      (vec (session/get :all-movements)))})]
             [movements-ac-comp {:id          id
                                 :class       "edit"
                                 :placeholder "type to find and add movement.."
                                 :size        28
                                 :auto-focus  true
                                 :on-save     #(when (some #{%} (session/get :all-movements))
                                                (do
                                                  (reset! show-search-input? false)
                                                  (add-movement-from-search title %)))}])]]
         [:div.pure-g.add-movement
          [:div.pure-u-2-5]
          [:div.pure-u-1-5
           [:i.fa.fa-search-plus.fa-2x
            {:on-click #(handler-fn (reset! show-search-input? true))
             :style    {:cursor 'pointer}}]]])])))

(defn part-component []
  (let []
    (fn [{:keys [title movements categories]} i]
      [:div
       [:h2 title]
       [:div.pure-g.movements
        (for [m (vals movements)]
          ^{:key (str m (rand-int 100000))} [movement-component m title categories])
        (when-not (empty? categories)
          [add-movement-component title i])]])))

(defn header-component []
  (let [months {0 "January" 1 "February" 2 "March" 3 "April" 4 "May" 5 "June"
                6 "July" 7 "August" 8 "September" 9 "October" 10 "November" 11 "December"}
        date (js/Date.)
        day (.getDate date)
        month (get months (.getMonth date))]
    (fn [{:keys [title description]}]
      [:div {:style {:margin-top 50}}
       [:div.pure-g
        [:div.pure-u.pure-u-md-2-5]
        [:div.pure-u-1.pure-u-md-1-5.center (str month " " day)]
        [:div.pure-u.pure-u-md-2-5]]
       [:div.pure-g
        [:div.pure-u.pure-u-md-1-5]
        [:h1.pure-u-1.pure-u-md-3-5 title]
        [:p.pure-u.pure-u-md-1-5]]
       (when-not (nil? description)
         [:div.pure-g
          [:div.pure-u.pure-u-md-1-9]
          [:p.pure-u-1.pure-u-md-7-9.subtitle description]
          [:div.pure-u.pure-u-md-1-9]])])))

(defn template-component [t]
  [:div.pure-u.button.button-primary {:on-click #(create-session-from-template (:db/id t))
                                      :style    {:margin "0 0 5px 5px"}} (:template/title t)])

(defn group-component [group]
  [:a.pure-u.button.button-primary {:on-click #(create-session-from-group (:group/title group))
                                    :style    {:margin "0 0 5px 5px"}} (:group/title group)])

(defn plan-component [plan]
  [:div.pure-g {:style {:margin-bottom 10 :padding-bottom 10 :border-bottom "dotted 1px"}}
   [:div.pure-u-1-2
    [:div.pure-g
     [:p.pure-u-1 (:plan/title plan)]]
    [:div.pure-g
     [:span.pure-u-1 (str (count (:plan/day plan)) " day plan")]]]
   [:p.pure-u.button.button-primary
    {:on-click #(POST "begin-plan"
                      {:params        {:email (session/get :email)
                                       :id    (:db/id plan)}
                       :handler       (fn [r] (session/put! :ongoing-plan plan))
                       :error-handler (fn [r] (pr r))})} "Begin plan"]])

(defn blank-state-component []
  (let [templates-showing? (atom false)
        groups-showing? (atom false)
        plans-showing? (atom false)]
    (fn []
      [:div.blank-state
       [:div.pure-g.center {:style {:margin-bottom 50}}
        [:h1.pure-u-1 "Create your next Movement Session"]]
       [:div.pure-g
        [:div.pure-u.pure-u-md-1-8]
        [:div.pure-u-1.pure-u-md-3-4
         (when-let [plan (session/get :ongoing-plan)]
           [:div.pure-g
            [:div.pure-u-1.button
             {:style    {:margin-bottom 5}
              :on-click #(GET "next-session-from-plan"
                              {:params        {:email (session/get :email)}
                               :handler       add-session-handler
                               :error-handler (fn [r] (pr "error getting session data from server."))})}
             (str "Continue " (:plan/title plan))]])
         [:div.pure-g
          [:div.pure-u-1.button.button-primary {:style    {:margin-bottom 5}
                                                :on-click pick-random-template} "From random template"]]
         [:div.pure-g
          [:div.pure-u-1.button {:style    {:margin-bottom 5}
                                 :on-click #(handler-fn
                                             (do
                                               (when groups-showing?
                                                 (reset! groups-showing? false))
                                               (when plans-showing?
                                                 (reset! plans-showing? false))
                                               (reset! templates-showing? (not @templates-showing?))))} "From template"]]
         (when @templates-showing?
           [:div.pure-g.animated.fadeIn {:style {:margin "20px 0 20px 0"}}
            (doall
              (for [t (session/get :templates)]
                ^{:key (:db/id t)} (template-component t)))])
         [:div.pure-g
          [:div.pure-u-1.button {:style    {:margin-bottom 5}
                                 :on-click #(handler-fn
                                             (do
                                               (when templates-showing?
                                                 (reset! templates-showing? false))
                                               (when plans-showing?
                                                 (reset! plans-showing? false))
                                               (reset! groups-showing? (not @groups-showing?))))} "From group"]]
         (when @groups-showing?
           [:div.pure-g.animated.fadeIn {:style {:margin "20px 0 20px 0"}}
            (doall
              (for [e (session/get :groups)]
                ^{:key (:db/id e)} (group-component e)))])
         (when (and (< 0 (count (session/get :plans)))
                    (nil? (session/get :ongoing-plan)))
           [:div.pure-g
            [:div.pure-u-1.button {:style    {:margin-bottom 5}
                                   :on-click #(handler-fn
                                               (do
                                                 (when groups-showing?
                                                   (reset! groups-showing? false))
                                                 (when templates-showing?
                                                   (reset! templates-showing? false))
                                                 (reset! plans-showing? (not @plans-showing?))))} "Begin a new plan"]])
         (when (and @plans-showing? (nil? (session/get :ongoing-plan)))
           [:div.pure-g.animated.fadeIn {:style {:margin "20px 0 20px 0"}}
            [:div.pure-u-1
             (doall
               (for [e (session/get :plans)]
                 ^{:key (:db/id e)} (plan-component e)))]])]
        [:div.pure-u.pure-u-md-1-8]]])))

(defn top-menu-component []
  (let [templates-showing? (atom false)
        groups-showing? (atom false)]
    (fn []
      [:div
       [:div.pure-g
        [:a.pure-u.pure-u-md-1-5 {:style    {:text-decoration 'underline
                                             :text-align      'right
                                             :margin-right    5}
                                  :on-click #(session/remove! :movement-session)}
         "Clear session"]
        [:div.pure-u.pure-u-md-1-5.button.button-primary {:style    {:margin-right 5}
                                                          :on-click pick-random-template}
         "Random"]
        [:div.pure-u.pure-u-md-1-5.button {:style    {:margin-right 5}
                                           :on-click #(handler-fn
                                                       (do
                                                         (when groups-showing?
                                                           (reset! groups-showing? false))
                                                         (reset! templates-showing? (not @templates-showing?))))}
         "Select"]
        [:div.pure-u.pure-u-md-1-5.button {:style    {:margin-right 5}
                                           :on-click #(handler-fn
                                                       (do
                                                         (when templates-showing?
                                                           (reset! templates-showing? false))
                                                         (reset! groups-showing? (not @groups-showing?))))}
         "Group"]
        ]
       (when @templates-showing?
         [:div.pure-g.animated.fadeIn {:style {:margin-top '20}}
          (doall
            (for [t (session/get :templates)]
              ^{:key (:db/id t)}
              [:div.pure-u.button.button-primary {:on-click #(do
                                                              (go (<! (timeout 500))
                                                                  (reset! templates-showing? false))
                                                              (create-session-from-template (:db/id t)))
                                                  :style    {:margin "0 0 5px 5px"}} (:template/title t)]))])
       (when @groups-showing?
         [:div.pure-g.animated.fadeIn {:style {:margin-top '20}}
          (doall
            (for [g (session/get :groups)]
              ^{:key (:db/id g)}
              [:div.pure-u.button.button-primary {:on-click #(do
                                                              (go (<! (timeout 500))
                                                                  (reset! groups-showing? false))
                                                              (create-session-from-group (:group/title g)))
                                                  :style    {:margin "0 0 5px 5px"}} (:group/title g)]))])])))

(defn time-comment-component []
  (let [adding-time (atom false)
        adding-comment (atom false)]
    (fn []
      [:div
       [:div.pure-g
        [:div.pure-u.pure-u-md-1-5]
        [:div.pure-u.pure-u-md-3-5.center
         [:i.fa.fa-clock-o.fa-4x {:style    {:cursor 'pointer :opacity 0.5 :margin-right 10}
                                  :on-click #(handler-fn (reset! adding-time (not @adding-time)))}]
         [:i.fa.fa-comment-o.fa-4x {:style    {:cursor 'pointer :opacity 0.5}
                                    :on-click #(handler-fn (reset! adding-comment (not @adding-comment)))}]
         (when @adding-time
           [:div
            [:div.pure-g
             [:div.pure-u.pure-u-md-1-3]
             [:label.pure-u-1-2.pure-u-md-1-6 "minutes"]
             [:label.pure-u-1-2.pure-u-md-1-6 "seconds"]
             [:div.pure-u.pure-u-md-1-3]]
            [:div.pure-g
             [:div.pure-u.pure-u-md-1-3]
             [:input.pure-u-1-2.pure-u-md-1-6 {:type      "number"
                                               :value     (session/get-in [:movement-session :time :minutes])
                                               :min       0
                                               :on-change #(try
                                                            (let [value (-> % .-target .-value)]
                                                              (session/assoc-in! [:movement-session :time :minutes] value))
                                                            (catch js/Error e
                                                              (pr (str "Caught exception: " e))))}]
             [:input.pure-u-1-2.pure-u-md-1-6 {:type      "number"
                                               :value     (session/get-in [:movement-session :time :seconds])
                                               :min       0
                                               :on-change #(try
                                                            (let [value (-> % .-target .-value)]
                                                              (session/assoc-in! [:movement-session :time :seconds] value))
                                                            (catch js/Error e
                                                              (pr (str "Caught exception: " e))))}]
             [:div.pure-u.pure-u-md-1-3]]])]
        [:div.pure-u.pure-u-md-1-5]]
       [:div.pure-g {:style {:margin-bottom 20}}
        [:div.pure-u.pure-u-md-1-5]
        [:div.pure-u.pure-u-md-3-5.center
         (when @adding-comment
           [:div.pure-g {:style {:margin-top 5}}
            [:div.pure-u.pure-u-md-1-5]
            [:div.pure-u.pure-u-md-3-5
             [:textarea {:rows      4
                         :cols      80
                         :on-change #(session/assoc-in! [:movement-session :comment] (-> % .-target .-value))
                         :value     (session/get-in [:movement-session :comment])}]]
            [:div.pure-u.pure-u-md-1-5]])]
        [:div.pure-u.pure-u-md-1-5]]

       ])))

(defn finish-session-component []
  (let [finish-button-clicked? (atom false)
        session-stored-successfully? (atom false)]
    (fn []
      (if @session-stored-successfully?
        (let []
          (go (<! (timeout 3000))
              (session/remove! :movement-session)
              (reset! finish-button-clicked? false)
              (reset! session-stored-successfully? false))
          [:div.pure-g
           [:div.pure-u-1.center {:style {:color "green" :font-size 24}} "Session stored successfully!"]])
        (if @finish-button-clicked?
          [:div.pure-g
           [:div.pure-u.pure-u-md-1-5]
           [:div.pure-u-1-1.pure-u-md-3-5.button.button-secondary
            {:on-click #(let [min (session/get-in [:movement-session :time :minutes])
                              min (when-not (nil? min) (int (reader/read-string min)))
                              sec (session/get-in [:movement-session :time :seconds])
                              sec (when-not (nil? sec) (int (reader/read-string sec)))]
                         (session/assoc-in! [:movement-session :time] (+ (* 60 min) sec))
                         (POST "store-session"
                               {:params        {:session (session/get :movement-session)
                                                :user    (session/get :user)}
                                :handler       (fn [response] (do
                                                                (reset! session-stored-successfully? true)
                                                                (get-stored-sessions)))
                                :error-handler (fn [response] (pr response))}))}
            "Confirm Finish Session"]
           [:div.pure-u.pure-u-md-1-5]]
          [:div.pure-g
           [:div.pure-u.pure-u-md-1-5]
           [:div.pure-u-1-1.pure-u-md-3-5.button.button-primary
            {:on-click #(handler-fn (reset! finish-button-clicked? true))}
            "Finish Movement Session"]
           [:div.pure-u.pure-u-md-1-5]])))))

(defn plan-completed []
  [:div
   [top-menu-component]
   [:div {:style {:margin-top 50}}
    [:div.pure-g
     [:div.pure-u.pure-u-md-1-5]
     [:h1.pure-u-1.pure-u-md-3-5 "Plan completed!"]
     [:p.pure-u.pure-u-md-1-5]]
    [:div.pure-g
     [:div.pure-u.pure-u-md-1-9]
     [:p.pure-u-1.pure-u-md-7-9.subtitle "Congratulations on completing your plan!
              All the data from your training is stored in the database.
              We're hard at work to create a statistics page where you soon can explore the details of your training."]
     [:div.pure-u.pure-u-md-1-9]]]])

(defn generator-component []
  (let []
    (fn []
      [:div#layout {:class (str "" (when (session/get :active?) "active"))}
       [menu-component]
       [:div.content {:style {:margin-top "20px"}}
        (if-let [session (session/get :movement-session)]
          (if (:plan-completed? session)
            (do
              (get-ongoing-plan)
              (plan-completed))
            [:div
             [top-menu-component]
             [:div.session
              [header-component session]
              (let [parts (:parts session)]
                (doall
                  (for [i (range (count parts))]
                    ^{:key i} [part-component (get parts i) i])))
              (when-not (= "A Rest Day" (:title session))
                [:div
                 [time-comment-component]
                 [finish-session-component]])]]
            [blank-state-component]))]])))