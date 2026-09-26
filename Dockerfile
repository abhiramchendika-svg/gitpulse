# GitPulse: one image serving both the API and the built React frontend.
#
#   docker build -t gitpulse .
#   docker run --rm -p 8080:8080 -e GITHUB_TOKEN=... gitpulse
#
# Three stages keep the final image small: only a JRE and one jar ship; Node, Maven and the
# sources stay in the build stages. Tests are not run here (CI runs them on every push).

# ---- 1. Frontend: static files in /app/frontend/dist --------------------------------------
FROM node:24-alpine AS frontend
WORKDIR /app/frontend
# Dependencies first, so this layer is cached until package-lock.json changes.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- 2. Backend: executable jar with the frontend inside -----------------------------------
FROM eclipse-temurin:21-jdk AS backend
WORKDIR /app/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
# No separate "dependency:go-offline" step: it downloads every plugin Maven might ever use
# (site and report tooling included), many times more than a package build needs.
COPY backend/src src
# Spring Boot serves anything under classpath:/static, so the frontend ships inside the jar.
COPY --from=frontend /app/frontend/dist src/main/resources/static
RUN ./mvnw -B -q -DskipTests package && cp target/gitpulse-backend-*.jar /app/gitpulse.jar

# ---- 3. Runtime ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
# Never run as root.
RUN useradd --system --uid 10001 --no-create-home gitpulse
WORKDIR /app
COPY --from=backend /app/gitpulse.jar gitpulse.jar
USER gitpulse

# Sized for small instances (free tiers often have 512 MB): the heap may use 75% of the
# container's memory, the serial collector has the least overhead on one CPU, and running out
# of memory restarts the container instead of leaving it half-working.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

# The app listens on $PORT (default 8080); hosts such as Render set PORT themselves.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/gitpulse.jar"]
