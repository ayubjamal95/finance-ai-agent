# --------------------------------------------------------------------------------------------------
# Stage 1: Build Stage - Builds the JAR using Maven
# --------------------------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-17 AS build

# Set working directory
WORKDIR /app

# Copy Maven descriptor first for dependency caching
COPY pom.xml .

# Pre-download dependencies (optional but speeds up builds)
RUN mvn dependency:go-offline -B

# Copy source code
COPY src src

# Package the application (skip tests for faster builds)
RUN mvn clean package -DskipTests

# List target directory to verify jar name (for debugging, safe to keep)
RUN ls -lah target

# --------------------------------------------------------------------------------------------------
# Stage 2: Runtime Stage - Lightweight JRE image for running the app
# --------------------------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine

# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Set working directory
WORKDIR /app

# Copy the built JAR from the build stage
# (copies whatever .jar is created, regardless of its exact name)
COPY --from=build /app/target/*.jar app.jar

# Change ownership for non-root user
RUN chown spring:spring /app/app.jar

# Switch to non-root user
USER spring:spring

# Expose Spring Boot default port
EXPOSE 8080

# Health check for container orchestration
HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Command to run the Spring Boot app
ENTRYPOINT ["java", "-jar", "app.jar"]
