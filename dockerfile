# Use an official Java runtime as a base image
FROM openjdk:17-jdk-slim

# Set working directory inside the container
WORKDIR /app

# Copy the project JAR file (Maven build output)
COPY target/Blockchain-0.0.1-SNAPSHOT.jar app.jar

# Expose the app port (matches your Spring Boot server.port)
EXPOSE 8080

# Run the Spring Boot application
ENTRYPOINT ["java", "-jar", "app.jar"]
