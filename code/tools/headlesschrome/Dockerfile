FROM amazoncorretto:26.0.0 AS jre-base

FROM debian:bookworm-slim

# Copy JRE from Amazon Corretto
COPY --from=jre-base /usr/lib/jvm/java-26-amazon-corretto /opt/java

RUN apt-get update
RUN apt-get install -y --no-install-recommends chromium chromium-driver
RUN apt-get install -y --no-install-recommends curl

HEALTHCHECK --start-period=10s --interval=30s --timeout=5s --retries=3 CMD curl -f http://localhost:8080/health || exit 1

# Copy the Chrome extension for DOM annotation
COPY extension/ /dom-export

# Copy the shadow JAR
COPY build/libs/headlesschrome-*.jar /app/headlesschrome.jar

ENV JAVA_HOME=/opt/java
ENV PATH="${JAVA_HOME}/bin:${PATH}"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/headlesschrome.jar"]
