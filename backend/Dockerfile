FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew --no-daemon clean bootJar


FROM eclipse-temurin:21-jre

# Tested with a 512 MB container; deployments may override this environment value.
ENV JAVA_TOOL_OPTIONS="-Xms64m -Xmx192m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=32m -XX:+ExitOnOutOfMemoryError"

WORKDIR /app

COPY --from=build /app/build/libs/restaurant-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "app.jar"]
