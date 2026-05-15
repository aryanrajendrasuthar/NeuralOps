# Build context: ./services (set in docker-compose)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY cost-analytics-service/pom.xml cost-analytics-service/pom.xml
RUN mvn dependency:go-offline -pl cost-analytics-service --also-make -q 2>/dev/null || true
COPY cost-analytics-service/src cost-analytics-service/src
RUN mvn package -pl cost-analytics-service --also-make -DskipTests -q

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends dumb-init \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd -r neuralops && useradd -r -g neuralops neuralops
COPY --from=build /workspace/cost-analytics-service/target/*.jar app.jar
USER neuralops
EXPOSE 8084
ENTRYPOINT ["dumb-init", "--"]
CMD ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
