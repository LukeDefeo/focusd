(ns chromex-sample.background.tab-manager
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require
    [clojure.string :as str]
    [clojure.set :as set]
    [cljs.core.async :refer [<! chan >! close!]]
    [chromex.ext.tabs :as tabs]
    [chromex.ext.windows :as windows]
    [chromex-sample.shared.util :refer [js->clj-keyed js->clj-keyed-first]]
    ))


(defonce window-state (atom {}))                                ;; {:context-id "window-id"}

(defn url->context-id [url]
  (if (or (str/starts-with? url "https://www.bbc.co.uk") (str/starts-with? url "http://testcontext.org"))
    :news
    nil))

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