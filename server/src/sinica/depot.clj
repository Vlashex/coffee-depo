(ns sinica.depot
  (:require [clojure.string :as str]))

(def ^:private initial-state
  {:turn 0
   :freshness 3
   :routes (sorted-map "6" ["Депо" "Луч" "Рынок"]
                       "9" ["Рынок" "Башня" "Набережная"])
   :players (sorted-map)
   :orders (sorted-map "1" {:drink "эспрессо" :stop "Луч" :deadline 9}
                       "2" {:drink "какао" :stop "Депо" :deadline 13}
                       "3" {:drink "флэт-уайт" :stop "Башня" :deadline 20}
                       "4" {:drink "фильтр" :stop "Набережная" :deadline 25})})

(def ^:private help
  (str "Команды: что по заказам, покажи карту, беру заказ N, еду на N, "
       "варю НАПИТОК, отдаю заказ, жду, выхожу."))

(defn- join [state name]
  (update-in state [:players name]
             #(assoc (or % {:stop "Депо" :score 0}) :online true)))

(defn- leave [state name]
  (-> (reduce-kv (fn [world id order]
                   (if (= name (:holder order))
                     (update-in world [:orders id] dissoc :holder)
                     world))
                 state (:orders state))
      (update-in [:players name] #(dissoc % :online :fresh-until))))

(defn- active? [order turn]
  (and (not (:delivered order)) (<= turn (:deadline order))))

(defn- finished? [{:keys [orders turn]}]
  (not-any? #(active? % turn) (vals orders)))

(defn- advance [state message]
  (let [world (update state :turn inc)
        turn (:turn world)
        world (reduce-kv
               (fn [world id order]
                 (if (and (:holder order) (not (active? order turn)))
                   (-> world
                       (update-in [:orders id] dissoc :holder)
                       (update-in [:players (:holder order)] dissoc :fresh-until))
                   world))
               world (:orders world))]
    [world message]))

(defn- tram-ride [route turn]
  (let [n (count route)]
    [(nth route (mod turn n)) (nth route (mod (inc turn) n))]))

(defn- command [state name input]
  (let [input (str/lower-case input)
        [_ order-id] (re-matches #"беру заказ (\d+)" input)
        [_ tram] (re-matches #"еду на (\d+)" input)
        [_ drink] (re-matches #"варю (\S+)" input)
        {:keys [turn routes orders freshness]} state
        player (get-in state [:players name])
        [held-id held] (first (filter #(= name (:holder (val %))) orders))]
    (cond
      (#{"что по заказам" "покажи карту" "осмотрись"} input) [state "Вот что в городе."]
      (= input "помощь") [state help]
      (finished? state) [state "Смена уже закончилась."]
      order-id
      (let [order (get orders order-id)]
        (cond
          held [state "У тебя уже есть заказ."]
          (or (nil? order) (not (active? order (inc turn))) (:holder order))
          [state "Этот заказ уже недоступен."]
          :else (advance (assoc-in state [:orders order-id :holder] name)
                         (str "Заказ " order-id " твой."))))
      tram
      (if-let [route (get routes tram)]
        (let [[from to] (tram-ride route turn)]
          (if (= from (:stop player))
            (advance (assoc-in state [:players name :stop] to) (str "Приехали на " to "."))
            [state (str "Трамвай " tram " сейчас на " from ".")]))
        [state "Такого трамвая нет."])
      drink
      (if (= drink (:drink held))
        (advance (assoc-in state [:players name :fresh-until] (+ turn freshness))
                 (str drink " готов."))
        [state "Вари напиток из своего заказа."])
      (= input "отдаю заказ")
      (cond
        (nil? held) [state "Ты ещё не взял заказ."]
        (not= (:stop held) (:stop player)) [state (str "Клиент ждёт на " (:stop held) ".")]
        (< (get player :fresh-until -1) turn) [state "Клиент ждёт горячий напиток."]
        :else (advance (-> state
                           (update-in [:orders held-id] #(-> % (dissoc :holder) (assoc :delivered true)))
                           (update-in [:players name :score] inc)
                           (update-in [:players name] dissoc :fresh-until))
                       (str "Заказ " held-id " доставлен.")))
      (= input "жду") (advance state "Прошёл ход.")
      :else [state help])))

(defn- order-line [turn [id {:keys [drink stop deadline holder delivered] :as order}]]
  (let [status (cond delivered "доставлен"
                     (not (active? order turn)) "просрочен"
                     holder "занят"
                     :else "свободен")]
    (format "Заказ %s %s для %s до %d статус %s %s"
            id drink stop deadline status (or holder "-"))))

(defn- results [players]
  (let [best (reduce max 0 (map :score (vals players)))
        winners (for [[name player] players :when (= best (:score player))] name)]
    (cond
      (zero? best) "Никто не успел с доставкой."
      (= 1 (count winners)) (str "Победитель: " (first winners) ".")
      :else (str "Ничья: " (str/join ", " winners) "."))))

(defn- snapshot [{:keys [turn freshness routes players orders] :as state}]
  (concat [(str "Ход " turn)
           (if (finished? state) "Смена окончена" "Смена идёт")
           (str "Свежесть " freshness " хода")]
          (for [[id route] routes]
            (str "Маршрут " id " по кругу " (str/join " " route)))
          (for [[id route] routes :let [[from to] (tram-ride route turn)]]
            (str "Трамвай " id " сейчас на " from ", дальше " to "."))
          (for [[name {:keys [stop score online]}] players]
            (format "Игрок %s на %s доставок %d %s" name stop score
                    (if online "в-игре" "ушёл")))
          (for [[name {:keys [fresh-until]}] players :when (and fresh-until (>= fresh-until turn))]
            (format "Стакан %s свеж до %d" name fresh-until))
          (map #(order-line turn %) orders)
          (when (finished? state) [(results players)])))

(def game
  {:title "Депо без сна"
   :initial-state initial-state
   :join join
   :leave leave
   :command command
   :snapshot snapshot})
