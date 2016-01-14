(ns movement.explore
  (:require [reagent.core :refer [atom]]
            [reagent.session :as session]
            [reagent-forms.core :refer [bind-fields]]
            [cljs.core.async :as async :refer [timeout <!]]
            [secretary.core :include-macros true :refer [dispatch!]]
            [movement.menu :refer [menu-component]]
            [movement.util :refer [handler-fn text-input GET POST get-templates]]
            [movement.text :refer [text-input-component auto-complete-did-mount]]
            [movement.generator :refer [image-url add-movement-from-search]]
            [movement.components.creator :refer [heading title description error]]
            [movement.template :refer [template-creator-component]]
            [movement.routine :refer [routine-creator-component]]
            [movement.group :refer [group-creator-component]]
            [movement.plan :refer [plan-creator-component]]
            [clojure.string :as str]
            [cljs.reader :refer [read-string]]))

(def explore-state (atom {:number-of-results 8
                          :menu-selection :temp}))

(defn results-slider []
  (let [temp-data (atom (:number-of-results @explore-state))]
    (fn []
      [:div
       [:div.pure-g
        [:div.pure-u.pure-u-md-3-5 (str "Max number of results: " @temp-data)]]
       [:div.pure-g
        [:input.pure-u.pure-u-md-3-5 {:type        "range"
                                      :value       @temp-data :min 1 :max 30 :step 1
                                      :on-mouse-up #(swap! explore-state assoc :number-of-results @temp-data)
                                      :on-change   #(reset! temp-data (-> % .-target .-value))}]
        [:a.pure-u.pure-u-md-1-5 {:style    {:margin-left 5}
                                  :on-click #(do
                                              (reset! temp-data 1000)
                                              (swap! explore-state assoc :number-of-results 1000))} "all"]]])))

(defn select-buttons []
  (let []
    (fn [explore-state]
      [:div {:style {:margin-top '40}}
       [:div.pure-g
        [:div.pure-u-1-2.pure-u-md-1-5.button {:className (when (= :movements (:menu-selection @explore-state)) "button-primary")
                                               :on-click  #(swap! explore-state assoc :menu-selection :movements)} "Movements"]
        [:div.pure-u-1-2.pure-u-md-1-5.button {:className (when (= :templates (:menu-selection @explore-state)) "button-primary")
                                               :on-click  #(swap! explore-state assoc :menu-selection :templates)} "Templates"]
        [:div.pure-u-1-2.pure-u-md-1-5.button {:className (when (= :groups (:menu-selection @explore-state)) "button-primary")
                                               :on-click  #(swap! explore-state assoc :menu-selection :groups)} "Groups"]
        [:div.pure-u-1-2.pure-u-md-1-5.button {:className (when (= :plans (:menu-selection @explore-state)) "button-primary")
                                               :on-click  #(swap! explore-state assoc :menu-selection :plans)} "Plans"]
        #_[:div.pure-u-1-2.pure-u-md-1-5.button {:className (when (= :routines (:menu-selection @explore-state)) "button-primary")
                                               :on-click  #(swap! explore-state assoc :menu-selection :routines)} "Routines"]]])))

(defn movements-component []
  (let []
    (fn []
      [:div {:style {:margin-top '20}}
       [:div.pure-g
        [:div.pure-u.pure-u-md-1-5
         [:div.pure-g
          [:h3.pure-u "Categories"]]
         (doall (for [c (sort (session/get :all-categories))]
                  ^{:key c}
                  [:div.pure-g {:style {:cursor     'pointer
                                        :color (when (= c (:selected-category @explore-state)) "#fffff8")
                                        :background-color (when (= c (:selected-category @explore-state)) "gray")}}
                   [:span.pure-u-1
                    {:on-click #(GET "movements-by-category"
                                     {:params        {:n        (:number-of-results @explore-state)
                                                      :category c}
                                      :handler       (fn [r] (do
                                                               (swap! explore-state assoc :selected-category c)
                                                               (swap! explore-state assoc :movements r)))
                                      :error-handler (fn [r] (pr (str "error getting movements by category: " r)))})} c]]))]
        [:div.pure-u.pure-u-md-4-5
         [:div.pure-g
          [:div.pure-u.pure-u-md-1-2
           [results-slider]]
          [:div.pure-u.pure-u-md-1-2
           (let [id (str "explore-mtags")
                 movements-ac-comp (with-meta text-input-component
                                              {:component-did-mount #(auto-complete-did-mount
                                                                      (str "#" id)
                                                                      (vec (session/get :all-movements)))})]
             [movements-ac-comp {:id          id
                                 :class       "edit"
                                 :placeholder "Search for movements"
                                 :size        80
                                 :on-save     #(when (some #{%} (session/get :all-movements))
                                                (GET "movement"
                                                     {:params        {:name (str %)}
                                                      :handler       (fn [r] (swap! explore-state assoc
                                                                                    :movements [r]
                                                                                    :selected-category nil))
                                                      :error-handler (pr "error getting single movement through add.")}))}])]]
         (when-not (nil? (:movements @explore-state))
           [:div.pure-g
            [:div.pure-u-1 (str "Showing " (count (:movements @explore-state)) " results")]])
         (let [movements (:movements @explore-state)]
           [:div.pure-g.movements
            (doall
              (for [m movements]
                ^{:key (rand-int 10000)}
                (let [name (:movement/unique-name m)]
                  [:div.pure-u.movement.small.is-center
                   [:h3.pure-g
                    [:div.pure-u-1-12]
                    [:div.pure-u.title name]]
                   [:img.graphic.small-graphic.pure-img-responsive {:src (image-url name) :title name :alt name}]
                   [:div {:style {:margin-bottom 10}}]])))])]]])))

(defn groups-component []
  (let []
    (fn []
      [:div {:style {:margin-top '20}}
       "Searching groups"])))

(defn plans-component []
  (let []
    (fn []
      [:div {:style {:margin-top '20}}
       "Searching plans"])))

(defn templates-component []
  (let []
    (fn []
      [:div {:style {:margin-top '20}}
       "Searching templates"])))

(defn routines-component []
  (let []
    (fn []
      [:div {:style {:margin-top '20}}
       "Searching routines"])))

(defn explore-component []
  (let []
    (fn []
      [:div#layout {:class (str "" (when (session/get :active?) "active"))}
       [menu-component]
       [:div.content {:style {:margin-top '20}}
        (heading "Explore")
        [select-buttons explore-state]
        (case (:menu-selection @explore-state)
          :movements [movements-component]
          :templates [templates-component]
          :groups [groups-component]
          :plans [plans-component]
          ;:routines [routines-component]
          "")]])))