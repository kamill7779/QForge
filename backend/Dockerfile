FROM maven:3.9.9-eclipse-temurin-17 AS build

ARG SERVICE_NAME
WORKDIR /workspace

COPY .mvn .mvn
COPY pom.xml pom.xml
COPY libs libs
COPY services services

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -s .mvn/settings-docker.xml -Dmaven.test.skip=true package -pl services/${SERVICE_NAME} -am

FROM maven:3.9.9-eclipse-temurin-17

ARG SERVICE_NAME
WORKDIR /app
COPY --from=build /workspace/services/${SERVICE_NAME}/target/${SERVICE_NAME}-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
