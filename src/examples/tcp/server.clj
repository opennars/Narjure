; Modified from Aleph examples
; http://aleph.io/examples/literate.html#aleph.examples.tcp

(ns examples.tcp.server
  (require
    [manifold.deferred :as d]
    [manifold.stream :as s]
    [clojure.edn :as edn]
    [aleph.tcp :as tcp]
    [gloss.core :as gloss]
    [gloss.io :as io]
    [clojure.core.async :refer [>! <! >!! <!! chan go]])
  (:gen-class))

(def protocol (gloss/string :utf-8 :delimiters ["\n"]))

(defn wrap-duplex-stream
  [protocol s]
  (let [out (s/stream)]
    (s/connect
      (s/map #(io/encode protocol %) out)
      s)
    (s/splice
      out
      (io/decode-stream s protocol))))

(defn client
  [host port]
  (d/chain (tcp/client {:host host, :port port})
    #(wrap-duplex-stream protocol %)))

(defn start-server
  [handler port]
  (tcp/start-server
    (fn [s info]
      (handler (wrap-duplex-stream protocol s) info))
    {:port port}))

(defn fast-echo-handler
  [f]
  (fn [s info]
    (s/connect
      (s/map f s)
      s)))

;@(s/put! c 1)
;@(s/take! c)
