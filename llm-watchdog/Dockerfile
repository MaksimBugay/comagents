# syntax=docker/dockerfile:1

# ---- build: GraalVM native image ----
# pinned: the floating ":25" tag is stuck on 25.0.2 (Jan 2026); 25i5 tracks current JDK 25 CPU updates
# the build context is the comagents root (see build-image.sh): agent-commons and its parent comagents-parent, which
# is this service's parent too, are not published, so they are installed from their sources before the service
# resolves its dependencies
FROM ghcr.io/graalvm/native-image-community:25i5-25.0.5.0.3-ol9 AS build
WORKDIR /workspace/llm-watchdog
COPY llm-watchdog/.mvn .mvn
COPY llm-watchdog/mvnw llm-watchdog/pom.xml llm-watchdog/aot-*.properties ./
COPY agent-commons /workspace/agent-commons
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -f ../agent-commons/parent/pom.xml install -DskipTests
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY llm-watchdog/src src
# mostly-static: link libz & co. into the binary, only glibc stays dynamic (provided by the runtime image)
ENV NATIVE_IMAGE_OPTIONS="--static-nolibc"
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -Dpackaging=native-image -DskipTests

# ---- runtime: distroless glibc, non-root ----
# no shell or harness needed: the watchdog only forwards HTTP calls
FROM gcr.io/distroless/base-debian13:nonroot
# marks every llm-watchdog build, tagged or not, so build-image.sh can remove the old ones
LABEL com.comagents.app=llm-watchdog
COPY --from=build /workspace/llm-watchdog/target/application /app/application
EXPOSE 8080
ENTRYPOINT ["/app/application"]
