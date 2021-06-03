FROM ghcr.io/navikt/pus-nais-java-app/pus-nais-java-app:java11
COPY /api/target/kafka-manager.jar app.jar
COPY /web-app/build /app/public