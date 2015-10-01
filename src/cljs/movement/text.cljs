(ns movement.text
  (:require [reagent.core :refer [dom-node atom]]
            [reagent.session :as session]))

(defn auto-complete-did-mount
  "Attaches the jQuery autocomplete functionality to DOM elements."
  [id tags]
  (js/$ (fn []
          (let [                                            ;available-tags (vec (session/get :all-categories))
                ]
            (.autocomplete (js/$ id)
                           (clj->js {:source tags}))))))

(defn text-input-component [{:keys [title on-save on-stop size]}]
  (let [val (atom title)
        stop #(do (reset! val "")
                  (if on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
               (if-not (empty? v) (on-save v))
               (stop))]
    (fn [props]
      [:input (merge props
                          {:type "text"
                           :value @val
                           :on-blur #(do (reset! val (-> % .-target .-value))
                                         (save))
                           :on-change #(reset! val (-> % .-target .-value))
                           :on-key-down #(case (.-which %)
                                          13 (save)
                                          27 (stop)
                                          nil)
                           :size size
                           :autofocus false})])))

(def text-edit-component
  (with-meta text-input-component {:component-did-mount #(do (.focus (dom-node %))
                                                             (auto-complete-did-mount "#tags" nil))}))

(def categories-ac-component
  (with-meta text-input-component {:component-did-mount #(do #_(.focus (dom-node %))
                                                             (auto-complete-did-mount "#tags" (vec (session/get :all-categories))))}))

(def movements-ac-component
  (with-meta text-input-component {:component-did-mount #(let []
                                                          (auto-complete-did-mount "#mtags" (vec (session/get :all-movements))))}))