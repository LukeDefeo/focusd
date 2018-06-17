(ns chromex-sample.rules.core
  (:import [goog.dom query])
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [reagent.core :as r]))


(defonce window-state (r/atom {}))


(defn run-message-loop! [message-channel]
  (log "RULES: starting message loop...")
  (go-loop []
           (when-some [message (<! message-channel)]
             (println "got message " message "from client")
             (reset! window-state (js->clj message :keywordize-keys true))
             (recur))
           (log "RULES: leaving message loop")))

(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (post-message! background-port "hello from RULES!")
    (run-message-loop! background-port)))

(defn mount []
  (println "i have mounted"))

(defn init! []
  (log "RULES:  init")
  (connect-to-background-page!)
  (mount)
  )
