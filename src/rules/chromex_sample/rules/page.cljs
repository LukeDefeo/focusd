(ns chromex-sample.rules.page
  (:require
    [reagent.core :as reagent]
    [reagent.ratom :refer-macros [reaction]]
    [re-com.text :refer [title label]]
    [re-com.buttons :refer [button md-circle-icon-button]]
    [clojure.string :as str]
    [re-com.box :refer [h-box v-box box gap]]
    [re-com.misc :refer [input-text]]
    [re-com.util :refer [get-element-by-id item-for-id]]
    [chromex-sample.shared.communication :refer [parse-client-message send-message!]]
    cljsjs.react-input-autosize))


(defonce *contexts
         (reagent/atom [{:name  "News"
                         :rules [["bbc.co.uk" "sport"]
                                 ["facebook.com" "messages"]]}
                        {:name  "monitoring"
                         :rules [["new-relic"]
                                 ["stack driver"]]}]))


(def double-whitespace-regexp #"[ \t]{2,}")

(defn remove-double-whitespace [text]
  (str/replace-all text double-whitespace-regexp " "))


(defn rule-component [*rule]
  (fn []
    (let [rule @*rule]
      (println rule)
      [h-box
       :gap "10px"
       :children (conj
                   (->>
                     rule
                     (map-indexed
                       (fn [idx val]
                         [box
                          :child
                          [:>
                           js/AutosizeInput
                           {:name        "autosize"
                            :style       {:padding-top "10px"}
                            :value       val
                            :placeholder "type a condition"
                            :on-key-down (fn [e]
                                           (let [keyCode (-> e .-keyCode)]
                                             (println keyCode)
                                             (when (= keyCode 8)
                                               (swap! *rule #(into [] (remove str/blank? %))))))
                            :onChange    (fn [e]
                                           (swap! *rule assoc idx
                                                  (-> e .-target .-value)))}]]))
                     (into []))
                   [md-circle-icon-button
                    :size :smaller
                    :md-icon-name "zmdi-plus"
                    :style {:margin-top "10px"}
                    :on-click (fn [] (when-not (= (last rule) "")
                                       (swap! *rule conj "")))])])))


(defn context-component [*context]
  (fn []
    (let [{:keys [name rules]} @*context]
      [v-box
       :gap "0px"
       :children [[input-text
                   :model name
                   :on-change (fn [text] (swap! *context assoc :name text ))
                   ;:level :level2
                   ;:label name
                   ]
                  (->> (range 0 (count rules))
                       (map (fn [i] [rule-component (reagent/cursor *context [:rules i])])))
                  [button
                   :label "Add rule"
                   :style {:margin-top "10px"}
                   :on-click (fn [] (swap! *context update :rules conj [""]))]]])))


(defn main [*contexts save-fn]
  (fn []
    [v-box
     :style {:background "white"
             :padding    "20px"}
     :children
     [[title :level :level1 :label "Context defintions"]
      (->> (range 0 (count @*contexts))
           (map (fn [i] [context-component (reagent/cursor *contexts [i])])))
      [button
       :label "Add Context"
       :style {:margin-top "10px"}
       :on-click (fn [] (swap! *contexts conj {:name  "new context"
                                               :id (.getTime (js/Date.))
                                               :rules [[""]]}))]
      [button
       :label "Save"
       :style {:margin-top "10px"}
       :on-click (fn [] (save-fn @*contexts))]
      ]]))

(defn mount
  [save-fn]
  (reagent/render [main *contexts save-fn] (get-element-by-id "app")))