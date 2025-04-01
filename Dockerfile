# Use an official OpenJDK runtime as a parent image
FROM openjdk:17-jdk-slim

# Set the working directory in the container
WORKDIR /app

# Copy the JAR file into the container
COPY target/*.jar app.jar

# Expose the port that the application runs on
EXPOSE 8091

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]