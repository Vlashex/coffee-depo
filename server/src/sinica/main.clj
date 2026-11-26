(ns sinica.main
  (:require [sinica.depot :as depot]
            [sinica.tcp :as tcp])
  (:gen-class))

(defn -main [& [port]]
  (tcp/serve! depot/game (Integer/parseInt (or port "4040"))))
