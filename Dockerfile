# Stage 1: build the assembly JAR using scala-cli
FROM virtuslab/scala-cli:latest AS builder
WORKDIR /app
COPY . .
RUN scala-cli package --power --assembly . -o /app/ranking-table.jar

# Stage 2: export the JAR as a clean artifact for CI extraction.
# Uses scratch (empty base) so BuildKit's local exporter sees only the JAR —
# the builder stage leaves a bloop Unix socket on disk that the exporter
# cannot handle, so we isolate the JAR here before extraction.
FROM scratch AS jar-export
COPY --from=builder /app/ranking-table.jar /ranking-table.jar

# Stage 3: minimal runtime image (default target for docker build / docker run)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/ranking-table.jar .
ENTRYPOINT ["java", "-jar", "ranking-table.jar"]
