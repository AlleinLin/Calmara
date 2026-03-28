FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

COPY pom.xml .
COPY Calmara-common/pom.xml Calmara-common/
COPY Calmara-model/pom.xml Calmara-model/
COPY Calmara-multimodal/pom.xml Calmara-multimodal/
COPY Calmara-agent-core/pom.xml Calmara-agent-core/
COPY Calmara-mcp/pom.xml Calmara-mcp/
COPY Calmara-security/pom.xml Calmara-security/
COPY Calmara-api/pom.xml Calmara-api/
COPY Calmara-admin/pom.xml Calmara-admin/
COPY Calmara-web/pom.xml Calmara-web/

RUN apk add --no-cache maven

COPY . .

RUN mvn dependency:go-offline -B

RUN mvn package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

RUN addgroup -S calmara && adduser -S calmara -G calmara

COPY --from=builder /app/Calmara-web/target/*.jar app.jar

RUN mkdir -p /tmp/calmara/reports && chown -R calmara:calmara /tmp/calmara

USER calmara

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
