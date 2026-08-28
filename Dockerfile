FROM gradle:9.5.1-jdk21 AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle build.gradle ./
COPY gradle gradle
RUN gradle --no-daemon dependencies
COPY src src
RUN gradle --no-daemon bootJar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && useradd --system --create-home ediflow
COPY --from=build /workspace/build/libs/*.jar app.jar
USER ediflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
