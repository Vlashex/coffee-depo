:- module(net, [connect/3, send_line/2]).

:- use_module(library(socket)).

connect(Host, Port, Stream) :-
    tcp_connect(Host:Port, Stream, []),
    stream_pair(Stream, In, Out),
    set_stream(In, encoding(utf8)),
    set_stream(In, newline(detect)),
    set_stream(Out, encoding(utf8)),
    set_stream(Out, newline(posix)).

send_line(Out, Line) :-
    format(Out, '~s\r\n', [Line]),
    flush_output(Out).
