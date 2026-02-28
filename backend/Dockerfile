FROM maven:3.9.9-eclipse-temurin-17 AS build

ARG SERVICE_NAME
WORKDIR /workspace

COPY pom.xml pom.xml
COPY libs libs
COPY services services

RUN mvn -q -DskipTests package -pl services/${SERVICE_NAME} -am

FROM eclipse-temurin:17-jre

ARG SERVICE_NAME
WORKDIR /app
COPY --from=build /workspace/services/${SERVICE_NAME}/target/${SERVICE_NAME}-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
