# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build application
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Add curl for healthcheck
RUN apk add --no-cache curl

# Copy JAR
COPY --from=build /app/target/*.jar app.jar

# Create non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Storage local: diretório com dono appuser. Quando um volume nomeado é
# montado vazio aqui, o Docker herda esta permissão (evita root-only no volume).
RUN mkdir -p /tmp/jetski-photos && chown -R appuser:appgroup /tmp/jetski-photos

USER appuser

EXPOSE 8090

# `sh -c` para que JAVA_OPTS (definido no compose) chegue à JVM — a forma exec
# pura não expande variáveis. O `exec` mantém a JVM como PID 1, preservando o
# encaminhamento de SIGTERM (parada limpa no `docker compose down`/deploy).
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
