# ---------- Stage 1: build the JAR ----------
# Full JDK image, only used for building. Nothing from here ends up in the final image except the JAR.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Copy only the Maven files first and download all libraries.
# Docker caches this step, so libraries are only downloaded again when pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -q

# Now copy the source code and build the JAR (tests are run separately with ./mvnw verify)
COPY src src
RUN ./mvnw package -DskipTests -q


# ---------- Stage 2: the image that actually runs ----------
# Small Java 21 runtime image (no compiler, no Maven, no source code)
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a normal user instead of root, so a security hole in the app can't take over the container
RUN useradd --system --create-home --uid 1001 shop \
    && mkdir -p /app/logs \
    && chown -R shop:shop /app
USER shop

COPY --from=build --chown=shop:shop /build/target/shop-api-*.jar app.jar

# Let Java use up to 75% of the container's memory limit
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# The app listens on 8080; log files go to /app/logs (mount a folder there to keep them)
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
