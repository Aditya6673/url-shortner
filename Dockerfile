FROM eclipse-temurin:21-jre

RUN useradd --system --create-home app
WORKDIR /app

COPY target/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
