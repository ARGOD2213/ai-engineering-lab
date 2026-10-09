# syntax=docker/dockerfile:1
# Dockerfile - packages the backend as an OCI image for ECS Fargate.
# Build context: services/ai-engineering-api   Output: <ecr-url>/ai-lab/api:<git-sha>
# Production tip: pin both base images by digest (image@sha256:...) for reproducible builds.

# ---- Stage 1: build the jar (tests already ran in CI before this step) -------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests && cp target/*.jar /workspace/app.jar

# ---- Stage 2: minimal runtime ------------------------------------------------------------
FROM eclipse-temurin:21-jre
# curl is used only by the ECS container health check.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system app \
 && useradd --system --gid app --home-dir /app --create-home app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar
USER app
EXPOSE 8080
# Size the heap from the TASK memory limit, not the host; exit (and get replaced) on OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
