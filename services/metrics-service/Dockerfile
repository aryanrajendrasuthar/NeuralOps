# Build context: ./services (set in docker-compose)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY metrics-service/pom.xml metrics-service/pom.xml
RUN mvn dependency:go-offline -pl metrics-service --also-make -q 2>/dev/null || true
COPY metrics-service/src metrics-service/src
RUN mvn package -pl metrics-service --also-make -DskipTests -q

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends dumb-init \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd -r neuralops && useradd -r -g neuralops neuralops
COPY --from=build /workspace/metrics-service/target/*.jar app.jar
USER neuralops
EXPOSE 8082
ENTRYPOINT ["dumb-init", "--"]
CMD ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
