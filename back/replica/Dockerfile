FROM maven:3.8.4-openjdk-17 AS build
WORKDIR /replica
COPY . .
RUN mvn clean package 

FROM openjdk:17-jdk-slim
WORKDIR /replica
COPY --from=build /replica/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]