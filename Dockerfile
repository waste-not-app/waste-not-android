FROM eclipse-temurin:25-jdk-jammy AS sdk
ARG CMDLINE_TOOLS_VERSION=11076708
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/gradle
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip python3 \
 && rm -rf /var/lib/apt/lists/*
RUN curl -fsSLo /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
 && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
 && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_HOME}/cmdline-tools" \
 && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
 && rm /tmp/cmdline-tools.zip
RUN yes | sdkmanager --licenses > /dev/null \
 && sdkmanager --install "platform-tools" "platforms;android-36" "build-tools;36.0.0" > /dev/null

FROM sdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/gradle ./gradlew --version --no-daemon
COPY app ./app
COPY scripts ./scripts
ARG GRADLE_TASKS="testDebugUnitTest assembleDebug"
ARG GRADLE_ARGS=""
RUN --mount=type=cache,target=/gradle \
    ./gradlew ${GRADLE_TASKS} ${GRADLE_ARGS} -Pwastenot.hotReload=false --no-daemon --max-workers=2 \
 && mkdir -p /out \
 && cp -r app/build/outputs/apk/. /out/

FROM scratch AS apk
COPY --from=build /out/ /
