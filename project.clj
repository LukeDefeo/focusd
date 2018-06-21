(defproject binaryage/chromex-sample "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.238"]
                 [org.clojure/core.async "0.4.474"]
                 [binaryage/chromex "0.6.0"]
                 [binaryage/devtools "0.9.10"]
                 [re-com "1.3.0"]
                 [reagent "0.8.1"],
                 [figwheel "0.5.15"]
                 [cljsjs/react-input-autosize "2.0.0-1"]
                 [environ "1.1.0"]]

  :plugins [[lein-cljsbuild "1.1.7"]
            [lein-figwheel "0.5.15"]
            [lein-shell "0.5.0"]
            [lein-environ "1.1.0"]
            [lein-cooper "1.2.2"]]

  :source-paths ["src/background"
                 "src/popup"
                 "src/rules"]

  :clean-targets ^{:protect false} ["target"
                                    "resources/unpacked/compiled"
                                    "resources/release/compiled"]

  :cljsbuild {:builds {}}                                   ; prevent https://github.com/emezeske/lein-cljsbuild/issues/413

  :profiles {:unpacked
             {:cljsbuild {:builds
                          {:background
                           {:source-paths ["src/background"]
                            :figwheel     true
                            :compiler     {:output-to     "resources/unpacked/compiled/background.js"
                                           :output-dir    "resources/unpacked/compiled/background-temp"
                                           :asset-path    "compiled/background-temp"
                                           :preloads      [devtools.preload figwheel.preload]
                                           :main          chromex-sample.background
                                           :optimizations :none
                                           :source-map    true}}
                           :popup
                           {:source-paths ["src/popup"]
                            :figwheel     true
                            :compiler     {:output-to     "resources/unpacked/compiled/popup.js"
                                           :output-dir    "resources/unpacked/compiled/popup-temp"
                                           :asset-path    "compiled/popup-temp"
                                           :preloads      [devtools.preload figwheel.preload]
                                           :main          chromex-sample.popup
                                           :optimizations :none
                                           :source-map    true}}

                           :rules
                           {:source-paths ["src/rules"]
                            :figwheel     true
                            :compiler     {:output-to     "resources/unpacked/compiled/rules.js"
                                           :output-dir    "resources/unpacked/compiled/rules-temp"
                                           :asset-path    "compiled/rules-temp"
                                           :preloads      [devtools.preload figwheel.preload]
                                           :main          chromex-sample.rules
                                           :optimizations :none
                                           :source-map    true}}
                           }}}

             :checkouts
             ; DON'T FORGET TO UPDATE scripts/ensure-checkouts.sh
             {:cljsbuild {:builds
                          {:background {:source-paths ["checkouts/cljs-devtools/src/lib"
                                                       "checkouts/chromex/src/lib"
                                                       "checkouts/chromex/src/exts"]}
                           :popup      {:source-paths ["checkouts/cljs-devtools/src/lib"
                                                       "checkouts/chromex/src/lib"
                                                       "checkouts/chromex/src/exts"]}

                           :rules      {:source-paths ["checkouts/cljs-devtools/src/lib"
                                                       "checkouts/chromex/src/lib"
                                                       "checkouts/chromex/src/exts"]}}}}


             :figwheel
             {:figwheel {:server-port    6888
                         :server-logfile ".figwheel.log"
                         :repl           true}}

             :disable-figwheel-repl
             {:figwheel {:repl false}}

             :cooper
             {:cooper {"content-dev"     ["lein" "content-dev"]
                       "fig-dev-no-repl" ["lein" "fig-dev-no-repl"]
                       "browser"         ["scripts/launch-test-browser.sh"]}}

             :release
             {:env       {:chromex-elide-verbose-logging "true"}
              :cljsbuild {:builds
                          {:background
                           {:source-paths ["src/background"]
                            :compiler     {:output-to     "resources/release/compiled/background.js"
                                           :output-dir    "resources/release/compiled/background-temp"
                                           :asset-path    "compiled/background-temp"
                                           :main          chromex-sample.background
                                           :optimizations :advanced
                                           :elide-asserts true}}
                           :popup
                           {:source-paths ["src/popup"]
                            :compiler     {:output-to     "resources/release/compiled/popup.js"
                                           :output-dir    "resources/release/compiled/popup-temp"
                                           :asset-path    "compiled/popup"
                                           :main          chromex-sample.popup
                                           :optimizations :advanced
                                           :elide-asserts true}}}}}}

  :aliases {"dev-build"       ["with-profile" "+unpacked,+unpacked-content-script,+checkouts" "cljsbuild" "once"]
            "fig"             ["with-profile" "+unpacked,+figwheel" "figwheel" "background" "popup" "rules"]
            "fig-dev-no-repl" ["with-profile" "+unpacked,+figwheel,+disable-figwheel-repl,+checkouts" "figwheel" "background" "popup"]
            "devel"           ["with-profile" "+cooper" "do" ; for mac only
                               ["shell" "scripts/ensure-checkouts.sh"]
                               ["cooper"]]
            "release"         ["with-profile" "+release" "do"
                               ["clean"]
                               ["cljsbuild" "once" "background" "popup"]]
            "package"         ["shell" "scripts/package.sh"]})
