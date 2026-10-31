FROM maven:3.9.16-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.m2,sharing=locked mvn -B -ntp -pl services/workforce-service -am -Dtest=MemberRepositoryPositionTests -Dsurefire.failIfNoSpecifiedTests=false test && mvn -B -ntp -pl services/workforce-service -am package -DskipTests
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/services/workforce-service/target/*.jar /app/application.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/application.jar"]
