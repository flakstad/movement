(ns movement.nav
  (:require [secretary.core :include-macros true :refer [dispatch!]]))

(defn nav-component []
  (let []
    (fn []
      [:div
       [:section#header
        [:header#header
         [:h1 "Movement Session"]]]
       [:section#nav
        [:button.button {:on-click #(dispatch! "/")} "Session Generator"]
        [:button.button {:on-click #(dispatch! "/user")} "User Profile"]
        [:button.button {:on-click #(dispatch! "/template")} "Template Creator"]
        [:button.button {:on-click #(dispatch! "/movements")} "Movement Explorer"]]])))

(defn about-component []
  [:div
   [:div.container
    [nav-component]
    [:section
     [:div "movementsession@gmail.com"]]]])