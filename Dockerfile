FROM gcr.io/distroless/java21
ENV TZ="Europe/Oslo"
WORKDIR /app
COPY /api/target/kafka-manager.jar app.jar
EXPOSE 8080
USER nonroot
CMD ["app.jar"]