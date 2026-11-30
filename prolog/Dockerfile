FROM swipl:10.0.2
WORKDIR /app
ENV LANG=C.UTF-8
COPY prolog/*.pl prolog/
COPY client/ client/
COPY bot/ bot/
ENTRYPOINT ["swipl", "-q", "-s"]
