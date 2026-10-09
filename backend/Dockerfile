FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew --no-daemon clean bootJar


FROM eclipse-temurin:21-jre

# Runtime selects the tested small or 2 GiB cgroup profile.
# Explicit Render JAVA_TOOL_OPTIONS always takes precedence.

WORKDIR /app

COPY --from=build /app/build/libs/restaurant-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 10000

COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh
ENTRYPOINT ["/app/docker-entrypoint.sh"]
