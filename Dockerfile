FROM ghcr.io/navikt/baseimages/temurin:21
COPY /api/target/kafka-manager.jar app.jar
COPY /web-app/dist /app/public
