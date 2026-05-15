# Build context: ./services (set in docker-compose)
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml pom.xml
COPY user-service/pom.xml user-service/pom.xml
RUN mvn dependency:go-offline -pl user-service --also-make -q 2>/dev/null || true
COPY user-service/src user-service/src
RUN mvn package -pl user-service --also-make -DskipTests -q

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends dumb-init \
    && rm -rf /var/lib/apt/lists/*
RUN groupadd -r neuralops && useradd -r -g neuralops neuralops
COPY --from=build /workspace/user-service/target/*.jar app.jar
USER neuralops
EXPOSE 8085
ENTRYPOINT ["dumb-init", "--"]
CMD ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
