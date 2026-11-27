# Multivo server — Docker build for Render.
#
# Render's native build environment has no Java at all (it defaults to
# Node.js, and JAVA_VERSION does not install a JDK), so the server has to
# bring its own Java. Multi-stage build: compile the Ktor server with a
# JDK, then run it on a slim JRE image. No code changes needed.
#
# Render settings for the web service:
#   Environment: Docker, Dockerfile path: ./Dockerfile
#   Environment variables: DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET
#   (PORT is provided by Render; the server reads it already.)

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradle gradle
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY server server
RUN sh gradlew :server:installDist --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/server/build/install/server ./
EXPOSE 8080
CMD ["bin/server"]
