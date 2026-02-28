FROM virtuslab/scala-cli:latest AS builder
WORKDIR /app
COPY . .
RUN scala-cli package --power --assembly . -o /app/ranking-table.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/ranking-table.jar .
ENTRYPOINT ["java", "-jar", "ranking-table.jar"]
