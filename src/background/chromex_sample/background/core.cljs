(ns chromex-sample.background.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.string.format]
            [cljs.core.async :refer [<! chan >! close!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.chrome-event-channel :refer [make-chrome-event-channel]]
            [chromex.protocols :refer [get-sender]]
            [chromex.ext.tabs :as tabs]
            [chromex.ext.windows :as windows]
            [chromex.ext.runtime :as runtime]
            [chromex-sample.background.tab-manager :as tm]
            [chromex-sample.shared.util :refer [js->clj-keyed js->clj-keyed-first]]
            [chromex-sample.shared.communication :refer [parse-client-message send-message!]]
            [chromex-sample.background.storage :refer [test-storage!]]))

(def clients (atom []))

; -- clients manipulation ---------------------------------------------------------------------------------------------------

(defn add-client! [client]
  (log "BACKGROUND: client connected" (get-sender client))
  (swap! clients conj client))

(defn remove-client! [client]
  (log "BACKGROUND: client disconnected" (get-sender client))
  (let [remove-item (fn [coll item] (remove #(identical? item %) coll))]
    (swap! clients remove-item client)))


; -- client event loop ------------------------------------------------------------------------------------------------------
(defmulti handle-client-message (fn [[sender message-type message]] [sender message-type]))

(defmethod handle-client-message [:rules :updated] [[_ _ message]]
  (println "got new rules from back ground"))

(defmethod handle-client-message [:popup :ping] [[_ _ message]]
  (println "got ping " message "from popup" " and keyword is " ::runtime/on-connect))

(defmethod handle-client-message :default [[sender message-type message]]
  (println "unknown message from " sender " with type " message-type))

(defn run-client-message-loop! [client]
  (go-loop []
           (when-some [message (parse-client-message (<! client))]
             (log "BACKGROUND: got client message:" message "from" (get-sender client))
             (handle-client-message message)
             (recur))
           (remove-client! client)))

(defn handle-client-connection! [client]
  (add-client! client)
  (println "connection from " (get-sender client))
  (send-message! client :background :ping "hello")
  (send-message! client :background :state-update @tm/window-state)
  (run-client-message-loop! client))

; -- main event loop --------------------------------------------------------------------------------------------------------

(defn process-chrome-event [event-num event]
  (go
    (let [[event-id event-args] event]
      (case event-id
        ::runtime/on-connect (apply handle-client-connection! event-args)
        ::windows/on-removed (tm/handle-closed-window! (first event-args))
        ::tabs/on-updated (<! (tm/process-updated-tab! event-args))
        nil))))

(defn run-chrome-event-loop! [chrome-event-channel]
  (log "BACKGROUND: starting main event loop...")
  (go-loop [event-num 1]
           (when-some [event (<! chrome-event-channel)]
             (<! (process-chrome-event event-num event))
             (recur (inc event-num)))
           (log "BACKGROUND: leaving main event loop")))

(defn boot-chrome-event-loop! []
  (let [chrome-event-channel (make-chrome-event-channel (chan))]
    (tabs/tap-on-updated-events chrome-event-channel)
    (windows/tap-on-removed-events chrome-event-channel)
    (runtime/tap-all-events chrome-event-channel)
    (run-chrome-event-loop! chrome-event-channel)))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (log "BACKGROUND: init")
  (test-storage!)
  (boot-chrome-event-loop!))
