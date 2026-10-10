# Build image for the box-breathing app: Android SDK 35 + JDK 17 (cirruslabs) plus Gradle.
FROM ghcr.io/cirruslabs/android-sdk:35
ARG GRADLE=8.10.2
RUN curl -fsSL https://services.gradle.org/distributions/gradle-${GRADLE}-bin.zip -o /tmp/g.zip \
 && unzip -q /tmp/g.zip -d /opt && rm /tmp/g.zip && ln -s /opt/gradle-${GRADLE}/bin/gradle /usr/local/bin/gradle
WORKDIR /src
