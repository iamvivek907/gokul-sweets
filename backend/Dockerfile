FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew --no-daemon clean bootJar


FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/build/libs/restaurant-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 10000

ENTRYPOINT ["java", "-jar", "app.jar"]
