FROM eclipse-temurin:17-jdk-focal AS builder
WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle .
COPY settings.gradle .

COPY server ./server
COPY gateway ./gateway
COPY fe ./fe
COPY deploy ./deploy
COPY user ./user

RUN chmod +x ./gradlew && ./gradlew :user:bootJar -x test

FROM eclipse-temurin:17-jre-focal
WORKDIR /app

COPY --from=builder /app/user/build/libs/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
