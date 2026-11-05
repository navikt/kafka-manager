FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25
WORKDIR /app

ARG JAR_FILE=api/build/libs/kafka-manager.jar
COPY ${JAR_FILE} app.jar

ENV TZ="Europe/Oslo"
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
CMD ["-jar", "app.jar"]