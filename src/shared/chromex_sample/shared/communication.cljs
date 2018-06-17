(ns chromex-sample.shared.communication
  (:require
    [chromex-sample.shared.util :refer [js->clj-keyed]]
    [chromex.protocols :refer [post-message! get-sender]]))

;;figwheel doesnt pick up changes in symlinked files, have to restart extension as work around

(defn parse-client-message [js-message]
  (when-let [[sender message-type payload] (js->clj-keyed js-message)]
    [(keyword sender) (keyword message-type) payload]))

(defn send-message! [client sender type payload]
  (post-message! client (clj->js [sender type payload])))