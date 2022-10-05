FROM maven:3.8.6-openjdk-18-slim AS build
COPY java-agent     /app/java-agent
COPY obfuscator     /app/obfuscator
COPY obfuscator-api /app/obfuscator-api
COPY pom.xml        /app/pom.xml
RUN mvn -f /app/pom.xml clean package

FROM openjdk:18-jdk-slim
COPY --from=build /app/obfuscator/target/obfuscator-1.0-SNAPSHOT.jar /app/obfuscator.jar
COPY obfuscator/native/CMakeLists.txt /app/CMakeLists.txt
WORKDIR /app
RUN apt-get update && apt-get install --yes cmake
ENTRYPOINT bash
