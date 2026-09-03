# --- STAGE 1: Build and Compile ---
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

# 1. Copy ONLY the configuration files to fetch dependencies first
COPY pom.xml .

# 2. Download all dependencies into the image layer cache
RUN mvn dependency:go-offline -B

# 3. Copy your actual source code folder
COPY src ./src

# 4. Compile and package everything into a single, executable .jar file
RUN mvn clean package -DskipTests
# --- Runtime Stage ---
FROM eclipse-temurin:26-jdk-alpine
WORKDIR /app/src/main/java/io/github/placereporter99/utilitybot
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 10000
# Runs the application using your specific Main class entry point
ENTRYPOINT ["java", "-jar", "app.jar"]