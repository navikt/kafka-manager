FROM ghcr.io/navikt/poao-baseimages/java:11
COPY /api/target/kafka-manager.jar app.jar
COPY /web-app/build /app/public