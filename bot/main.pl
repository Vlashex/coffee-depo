:- encoding(utf8).
:- use_module('../prolog/net').
:- use_module(library(readutil)).
:- use_module(depot).
:- initialization(main, main).

main(Args) :-
    endpoint(Args, Host, Port, Name),
    setup_call_cleanup(connect(Host, Port, Stream),
                       play(Stream, Name),
                       close(Stream)).

endpoint([], localhost, 4040, "Дед-Фильтр").
endpoint([Host, PortArg, NameArg], Host, Port, Name) :-
    atom_number(PortArg, Port),
    atom_string(NameArg, Name).

play(Stream, Name) :-
    stream_pair(Stream, In, Out),
    format(string(Login), 'меня зовут ~s', [Name]),
    send_line(Out, Login),
    loop(In, Out, Name, waiting).

loop(In, Out, Name, Pending) :-
    read_line_to_string(In, Header),
    (   Header == end_of_file
    ->  true
    ;   read_snapshot(In, Lines),
        writeln(Header),
        (   Lines == [], sub_string(Header, 0, 6, _, "Ответ ")
        ->  halt(1)
        ;   play_frame(In, Out, Name, Header, Lines, Pending)
        )
    ).

read_snapshot(In, Lines) :-
    read_line_to_string(In, Line),
    (   Line == "Конец"
    ->  Lines = []
    ;   Line \== end_of_file,
        Lines = [Line|Rest],
        read_snapshot(In, Rest)
    ).

play_frame(In, Out, Name, Header, Lines, Pending) :-
    remember(Lines),
    (   finished
    ->  forall(member(Line, Lines), writeln(Line))
    ;   (sub_string(Header, 0, 6, _, "Ответ ") -> Ready = ready ; Ready = Pending),
        (   Ready == ready, choose(Name, Command)
        ->  sleep(0.35),
            format('> ~s~n', [Command]),
            send_line(Out, Command),
            loop(In, Out, Name, waiting)
        ;   loop(In, Out, Name, Ready)
        )
    ).
