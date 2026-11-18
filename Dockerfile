# syntax=docker/dockerfile:1

# --- Etapa de construcción ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 1) contracts es un módulo independiente (sin parent): se instala primero en el repo local
COPY contracts/ ./contracts/
RUN mvn -B -q -f contracts/pom.xml install -DskipTests

# 2) la aplicación consume contracts desde el repositorio local de Maven
COPY pom.xml ./
COPY src/ ./src/
RUN mvn -B -DskipTests package

# --- Etapa de ejecución ---
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app \
    && useradd --system --gid app --home-dir /app --create-home app

WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/*.jar /app/app.jar

USER app
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
