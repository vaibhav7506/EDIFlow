FROM gradle:9.5.1-jdk21 AS builder
WORKDIR /workspace

COPY settings.gradle build.gradle gradlew gradlew.bat ./
COPY gradle gradle
COPY packages packages
COPY authorization-server authorization-server
COPY gateway gateway
COPY ingestion-service ingestion-service
COPY order-service order-service
COPY integration-tests integration-tests
COPY gateway-integration-tests gateway-integration-tests

RUN ./gradlew --no-daemon \
    :authorization-server:bootJar \
    :gateway:bootJar \
    :ingestion-service:bootJar \
    :order-service:bootJar

FROM eclipse-temurin:21-jre-jammy AS runtime-base
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home ediflow
USER ediflow

FROM runtime-base AS authorization-server
COPY --from=builder /workspace/authorization-server/build/libs/*.jar /app/app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS gateway
COPY --from=builder /workspace/gateway/build/libs/*.jar /app/app.jar
EXPOSE 8443
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS ingestion-service
COPY --from=builder /workspace/ingestion-service/build/libs/*.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS order-service
COPY --from=builder /workspace/order-service/build/libs/*.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
