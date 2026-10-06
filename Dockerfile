FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 naboo && mkdir -p /app/uploads && chown naboo:naboo /app/uploads
COPY --from=build /app/target/naboo-api-1.0.0.jar app.jar
USER naboo
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
