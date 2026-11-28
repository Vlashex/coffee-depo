:- encoding(utf8).
:- module(depot, [remember/1, choose/2, finished/0]).

:- use_module(search).
:- dynamic known/1.

% Снимок приходит целиком. Заменяем факты, чтобы не хранить старые заказы и стаканы.
remember(Lines) :-
    retractall(known(_)),
    forall((member(Line, Lines),
            split_string(Line, " ", " ", Words),
            phrase(fact(Fact), Words)),
           assertz(known(Fact))).

fact(turn(Turn)) --> ["Ход"], number(Turn).
fact(over) --> ["Смена", "окончена"].
fact(freshness(Ticks)) --> ["Свежесть"], number(Ticks), ["хода"].
fact(route(Id, Stops)) --> ["Маршрут", Id, "по", "кругу"], stops(Stops).
fact(player(Name, Stop)) --> ["Игрок", Name, "на", Stop, "доставок"], number(_), ["в-игре"].
fact(cup(Name, Fresh)) --> ["Стакан", Name, "свеж", "до"], number(Fresh).
fact(order(Id, Drink, Stop, Deadline, Status, Holder)) -->
    ["Заказ", Id, Drink, "для", Stop, "до"], number(Deadline), ["статус", Status, Holder].

number(N) --> [Word], {number_string(N, Word)}.
stops([]) --> [].
stops([Stop|Rest]) --> [Stop], stops(Rest).

finished :- known(over).

choose(Name, Command) :-
    known(turn(Turn)),
    known(player(Name, Stop)),
    findall(Length-Plan,
            ( delivery(Name, Stop, Turn, Plan),
              length(Plan, Length)
            ),
            Plans),
    (   keysort(Plans, [_-[Command|_]|_])
    ->  true
    ;   \+ (known(player(Other, _)), Other \== Name),
        Command = "жду"
    ).

delivery(Name, Stop, Turn, Plan) :-
    known(order(Id, Drink, Target, Deadline, Status, Holder)),
    (   Status == "занят", Holder == Name
    ->  StartTurn = Turn,
        (known(cup(Name, Fresh)) -> true ; Fresh = -1),
        Plan = Actions
    ;   Status == "свободен",
        \+ known(order(_, _, _, _, "занят", Name)),
        StartTurn is Turn + 1,
        Fresh = -1,
        format(string(Claim), 'беру заказ ~s', [Id]),
        Plan = [Claim|Actions]
    ),
    StartTurn =< Deadline,
    known(freshness(Ticks)),
    astar(state(Stop, StartTurn, Fresh),
          step(Deadline, Drink, Ticks), ready(Target), estimate(Target), Ride),
    append(Ride, ["отдаю заказ"], Actions).

step(Deadline, Drink, Ticks, state(Stop, Turn, Fresh), Command, Next) :-
    Turn < Deadline,
    NextTurn is Turn + 1,
    (   Command = "жду",
        Next = state(Stop, NextTurn, Fresh)
    ;   known(route(Id, Stops)),
        length(Stops, Count),
        From is Turn mod Count,
        nth0(From, Stops, Stop),
        To is NextTurn mod Count,
        nth0(To, Stops, NextStop),
        format(string(Command), 'еду на ~s', [Id]),
        Next = state(NextStop, NextTurn, Fresh)
    ;   format(string(Command), 'варю ~s', [Drink]),
        FreshUntil is Turn + Ticks,
        Next = state(Stop, NextTurn, FreshUntil)
    ).

ready(Target, state(Target, Turn, Fresh)) :-
    Fresh >= Turn.

% Нижняя оценка: хотя бы одна поездка до другой остановки
% и хотя бы одна варка, если напиток уже остыл.
estimate(Target, state(Stop, Turn, Fresh), H) :-
    (Stop == Target -> Travel = 0 ; Travel = 1),
    (Fresh >= Turn -> Brew = 0 ; Brew = 1),
    H is Travel + Brew.
