FROM maven:3.9.16-eclipse-temurin-25 AS build

ARG SERVICE
WORKDIR /workspace
COPY . .
RUN mvn -B -ntp -pl "services/${SERVICE}" -am package -DskipTests \
    && cp "services/${SERVICE}"/target/*.jar /workspace/application.jar

FROM eclipse-temurin:25-jre

WORKDIR /app
COPY --from=build /workspace/application.jar /app/application.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/application.jar"]
