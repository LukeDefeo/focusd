(ns chromex-sample.popup.page
  (:require
    [reagent.core :as reagent]
    [reagent.ratom :refer-macros [reaction]]
    [re-com.text :refer [title label]]
    [re-com.buttons :refer [button md-circle-icon-button]]
    [clojure.string :as str]
    [re-com.box :refer [h-box v-box box gap]]
    [re-com.util :refer [get-element-by-id]]))


(defn wrap-in-div [highlighted? click-Handler comp]
  [:div {:style    {:background-color (if highlighted? "red" "white")}
         :on-click click-Handler} comp])

(def down-arrow 40)
(def up-arrow 38)
(def enter 13)


(defn cycle-up [total-items cur]
  (let [max-idx (dec total-items)]
    (cond
      (= cur 0) max-idx
      :else (dec cur))))


(defn cycle-down [total-items cur]
  (let [max-idx (dec total-items)]
    (cond
      (= cur max-idx) 0
      :else (inc cur))))


(defn list-comp [items render-fn selected-fn]
  (let [state (reagent/atom {:index 0})]
    (fn [items render-fn selected-fn]
      (let [{:keys [index]} @state]
        [v-box
         :attr {:id          "list-box"
                :tab-index   0
                :on-key-down (fn [key]
                               (let [code (-> key .-keyCode)]
                                 (condp = code
                                   down-arrow (swap! state update :index (partial cycle-down (count items)))
                                   up-arrow (swap! state update :index (partial cycle-up (count items)))
                                   enter (selected-fn (nth items index))
                                   nil)))}
         :children
         (map-indexed
           (fn [cur-idx item]
             (let [handler (fn [_]
                             ;update highlight
                             (swap! state assoc :index cur-idx)
                             (selected-fn item))]
               (->> item render-fn (wrap-in-div (= index cur-idx) handler))))
           items)]))))


(defn render-context [{:keys [name]}]
  [:div {:style {:width "300px"}} name])


(defn rule-link []
  [button
   :label "Configure rules"
   :style {:margin-top "10px"}
   :on-click (fn []
               (println "clicked")
               (js/window.open "rules.html"))])

(defn main [contexts selected-fn]
  [v-box
   :children
   [[list-comp
     contexts
     render-context
     selected-fn]
    [rule-link]
    ]] )

(defn mount
  [contexts selected-fn]
  (reagent/render
    [main contexts selected-fn] (get-element-by-id "app"))
  (.focus (get-element-by-id "list-box")))


