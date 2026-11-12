# syntax=docker/dockerfile:1

# ---- build: GraalVM native image ----
# pinned: the floating ":25" tag is stuck on 25.0.2 (Jan 2026); 25i5 tracks current JDK 25 CPU updates
FROM ghcr.io/graalvm/native-image-community:25i5-25.0.5.0.3-ol9 AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml aot-*.properties ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
# mostly-static: link libz & co. into the binary, only glibc stays dynamic (provided by the runtime image)
ENV NATIVE_IMAGE_OPTIONS="--static-nolibc"
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -Dpackaging=native-image -DskipTests

# ---- runtime: Debian 13 slim + Claude Code CLI (headless `claude -p`), non-root ----
# not distroless: Claude Code needs bash (https://code.claude.com/docs/en/setup)
FROM debian:trixie-slim
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
RUN apt-get update \
 && apt-get install -y --no-install-recommends bash ca-certificates curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --uid 65532 --user-group --create-home --shell /bin/bash nonroot
USER 65532:65532
# the native installer is per-user (~/.local/bin); the image is immutable, so no self-updates
ENV PATH="/home/nonroot/.local/bin:${PATH}" \
    DISABLE_AUTOUPDATER=1
# build-image.sh passes a new value on every build, so this layer is never reused from cache
# and always installs the latest Claude Code release
ARG CLAUDE_CODE_BUILD_ID=unset
RUN echo "Claude Code install, build ${CLAUDE_CODE_BUILD_ID}" \
 && curl -fsSL https://claude.ai/install.sh | bash -s latest \
 && claude --version
COPY --from=build /workspace/target/application /app/application
EXPOSE 8080
ENTRYPOINT ["/app/application"]
