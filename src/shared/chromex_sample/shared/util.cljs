(ns chromex-sample.shared.util)



(defn js->clj-keyed [js]
  (js->clj js :keywordize-keys true))

(def js->clj-keyed-first (comp first js->clj-keyed))
