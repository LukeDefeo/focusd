(ns chromex-sample.popup.page
  (:require
    [reagent.core :as reagent]
    [reagent.ratom :refer-macros [reaction]]
    [re-com.core :refer [line]]
    [re-com.text :refer [title label]]
    [re-com.buttons :refer [button md-circle-icon-button]]
    [clojure.string :as str]
    [re-com.box :refer [h-box v-box box gap]]
    [re-com.util :refer [get-element-by-id]]))


(defn wrap-in-div [highlighted? click-Handler comp]
  ;;for some reason when i set background color dynamically based on highlighted? it prevent hover state from working
  ;;seting the class dynamically is a work around
  [:div {:class    (if highlighted? "list-item-highlighted" "list-item")
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


(defn list-component [items render-fn selected-fn]
  (let [state (reagent/atom {:index 0})]
    (fn [items render-fn selected-fn]
      (let [{:keys [index]} @state]
        [v-box
         :attr {:id          "list-component"
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
  [:div {:style {:padding "2px"
                 :width "250px"}} name])

(defn rule-link []
  [:a
   {:tab-index -1                                           ;makes non focusable with keyboard
    :href   "rules.html"
    :target "_blank"
    :title  "Edit context definitions"}
   [:img {:src    "assets/images/settings-50.png"
          :width  "20px"
          :style  {:margin "5px"
                   ;:background-color "red"
                   }
          :height "20px"}]])

(defn clean-ctx [clean-context-fn]
  [:input
   {:tab-index -1                                           ;makes non focusable with keyboard
    :type      "image"
    :src       "assets/images/cross-24.png"
    :title     "Kill tabs not matching context rules"
    :style     {:width  "17px"
                :height "17px"}
    :on-click  clean-context-fn}])


(defn sized-box [child size]
  [box :child child :size size])

(defn top-component [current-ctx clean-context-fn]
  [h-box
   :align :center
   :children
   [[sized-box [:div {:style {:padding "2px"}
                      :title "Current context"} current-ctx] "2 0 auto"]
    [clean-ctx clean-context-fn]
    [rule-link]]])

(defn main [contexts selected-fn clean-context-fn]
  [v-box
   :padding "2px 0 0 0"
   :children
   [[top-component (:name (first contexts)) clean-context-fn]
    [line
     :size "1px"
     :color "#847996"]
    [list-component
     (rest contexts)
     render-context
     selected-fn]]])

(defn mount
  [contexts selected-fn clean-context-fn]
  (reagent/render
    [main contexts selected-fn clean-context-fn] (get-element-by-id "app"))
  (.focus (get-element-by-id "list-component")))


