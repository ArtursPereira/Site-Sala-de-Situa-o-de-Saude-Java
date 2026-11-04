# Build stage: test-scoped dependencies exist only here and are not copied to
# the runtime image. The dev Maven profile is intentionally not activated.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline
COPY src ./src
RUN ./mvnw -B package

# Runtime stage: only the repackaged application and the JRE are included.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S nss && adduser -S nss -G nss
COPY --from=build /app/target/*.jar app.jar
RUN chown nss:nss app.jar
USER nss
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
