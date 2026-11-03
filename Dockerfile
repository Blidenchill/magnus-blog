# Build the executable JAR in a Maven/JDK image, run on a slim JRE.
# Build context must not contain secrets (see .dockerignore).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
RUN groupadd --gid 10001 magnus && useradd --uid 10001 --gid 10001 --no-create-home magnus
WORKDIR /app
COPY --from=build /build/target/magnus-blog-*.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
