(ns chromex-sample.popup.core
  (:import [goog.dom query])
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [re-com.buttons :refer [button md-circle-icon-button]]
            [reagent.core :as r]
            [chromex-sample.shared.util :refer [js->clj-keyed js->clj-keyed-first]]
            [chromex-sample.shared.communication :refer [parse-client-message send-message!]]
            [re-com.util :refer [get-element-by-id item-for-id]]))

; -- a message loop ---------------------------------------------------------------------------------------------------------

(defonce window-state (r/atom {}))

(defmulti handle-client-message (fn [[sender message-type payload]] [sender message-type]))

(defmethod handle-client-message [:background :state-update] [[_ _ payload]]
  (println "got new rules from back ground" payload)
  (reset! window-state payload)
  )

(defmethod handle-client-message [:background :ping] [[sender _ message]]
  (println "got ping " message "from " sender))

(defmethod handle-client-message :default [[sender message-type payload]]
  (println "unknown message from " sender " with type " message-type))


(defn run-message-loop! [message-channel]
  (log "POPUP: starting message loop...")
  (go-loop []
    (when-some [message (parse-client-message (<! message-channel))]
      (println "got message " message "from client")
      (handle-client-message message)
      (recur))
    (log "POPUP: leaving message loop")))

(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (send-message! background-port :popup :ping "Hello from popup")
    (run-message-loop! background-port)))


(defn main-cpt []
  [button
   :label "Configure rules"
   :style {:margin-top "10px"}
   :on-click (fn []
               (println "clicked")
               (js/window.open "rules.html"))])

(defn mount []
  (r/render [main-cpt] (get-element-by-id "app")))

(defn init! []
  (log "POPUP:  init")
  (connect-to-background-page!)
  (mount))
