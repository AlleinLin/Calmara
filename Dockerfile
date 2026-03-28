# 多阶段构建 - Calmara主应用

# 构建阶段
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

COPY pom.xml .
COPY Calmara-api/pom.xml Calmara-api/
COPY Calmara-agent-core/pom.xml Calmara-agent-core/
COPY Calmara-multimodal/pom.xml Calmara-multimodal/
COPY Calmara-mcp/pom.xml Calmara-mcp/
COPY Calmara-security/pom.xml Calmara-security/
COPY Calmara-admin/pom.xml Calmara-admin/
COPY Calmara-model/pom.xml Calmara-model/
COPY Calmara-common/pom.xml Calmara-common/

RUN mvn dependency:go-offline -B

COPY . .

RUN mvn clean package -DskipTests -B

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S calmara && adduser -S calmara -G calmara

RUN mkdir -p /app/logs && chown -R calmara:calmara /app

COPY --from=builder /build/Calmara-api/target/*.jar app.jar

USER calmara

ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
ENV SPRING_PROFILES_ACTIVE=docker

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
