FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app
ARG JAR_FILE=target/vyaparsathi-0.1.0.jar

COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]