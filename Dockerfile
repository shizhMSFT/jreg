FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="jreg" \
      org.opencontainers.image.description="OCI-compliant container registry server" \
      org.opencontainers.image.version="1.0.0"

WORKDIR /app

# Copy the application JAR
COPY target/jreg-*.jar /app/jreg.jar

# Expose the application port
EXPOSE 5000

# Run the application
ENTRYPOINT ["java", "-jar", "/app/jreg.jar"]
