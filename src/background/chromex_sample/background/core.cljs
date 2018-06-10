(ns chromex-sample.background.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.string :as gstring]
            [goog.string.format]
            [clojure.string :as str]
            [cljs.core.async :refer [<! chan >! close!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.chrome-event-channel :refer [make-chrome-event-channel]]
            [chromex.protocols :refer [post-message! get-sender]]
            [chromex.ext.tabs :as tabs]
            [chromex.ext.windows :as windows]
            [chromex.ext.runtime :as runtime]
            [clojure.set :as set]
            [chromex-sample.background.storage :refer [test-storage!]]))



(def window-state (atom {}))                                ;; {:context-id "window-id"}

(def clients (atom []))

; -- clients manipulation ---------------------------------------------------------------------------------------------------

(defn add-client! [client]
  ;(log "BACKGROUND: client connected" (get-sender client))
  (swap! clients conj client))

(defn remove-client! [client]
  ;(log "BACKGROUND: client disconnected" (get-sender client))
  (let [remove-item (fn [coll item] (remove #(identical? item %) coll))]
    (swap! clients remove-item client)))

; -- client event loop ------------------------------------------------------------------------------------------------------

(defn run-client-message-loop! [client]
  ;(log "BACKGROUND: starting event loop for client:" (get-sender client))
  (go-loop []
           (when-some [message (<! client)]
             ;(log "BACKGROUND: got client message:" message "from" (get-sender client))
             (recur))
           ;(log "BACKGROUND: leaving event loop for client:" (get-sender client))
           (remove-client! client)))

; -- event handlers ---------------------------------------------------------------------------------------------------------

(defn handle-client-connection! [client]
  (add-client! client)
  (post-message! client "hello from BACKGROUND PAGE!")
  (run-client-message-loop! client))

(defn tell-clients-about-new-tab! []
  (doseq [client @clients]
    (post-message! client "a new tab was created")))

; -- main event loop --------------------------------------------------------------------------------------------------------

(defn url->context-id [url]
  (if (str/starts-with? url "https://www.bbc.co.uk")
    :news
    :other))


(defn create-window [context-id]
  (go
    (let [_ (swap! window-state assoc context-id :temp)
          id (-> (<! (windows/create (clj->js {:url ["https://www.bbcd.co.uk" "https://www.blah.co.uk"]})))
                 (js->clj :keywordize-keys true)
                 first
                 :id)]                                      ;;for some reason its a vector
      (swap! window-state assoc context-id id)
      (println "created window for context-id " context-id "with id" id)
      (println "new state " @window-state)
      id)))


(defn context-id->window-id [context]
  (go
    (if-let [window-id (get @window-state context)]
      window-id
      (<! (create-window context)))))


(defn process-updated-tab! [[event-id [num status event]]]
  ;(println "got 2 " num)
  ;(println "got 3 " first)
  ;(println "got 4 " second)

  ;(println "got updated event " event)
  (go
    (let [{:keys [url] :as event} (js->clj event :keywordize-keys true)
          dest-window-id (->
                           (url->context-id url)
                           context-id->window-id
                           <!)]
      (println "tab with " url "should go in " dest-window-id)))

  ;(println "url mm?" )
  ;(println "url" (aget event "url"))
  )

(defn window-id->context-id [window-id]
  (get (set/map-invert @window-state) window-id))

(defn handle-closed-window! [window-id]
  (println "window cldosed" window-id)
  (if-let [context-id (window-id->context-id window-id)]
    (do
      (swap! window-state dissoc context-id)
      (println "removed window " window-id " from state for context" context-id))
    (println "unknown window closed"))
  (println "state now" @window-state))

(defn process-chrome-event [event-num event]
  ;(log (gstring/format "BACKGROUND: got chrome event (%05d)" event-num) event)
  (let [[event-id event-args] event]
    (case event-id
      ::runtime/on-connect (apply handle-client-connection! event-args)
      ::tabs/on-created (tell-clients-about-new-tab!)
      ::windows/on-removed (handle-closed-window! (first event-args))
      ::tabs/on-updated (process-updated-tab! event)
      nil)))

(defn run-chrome-event-loop! [chrome-event-channel]
  (log "BACKGROUND: starting main event loop...")
  (go-loop [event-num 1]
           (when-some [event (<! chrome-event-channel)]
             (process-chrome-event event-num event)
             (recur (inc event-num)))
           (log "BACKGROUND: leaving main event loop")))

(defn boot-chrome-event-loop! []
  (let [chrome-event-channel (make-chrome-event-channel (chan))]
    (tabs/tap-all-events chrome-event-channel)
    (windows/tap-on-removed-events chrome-event-channel)
    (runtime/tap-all-events chrome-event-channel)
    (run-chrome-event-loop! chrome-event-channel)))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (log "BACKGROUND: init")
  (test-storage!)
  (boot-chrome-event-loop!))
