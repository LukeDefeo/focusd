(ns chromex-sample.popup.core
  (:import [goog.dom query])
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [re-com.buttons :refer [button md-circle-icon-button]]
            [reagent.core :as r]
            [re-com.util :refer [get-element-by-id item-for-id]]
    ;[re-com.core :as rc]
            ))

; -- a message loop ---------------------------------------------------------------------------------------------------------

(defonce window-state (r/atom {}))

;(defn process-message! [message]
;  )

(defn run-message-loop! [message-channel]
  (log "POPUP: starting message loop...")
  (go-loop []
    (when-some [message (<! message-channel)]
      (println "got message " message "from client")
      (reset! window-state (js->clj message :keywordize-keys true))
      (recur))
    (log "POPUP: leaving message loop")))

(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (post-message! background-port (clj->js [:popup :ping "hello it worksfrom POPUP!"]))
    (run-message-loop! background-port)))

; -- main entry point -------------------------------------------------------------------------------------------------------

;(defn main-cpt []
;  (let [submit-link (build-hn-submit-link)
;        s (list-stories)
;        rs (list-related-stories)]
;    [rc/v-box
;     :size "auto"
;     :children
;     [(if (error?)
;        [cpts/error-cpt (:error @app-state)]
;        (if (loading?)
;          [cpts/loading-cpt]
;          (if (no-results?)
;            [cpts/blank-cpt submit-link]
;            [rc/v-box
;             :size "auto"
;             :gap "10px"
;             :children
;             [[cpts/hn-cpt s rs]
;              [rc/line]
;              (when (repost-allowed? s)
;                [cpts/repost-cpt submit-link])]])))]]))


(defn frame-cpt []
  [button
   :label "Configure rules"
   :style {:margin-top "10px"}
   :on-click (fn []
               (println "clicked")
               (js/window.open "rules.html")
               )]
  ;[:div [:p (str @window-state)]]
  )

(defn mount []
  (r/render [frame-cpt] (get-element-by-id "app")))


(defn init! []
  (log "POPUP:  init")
  ;(println "url ->" (runtime/get-url "rules.html"))
  (connect-to-background-page!)
  (mount))
