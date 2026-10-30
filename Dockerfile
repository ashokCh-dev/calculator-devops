# ---------- Stage 1: build the jar with Maven + JDK ----------
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

# copy only the pom first so dependencies are cached in their own layer
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---------- Stage 2: slim JRE runtime ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# run as a non-root user
RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build --chown=spring:spring /workspace/target/calculator.jar app.jar

USER spring
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
