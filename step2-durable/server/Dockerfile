FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src

COPY gradlew ./
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
COPY step1-simple/build.gradle.kts step1-simple/build.gradle.kts
COPY step1-simple/shared/build.gradle.kts step1-simple/shared/build.gradle.kts
COPY step1-simple/server/build.gradle.kts step1-simple/server/build.gradle.kts
COPY step1-simple/client/build.gradle.kts step1-simple/client/build.gradle.kts
COPY step2-durable/build.gradle.kts step2-durable/build.gradle.kts
COPY step2-durable/shared/build.gradle.kts step2-durable/shared/build.gradle.kts
COPY step2-durable/server/build.gradle.kts step2-durable/server/build.gradle.kts
COPY step2-durable/client/build.gradle.kts step2-durable/client/build.gradle.kts
RUN chmod +x gradlew && ./gradlew --no-daemon :step2-durable:server:dependencies > /dev/null

COPY step2-durable/shared step2-durable/shared
COPY step2-durable/server step2-durable/server
RUN ./gradlew --no-daemon :step2-durable:server:shadowJar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S chatting && adduser -S -G chatting chatting
COPY --from=build /src/step2-durable/server/build/libs/server.jar /app/server.jar
USER chatting
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
