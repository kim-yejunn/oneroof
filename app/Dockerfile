# Web stage: builds the console (Vite + React) into static files.
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Build stage: compiles the Spring Boot jar with the console bundled as static resources.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN ./gradlew --no-daemon bootJar -x test

# Runtime stage: JRE only.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
