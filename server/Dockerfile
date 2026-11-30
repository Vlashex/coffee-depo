FROM clojure:temurin-21-lein-trixie AS build
WORKDIR /build
COPY server/project.clj .
RUN lein deps
COPY server/src src
RUN lein uberjar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/sinica.jar .
EXPOSE 4040
ENTRYPOINT ["java", "-jar", "sinica.jar"]
