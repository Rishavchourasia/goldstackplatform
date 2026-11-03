# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY common/pom.xml common/
COPY common-web/pom.xml common-web/
COPY api-gateway/pom.xml api-gateway/
COPY user-service/pom.xml user-service/
COPY wallet-service/pom.xml wallet-service/
COPY price-service/pom.xml price-service/
COPY order-service/pom.xml order-service/
COPY sip-service/pom.xml sip-service/
COPY portfolio-service/pom.xml portfolio-service/
COPY notification-service/pom.xml notification-service/
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline -DexcludeGroupIds=com.goldstack

COPY . .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B -DskipTests package

RUN for s in api-gateway user-service wallet-service price-service order-service sip-service \
             portfolio-service notification-service; do \
      java -Djarmode=tools -jar $s/target/app.jar extract --layers --launcher --destination /extracted/$s; \
    done

FROM eclipse-temurin:21-jre-alpine AS runtime
ARG SERVICE
WORKDIR /app
RUN addgroup -S goldstack && adduser -S goldstack -G goldstack
USER goldstack

COPY --from=build /extracted/${SERVICE}/dependencies/ ./
COPY --from=build /extracted/${SERVICE}/spring-boot-loader/ ./
COPY --from=build /extracted/${SERVICE}/snapshot-dependencies/ ./
COPY --from=build /extracted/${SERVICE}/application/ ./

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=65 -XX:+ExitOnOutOfMemoryError -XX:TieredStopAtLevel=1 -Xss512k"
ENV PORT=8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=6 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
