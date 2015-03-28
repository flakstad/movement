(ns movement.core
    (:require [reagent.core :as reagent :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
              [cljsjs.react :as react])
    (:import goog.History))

(enable-console-print!)

(def warmup-all [:joint-mobility :jump-rope :running])
(def mobility-all [:squat-routine :shoulder-rom-stabilisation
                   :scapula-mobilisation :wrist-prep :ankle-prep
                   :movnat-routine :bridge-rotation
                   :locked-knees-deadlift])
(def hanging [:passive-hang :active-hang :side-to-side-swing
              :arching-active-hang :front-stationary-swing
              :one-arm-passive :one-arm-active :shawerma
              :swing-grip-routine :figure-8])
(def locomotion [:swing-to-handstand :handstand-walk :bridge-walk
                 :duck-walk :horse-walk :lizard-crawl :ostrich-walk])
(def equilibre [:gatherings :wall-walk :wall-kick :handstand-walk
                :handstand-push-up :air-baby :qdr])
(def leg-strength [:basic-squat :back-squat :front-squat :overhead-squat
                   :basic-lunge :back-lunge :front-lunge :overhead-lunge
                   :deadlift :pistols :shrimp :behind-leg
                   :jump-onto-box-standing :jump-onto-box-squatting
                   :explosive-flipping :natural-leg-curl])
(def auxiliry [:l-sit :v-up :sitting-leg-lift :swedish-leg-lift
               :hanging-leg-lift :gatherings :archups])
(def sass [:swedish-bar-hold-front :swedish-bar-hold-back
           :back-lever :front-lever :side-lever :planche :handstand])
(def bas [:push-up-basic :push-up-russian :push-up-wide
          :push-up-diamond :push-up-hindu :push-up-lateral :push-up-bridge
          :push-up-archer :push-up-one-arm :push-up-one-leg-one-arm
          :dips-basic :dips-russian :dips-single-bar :dips-korean :dips-ring
          :dips-ring-wide :dips-ring-archer
          :handstand-push-up-head-wall :handstand-push-up-wall :handstand-push-up-free
          :planche-push-up :pull-up-basic :pull-up-rings :pull-up-rings-wide
          :pull-up-chest :pull-up-waist :pull-up-weighted :pull-up-scapula
          :one-arm-pull-up-forearm :one-arm-pull-up-bicep :one-arm-ring-negative
          :archer-pull-up :one-arm-pull-up-band :one-arm-pull-up-shoulder
          :one-arm-pull-up :row-basic :row-wide :front-lever-row
          :german-hang-pull :pull-over :front-lever-pull :back-lever-pull
          :tick-tock :back-lever-negative :front-lever-negative
          :muscle-up :false-grip-hang :false-grip-pull-up :muscle-up-negative
          :muscle-up-l-sit :rope-climb])
(def strength-all (concat leg-strength auxiliry sass bas))
(def running [:sprint :interval :5K])
(def hiking [])
(def movnat [])
(def movnat-climbing [])
(def movnat-sitting [])
(def swimming [])
(def rock-climbing [])
(def squash [])
(def football [])

(defn create-template [n1 n2 n3]
  (let [a (take n1 (shuffle warmup-all))
        b (take n2 (shuffle mobility-all))
        c (take n3 (shuffle strength-all))]
    (do
      (println a)
      (println b)
      (println c))))

(create-template 1 3 1)

;; -------------------------
;; Views

(defn home-page []
  [:div [:h2 "Welcome to movement"]
   [:div [:a {:href "#/about"} "go to about page"]]])

(defn about-page []
  [:div [:h2 "About movement"]
   [:div [:a {:href "#/"} "go to the home page"]]])

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

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
