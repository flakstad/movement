(ns movement.pages.pricing
  (:require [hiccup.core :refer [html]]
            [hiccup.page :refer [include-css include-js html5]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [movement.pages.components :refer [header footer-2]]))

(defn signup-form []
  [:form.pure-form.pure-form-stacked
   {:method "POST"
    :action "/signup"}
   [:fieldset
    [:input#email {:type        "email"
                   :name        "email"
                   :required    "required"
                   :placeholder "Your Email"}]
    [:input#password {:type        "password"
                      :name        "password"
                      :placeholder "Your Password"
                      :required    "required"}]
    [:input.button-primary {:type  "submit"
                              :value "Sign Up"}]
    (anti-forgery-field)]])

(defn pricing-page [& error-message]
  (html5
    [:head
     [:link {:rel "shortcut icon" :href "images/pull-up.png"}]
     [:title "Pricing Movement Session"]
     (include-js "analytics.js")
     (include-css
       "https://fonts.googleapis.com/css?family=Roboto"
       "https://fonts.googleapis.com/css?family=Raleway"
       "https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css"
       "/css/pure-min.css"
       "/css/grids-responsive-min.css"
       "/css/normalize.css"
       "/css/marketing.css"
       "/css/site.css"
       "/css/pricing.css")]
    [:body
     (header)
     [:div.l-content
      [:div.pricing-tables.pure-g
       [:div.pure-u-1.pure-u-md-1-3]
       [:div.pure-u-1.pure-u-md-1-3
        [:div.pricing-table.pricing-table-biz
         [:div.pricing-table-header
          [:h2 ""]
          [:span.pricing-table-price "$9.95" [:span "per month"]]]
         [:ul.pricing-table-list
          [:li
           (when error-message
             [:div
              [:div.pure-g
               [:div.pure-u-1 error-message]]
              [:div.pure-g
               [:a.pure-u-1.button.button-secondary
                {:title  "Launch app"
                 :href   "/app"
                 :target ""} "Launch app & Log in"]]])
           (signup-form)]]]]
       [:div.pure-u-1.pure-u-md-1-3]]
      [:div.information.pure-g
       [:div.pure-u-1.pure-u-md-1-3
        [:div.l-box
         [:h3.information-head "30 day free trial"]
         [:p "After registering your credit card you have access for 30 days without any fees."]]]
       [:div.pure-u-1.pure-u-md-1-3
        [:div.l-box
         [:h3.information-head "Cancel your plan anytime"]
         [:p "You are free to cancel your subscription at any time. If you cancel within the first 30 days,
         there will be no charges to your credit card."]]]
       [:div.pure-u-1.pure-u-md-1-3
        [:div.l-box
         [:h3.information-head "Customer support"]
         [:p "We will get back to you within 24 hours."]]]]]
     (footer-2)]))