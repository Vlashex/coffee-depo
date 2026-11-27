:- encoding(utf8).
:- use_module('../prolog/net').
:- use_module(library(readutil)).
:- initialization(main, main).

main(Args) :-
    endpoint(Args, Host, Port),
    setup_call_cleanup(connect(Host, Port, Stream),
                       chat(Stream),
                       close(Stream)).

endpoint([], localhost, 4040).
endpoint([Host, PortArg], Host, Port) :-
    atom_number(PortArg, Port).

chat(Stream) :-
    stream_pair(Stream, In, Out),
    thread_create(copy_commands(Out), _, [detached(true)]),
    copy_replies(In).

copy_commands(Out) :-
    read_line_to_string(user_input, Line),
    (   Line == end_of_file
    ->  send_line(Out, "выхожу")
    ;   send_line(Out, Line),
        (Line == "выхожу" -> true ; copy_commands(Out))
    ).

copy_replies(In) :-
    read_line_to_string(In, Line),
    (   Line == end_of_file
    ->  true
    ;   (Line == "Конец" -> nl ; writeln(Line)),
        flush_output(user_output),
        copy_replies(In)
    ).
