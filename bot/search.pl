:- module(search, [astar/5]).

:- meta_predicate astar(+, 3, 1, 2, -).

% Step(From, Action, To): все действия стоят один ход.
% Estimate — согласованная нижняя оценка числа оставшихся ходов.
astar(Start, Step, Goal, Estimate, Plan) :-
    call(Estimate, Start, H),
    search([H-node(Start, 0, [])], [], Step, Goal, Estimate, Reversed),
    reverse(Reversed, Plan).

search([_-node(State, Cost, Path)|Open], Seen, Step, Goal, Estimate, Plan) :-
    (   memberchk(State, Seen)
    ->  search(Open, Seen, Step, Goal, Estimate, Plan)
    ;   call(Goal, State)
    ->  Plan = Path
    ;   NextCost is Cost + 1,
        findall(F-node(Next, NextCost, [Action|Path]),
                ( call(Step, State, Action, Next),
                  call(Estimate, Next, H),
                  F is NextCost + H
                ),
                Children),
        append(Open, Children, Pending),
        keysort(Pending, Sorted),
        search(Sorted, [State|Seen], Step, Goal, Estimate, Plan)
    ).
