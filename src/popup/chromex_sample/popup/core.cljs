(ns chromex-sample.popup.core
  (:import [goog.dom query])
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [re-com.buttons :refer [button md-circle-icon-button]]
            [reagent.core :as r]
            [chromex-sample.popup.page :as page]
            [chromex-sample.shared.util :refer [js->clj-keyed js->clj-keyed-first]]
            [chromex-sample.shared.communication :refer [parse-client-message send-message!]]
            [re-com.util :refer [get-element-by-id item-for-id]]
            [chromex.ext.windows :as windows]
            ))

; -- a message loop ---------------------------------------------------------------------------------------------------------

(defonce window-state (r/atom {}))
(defonce *background-port (r/atom nil))

(defmulti handle-client-message (fn [[sender message-type payload]] [sender message-type]))

(defmethod handle-client-message [:background :state-update] [[_ _ payload]]
  (println "got new rules 2 from background" payload)
  (reset! window-state payload)
  (page/mount payload (fn [context]
                        (if-let [id (:window-id context)]
                          (do
                            (println "activating window " id)
                            (windows/update id (clj->js {:focused true})))
                          (println "no window id for context")))
              (fn []
                (send-message! @*background-port :popup :clean-ctx nil)
                (js/window.close))))

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
    (reset! *background-port background-port)
    (send-message! background-port :popup :ping "Hello from popup")
    (run-message-loop! background-port)))


(defn init! []
  (log "POPUP:  init")
  (connect-to-background-page!))
