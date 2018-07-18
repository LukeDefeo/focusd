(ns chromex-sample.background.tab-manager
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]
                   [com.rpl.specter :refer [select select-one select-first transform setval select-any]])
  (:require
    [clojure.string :as str]
    [clojure.set :as set]
    [cljs.core.async :refer [<! chan >! close!]]
    [chromex.ext.tabs :as tabs]
    [chromex.ext.windows :as windows]
    [com.rpl.specter :refer [ALL MAP-VALS selected?] :as sp]
    [chromex-sample.shared.util :refer [js->clj-keyed js->clj-keyed-first]]))


(defonce *ordered-windows-state (atom []))
(defonce *context->window-state (atom {}))
(defonce *contexts (atom []))

(defn url-matches-rule?
  "in order for a url to match a rule every condition must be substring of the url"
  [url rule]
  (every? (partial str/includes? url) rule))

(defn url-matches-context?
  "returns nil if no match, if matches returns the number of conditions that were used in the match"
  [url {:keys [rules] :as context}]
  (when-let [match (->> rules
                        (filter (partial url-matches-rule? url))
                        (sort-by count)
                        last)]
    (count match)))

(defn url->context-id [url contexts]
  "looks through all contexts and finds the most specific match (deterimined by the number of conditions)"
  (->>
    contexts
    (map (fn [ctx] [(url-matches-context? url ctx) ctx]))
    (filter (fn [[match _]] match))
    (sort-by first)
    last
    second
    :id))


(defn <create-window-with-tab [context-id tab-id]
  (go
    (let [{:keys [id] :as window} (js->clj-keyed-first (<! (windows/create (clj->js {:tabId tab-id}))))]
      (swap! *context->window-state assoc context-id id)
      (println "new state " @*context->window-state)
      window)))

(defn <context-id->window [context]
  (go
    (when-let [window-id (get @*context->window-state context)]
      (js->clj-keyed-first (<! (windows/get window-id))))))

(defn <move-tab-to-context! [{:keys [url id windowId] :as tab} context-id]
  (go
    (let [dest-window (<! (<context-id->window context-id))]
      (cond
        (nil? dest-window) (<create-window-with-tab context-id id)
        ;We use the tabs current window rather than the users current window since tabs
        ;can get refreshed when they are in the background in the case of google mail notifications
        (= windowId (:id dest-window)) (println "window already in correct context new")
        :else
        (do
          (println "tab with " url "will be moved to " (:id dest-window))
          (<! (tabs/move id (clj->js {:windowId (:id dest-window) :index -1})))
          (<! (tabs/update id (clj->js {:active true}))))))))


(defn process-updated-tab! [[_ _ event]]
  (go
    (let [{:keys [url] :as event} (js->clj-keyed event)
          context-id (url->context-id url @*contexts)]
      (when context-id
        (<! (<move-tab-to-context! event context-id))))))

(defn window-id->context-id [window-id]
  (get (set/map-invert @*context->window-state) window-id))

(defn handle-window-focused! [window-id]
  (swap! *ordered-windows-state #(distinct (cons window-id %)))
  (println @*ordered-windows-state "<<<  windows focused")
  )

(defn handle-closed-window! [window-id]
  (swap! *ordered-windows-state #(remove (partial = window-id) %))
  (if-let [context-id (window-id->context-id window-id)]
    (do
      (swap! *context->window-state dissoc context-id)
      (println "removed window " window-id " from state for context" context-id))
    (println "unknown window closed"))
  (println "state now" @*context->window-state))

(defn join-window-id-to-contexts [contexts window-state ordered-windows]
  (let [inverted-window-state (set/map-invert window-state)

        order-windows-with-ctx-id (map (fn [window-id]
                                         {:id        (get inverted-window-state window-id :unmanaged)
                                          :window-id window-id}) (remove #(= % -1) ordered-windows))]
    (map (fn [{:keys [id window-id]}]

           (let [name (->>
                        contexts
                        (filter (fn [x] (= id (:id x))))
                        first
                        :name)]
             {:window-id window-id
              :name      (or name "Unmanaged")}))
         order-windows-with-ctx-id)))

(defn get-context-switcher-state []
  (drop 1 (join-window-id-to-contexts @*contexts @*context->window-state @*ordered-windows-state)))


(defn clean-current-context []
  (go
    (let [{:keys [id tabs] :as current-window} (js->clj-keyed-first (<! (windows/get-current (clj->js {:populate true}))))

          current-ctx-id (window-id->context-id id)

          current-ctx (select-first [ALL (selected? :id #(= % current-ctx-id))] @*contexts)

          to-kill (->>
                    tabs
                    (remove (fn [tab] (url-matches-context? (:url tab) current-ctx)))
                    (map :id))]

      (when current-ctx
        (tabs/remove (clj->js to-kill))))))

(comment
  ; the problem with the array with window idz approach is that its difficult to add new entrys added from the ui

  ; another option is to have 2 atoms, one with just the array without window id and another which maintains the mapping from window context id to window id
  ; when ui is updated we just replace the array atom.

  ; since we cant pass the id
  (def sample-window-state {12312 "window-id"
                            552   "window-id2"})
  (def sample-contexts [{
                         :id    12312
                         :name  "News2"
                         :rules [["bbc.co.uk/sport" "sport" "sport"]
                                 ["facebook.com" "messages"]]}
                        {:id    552
                         :name  "monitoring"
                         :rules [["new-relic"]
                                 ["stack driver"]]}]))