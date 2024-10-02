
# Use an official OpenJDK image as the base image
FROM openjdk:21-slim

# Install Gradle
RUN apt-get update && apt-get install -y curl unzip zip

# Set the working directory
WORKDIR /app

# Copy project files to the container
COPY . .


# Use ENTRYPOINT to source SDKMAN and then run the passed command (Gradle build, etc.)
ENTRYPOINT ["/app/gradlew"]

# Default CMD if no command is passed (this is useful for specifying default behavior)
CMD ["build"]