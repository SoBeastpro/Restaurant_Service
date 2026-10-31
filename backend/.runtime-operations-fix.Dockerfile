FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -ntp -pl services/operations-service -am package -DskipTests
FROM build AS verify
RUN --mount=type=cache,target=/root/.m2,sharing=locked cp -a /root/.m2 /maven-cache
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/services/operations-service/target/*.jar /app/application.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/application.jar"]
