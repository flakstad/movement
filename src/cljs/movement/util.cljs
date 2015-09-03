(ns movement.util
  (:import goog.History)
  (:require
    [goog.events :as events]
    [goog.history.EventType :as EventType]
    [reagent.session :as session]
    [secretary.core :as secretary
     :include-macros true]
    [ajax.core :as ajax]))

(defn GET1 [url & [opts]]
  (ajax/GET url (update-in opts [:params] assoc :timestamp (.getTime (js/Date.)))))

(defn POST1 [url opts]
  (ajax/POST url opts))

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
      EventType/NAVIGATE
      (fn [event]
        (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn set-page! [page]
  (session/put! :current-page page))