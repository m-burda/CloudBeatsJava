FROM maven:3.9.4-eclipse-temurin-21 as builder

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /app/target/CloudBeats-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

#HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
#    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=docker"]

