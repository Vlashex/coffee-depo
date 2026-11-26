(ns sinica.tcp
  (:require [clojure.string :as str])
  (:import [java.io BufferedReader BufferedWriter IOException InputStreamReader OutputStreamWriter]
           [java.net ServerSocket Socket]
           [java.util.concurrent LinkedBlockingQueue]))

(defn- response [message lines]
  (str (str/join "\r\n" (concat [message] lines ["Конец"])) "\r\n"))

(defn- reply! [{:keys [socket outbox]} frame]
  (when-not (.offer outbox frame)
    (.close socket)))

(defn- publish! [{:keys [game world clients output-lock]} client message changed?]
  ;; Пока запрос ждал своей очереди на рассылку, другой уже мог обновить мир.
  ;; Берём свежий снимок под блокировкой, чтобы кадры не пришли в обратном порядке.
  (locking output-lock
    (let [[state sessions] (dosync [@world @clients])
          lines ((:snapshot game) state)]
      (when client
        (reply! client (response (str "Ответ " message) lines)))
      (when changed?
        (let [frame (response "Событие Мир изменился." lines)]
          (doseq [other (vals sessions) :when (not= client other)]
            (reply! other frame)))))))

(defn- join! [{:keys [game world clients] :as server} client name]
  (let [joined? (dosync
                  (when-not (contains? @clients name)
                    (alter world (:join game) name)
                    (alter clients assoc name client)
                    true))]
    (if joined?
      (publish! server client (str "В игре, " name ".") true)
      (reply! client (response "Ответ Имя уже занято." [])))
    joined?))

(defn- leave! [{:keys [game world clients] :as server} name]
  (dosync
    (alter world (:leave game) name)
    (alter clients dissoc name))
  (publish! server nil nil true))

(defn- command! [{:keys [game world] :as server} client name command]
  (let [[message changed?]
        (dosync
          (let [before @world
                [after message] ((:command game) before name command)
                changed? (not= before after)]
            (when changed? (ref-set world after))
            [message changed?]))]
    (publish! server client message changed?)))

(defn- play! [server client name ^BufferedReader input]
  (try
    (loop []
      (when-let [line (.readLine input)]
        (let [command (str/trim line)]
          (when-not (= command "выхожу")
            (command! server client name command)
            (recur)))))
    (finally (leave! server name))))

(defn- read-client! [{:keys [game] :as server} client ^BufferedReader input]
  (reply! client (response (str (:title game) ". Напиши: меня зовут Имя") []))
  (loop []
    (when-let [line (.readLine input)]
      (let [command (str/trim line)]
        (when-not (= command "выхожу")
          (if-let [[_ name] (re-matches #"меня зовут (\p{L}[\p{L}\p{N}_-]{0,19})" command)]
            (if (join! server client name)
              (play! server client name input)
              (recur))
            (do
              (reply! client (response "Ответ Напиши: меня зовут Имя" []))
              (recur))))))))

(defn- write-client! [^Socket socket ^BufferedWriter output ^LinkedBlockingQueue outbox]
  (try
    (while true
      (.write output ^String (.take outbox))
      (.flush output))
    (catch InterruptedException _)
    (catch IOException _ (.close socket))))

(defn- handle-client! [server ^Socket socket]
  (try
    (with-open [connection socket]
      (.setTcpNoDelay connection true)
      (let [input (BufferedReader. (InputStreamReader. (.getInputStream connection) "UTF-8"))
            output (BufferedWriter. (OutputStreamWriter. (.getOutputStream connection) "UTF-8"))
            outbox (LinkedBlockingQueue. 32)
            client {:socket connection :outbox outbox}
            writer (future (write-client! connection output outbox))]
        (try
          (read-client! server client input)
          (finally (future-cancel writer)))))
    (catch IOException _)))

(defn serve! [game port]
  (let [server {:game game
                :world (ref (:initial-state game))
                :clients (ref {})
                :output-lock (Object.)}]
    (with-open [listener (ServerSocket. port)]
      (println (str (:title game) " · TCP " (.getLocalPort listener)))
      (while true
        (let [socket (.accept listener)]
          (future (handle-client! server socket)))))))
