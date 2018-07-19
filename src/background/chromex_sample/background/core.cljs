(ns chromex-sample.background.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.string.format]
            [cljs.core.async :refer [<! chan >! close!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.chrome-event-channel :refer [make-chrome-event-channel]]
            [chromex.protocols :refer [get-sender]]
            [chromex.ext.commands :as commands]
            [chromex.ext.tabs :as tabs]
            [chromex.ext.windows :as windows]
            [chromex.ext.runtime :as runtime]
            [chromex-sample.background.tab-manager :as tm]
            [chromex-sample.shared.util :refer [js->clj-keyed js->clj-keyed-first]]
            [chromex-sample.shared.communication :refer [parse-client-message send-message!]]
            [chromex-sample.background.storage :refer [store-contexts <fetch-contexts]]
            [clojure.string :as str]))


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
(defmulti handle-client-message (fn [[sender message-type payload]] [sender message-type]))

(defmethod handle-client-message [:rules :state-update] [[_ _ contexts]]
  (reset! tm/*contexts contexts)
  (store-contexts contexts)
  (println "got new contexts from back ground"))

(defmethod handle-client-message [:popup :clean-ctx] [[_ _ _]]
  (println "Will clean current context")
  (tm/<clean-current-context))

(defmethod handle-client-message [:popup :ping] [[_ _ message]]
  (println "got ping " message "from popup" " and keyword is " ::runtime/on-connect))

(defmethod handle-client-message :default [[sender message-type payload]]
  (println "unknown message from " sender " with type " message-type))

(defn run-client-message-loop! [client]
  (go-loop []
    (when-some [message (parse-client-message (<! client))]
      (log "BACKGROUND: got client message:" message "from" (get-sender client))
      (handle-client-message message)
      (recur))
    (remove-client! client)))


(defn get-sender-id [client]
  (if (str/ends-with? (:url (js->clj-keyed (get-sender client))) "popup.html")
    :popup
    :rules))

(defn handle-client-connection! [client]
  (add-client! client)
  (let [sender-id (get-sender-id client)]
    (println "connection from wat?" sender-id)
    (if (= sender-id :rules)
      (do
        (send-message! client :background :ping "hello")
        (send-message! client :background :state-update @tm/*contexts))
      (do
        (send-message! client :background :ping "hello")
        (send-message! client :background :state-update (tm/get-context-switcher-state)))))

  (run-client-message-loop! client))

; -- main event loop --------------------------------------------------------------------------------------------------------


(defn process-chrome-event [event]
  (go
    (let [[event-id event-args] event]
      (case event-id
        ::runtime/on-connect (apply handle-client-connection! event-args)
        ::windows/on-removed (tm/handle-closed-window! (first event-args))
        ::windows/on-focus-changed (tm/handle-window-focused! (first (js->clj-keyed event-args)))
        ::tabs/on-updated (<! (tm/process-updated-tab! event-args))
        ::commands/on-command (<! (tm/<clean-current-context))
        nil))))

(defn run-chrome-event-loop! [chrome-event-channel]
  (log "BACKGROUND: starting main event loop...")
  (go-loop []
    (when-some [event (<! chrome-event-channel)]
      (<! (process-chrome-event event))
      (recur))
    (log "BACKGROUND: leaving main event loop")))

(defn boot-chrome-event-loop! []
  (let [chrome-event-channel (make-chrome-event-channel (chan))]
    (tabs/tap-on-updated-events chrome-event-channel)
    (windows/tap-on-removed-events chrome-event-channel)
    (windows/tap-on-focus-changed-events chrome-event-channel)
    (commands/tap-on-command-events chrome-event-channel)
    (runtime/tap-all-events chrome-event-channel)
    (run-chrome-event-loop! chrome-event-channel)))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn load-contexts []
  (go (reset! tm/*contexts (or (<! (<fetch-contexts)) []))))

(defn init! []
  (log "BACKGROUND: init")
  (load-contexts)
  (boot-chrome-event-loop!))
