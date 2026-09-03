# --- Runtime Stage ---
FROM eclipse-temurin:26-jre
WORKDIR /app
COPY src/ ./src/
WORKDIR /app/src/main/java/io/github/placereporter99/utilitybot
EXPOSE 10000
# Runs the application using your specific Main class entry point
ENTRYPOINT ["java", "Main.java"]