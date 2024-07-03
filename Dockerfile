FROM gcr.io/distroless/java21
ENV TZ="Europe/Oslo"
WORKDIR /app
COPY /api/target/kafka-manager.jar app.jar
COPY /web-app/dist /app/public
EXPOSE 8080
USER nonroot
CMD ["app.jar"]