(ns chromex-sample.rules.core
  (:import [goog.dom query])
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [<!]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [chromex-sample.rules.page :as page]
            [chromex-sample.shared.util :refer [js->clj-keyed js->clj-keyed-first]]
            [chromex-sample.shared.communication :refer [parse-client-message send-message!]]))


(def *background-port (atom nil))

(defmulti handle-client-message (fn [[sender message-type payload]] [sender message-type]))

(defmethod handle-client-message [:background :state-update] [[_ _ payload]]
  (println "resetting state")
  (reset! page/*contexts payload))

(defmethod handle-client-message [:background :ping] [[_ _ payload]]
  (println "got ping " payload "from background"))

(defmethod handle-client-message :default [[sender message-type payload]]
  (println "unknown message from " sender " with type " message-type))

(defn run-message-loop! [message-channel]
  (log "RULES: starting message loop...")
  (go-loop []
           (when-some [message (parse-client-message (<! message-channel))]
             (println "got message " message "from background")
             (handle-client-message message)
             (recur))
           (log "RULES: leaving message loop")))

(defn connect-to-background-page! []
  (let [background-port (runtime/connect)]
    (reset! *background-port background-port)
    (run-message-loop! background-port)))

(defn mount []
  (page/mount (fn [contexts]
                (send-message! @*background-port :rules :state-update contexts))))

(defn init! []
  (log "RULES: init")
  (connect-to-background-page!)
  (mount))
