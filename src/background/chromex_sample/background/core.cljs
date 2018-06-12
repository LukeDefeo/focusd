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
  (if (or (str/starts-with? url "https://www.bbc.co.uk") (str/starts-with? url "http://testcontext.org"))
    :news
    nil))


(defn js->clj-keyed [js]
  (js->clj js :keywordize-keys true))


(def js->clj-keyed-first (comp first js->clj-keyed))

(defn <create-window [context-id]
  (go
    (let [{:keys [id] :as window} (js->clj-keyed-first (<! (windows/create)))] ;;for some reason its a vecto,r
      (swap! window-state assoc context-id id)
      (println "new state " @window-state)
      window)))

(defn <context-id->window-state [context]
  (go
    (if-let [window-id (get @window-state context)]
      [(js->clj-keyed-first (<! (windows/get window-id))) :existing]
      [(<! (<create-window context)) :new])))

(defn <move-tab-to-context [{:keys [url id]} context-id]
  (go
    (let [current-window (js->clj-keyed-first (<! (windows/get-last-focused)))
          [dest-window state] (<! (<context-id->window-state context-id))
          _ (println "current window " current-window "\ndest window " dest-window "\nstate is" state)]

      (if (not= (:id dest-window) (:id current-window))
        (do
          (println "tab with " url "will be moved to " (:id dest-window))
          (<! (tabs/move id (clj->js {:windowId (:id dest-window) :index -1})))
          (when (= :new state) (<! (tabs/remove (-> dest-window :tabs first :id))))
          (<! (windows/update (:id dest-window) (clj->js {:focused true})))
          (<! (tabs/update id (clj->js {:active true}))))
        (println "not moving tab")))))

(defn process-updated-tab! [[_ _ event]]
  (go
    (let [{:keys [url] :as event} (js->clj-keyed event)
          context-id (url->context-id url)]
      (when context-id
        (<! (<move-tab-to-context event context-id))))))

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
  (go
    (let [[event-id event-args] event]
      (case event-id
        ::runtime/on-connect (apply handle-client-connection! event-args)
        ::tabs/on-created (tell-clients-about-new-tab!)
        ::windows/on-removed (handle-closed-window! (first event-args))
        ::tabs/on-updated (<! (process-updated-tab! event-args)) ;maybe should be in vector ot prevent nils   ; (process-updated-tab! event-args)
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
    (tabs/tap-all-events chrome-event-channel)
    (windows/tap-on-removed-events chrome-event-channel)
    (runtime/tap-all-events chrome-event-channel)
    (run-chrome-event-loop! chrome-event-channel)))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (log "BACKGROUND: init")
  ;(process-updated-tab-channel!)
  (test-storage!)
  (boot-chrome-event-loop!))
