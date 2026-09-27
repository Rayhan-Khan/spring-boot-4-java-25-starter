# First stage: Build the application
FROM eclipse-temurin:25-jdk AS build

# Set the working directory in the container
WORKDIR /app

# Copy Gradle Wrapper and configuration files
COPY gradlew /app/
COPY gradle /app/gradle/
COPY build.gradle settings.gradle /app/

# Ensure Gradle Wrapper is executable
RUN chmod +x /app/gradlew

# Download dependencies separately to leverage Docker caching
RUN ./gradlew dependencies --no-daemon

# Copy the application source code
COPY src /app/src

# Build the application
RUN ./gradlew bootJar --no-daemon

# Second stage: Prepare the final runtime image
FROM eclipse-temurin:25-jre-alpine

# Run as an unprivileged user
RUN addgroup -S app && adduser -S app -G app

# Set the working directory in the container
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

USER app

# Expose the default Spring Boot port
EXPOSE 8000

# Report container health from the actuator endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8000/actuator/health > /dev/null || exit 1

# Run the application; heap size follows the container memory limit
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
