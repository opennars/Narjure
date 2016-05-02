(defproject narjure "0.1.0-SNAPSHOT"
  :description "A Clojure implementation of the Non-Axiomatic Reasoning System proposed by Pei Wang."
  :url "https://github.com/jarradh/narjure"
  :license {:name "GNU General Public License 2.0"
            :url  "http://www.gnu.org/licenses/old-licenses/gpl-2.0.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.logic "0.8.10"]
                 [instaparse "1.4.1"]
                 [com.rpl/specter "0.9.1"]
                 [org.clojure/tools.nrepl "0.2.12"]
                 [org.clojure/data.priority-map "0.0.7"]
                 [co.paralleluniverse/pulsar "0.7.4"]
                 [org.immutant/immutant "2.1.2"]
                 [clj-time "0.11.0"]
                 [com.taoensso/timbre "4.3.1"]
                 [midje "1.8.3"]]
  :java-agents [[co.paralleluniverse/quasar-core "0.7.4"]]
  :main ^:skip-aot narjure.core
  :plugins [[lein-cloverage "1.0.6"]
            [cider/cider-nrepl "0.11.0-SNAPSHOT"]
            [lein-midje "3.1.3"]]
  :target-path "target/%s"
  :repl-options {:init-ns          narjure.repl
                 :nrepl-middleware [narjure.repl/narsese-handler]}
  :profiles {:uberjar {:aot :all}}
  :jvm-opts ["-Dco.paralleluniverse.fibers.detectRunawayFibers=false -Dco.paralleluniverse.fibers.verifyInstrumentation"])
