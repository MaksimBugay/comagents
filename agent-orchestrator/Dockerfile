# syntax=docker/dockerfile:1

# ---- build: GraalVM native image ----
# pinned: the floating ":25" tag is stuck on 25.0.2 (Jan 2026); 25i5 tracks current JDK 25 CPU updates
# the build context is the comagents root (see build-image.sh): agent-commons is not published, so it is installed
# from its sources next to agent-orchestrator before agent-orchestrator resolves its dependencies
FROM ghcr.io/graalvm/native-image-community:25i5-25.0.5.0.3-ol9 AS build
WORKDIR /workspace/agent-orchestrator
COPY agent-orchestrator/.mvn .mvn
COPY agent-orchestrator/mvnw agent-orchestrator/pom.xml agent-orchestrator/aot-*.properties ./
COPY agent-commons /workspace/agent-commons
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -f ../agent-commons/pom.xml install -DskipTests
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY agent-orchestrator/src src
# mostly-static: link libz & co. into the binary, only glibc stays dynamic (provided by the runtime image)
ENV NATIVE_IMAGE_OPTIONS="--static-nolibc"
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -Dpackaging=native-image -DskipTests

# ---- runtime: distroless glibc, non-root ----
# no shell or harness needed: the orchestrator runs no agents itself
FROM gcr.io/distroless/base-debian13:nonroot
# marks every agent-orchestrator build, tagged or not, so build-image.sh can remove the old ones
LABEL com.comagents.app=agent-orchestrator
COPY --from=build /workspace/agent-orchestrator/target/application /app/application
EXPOSE 8080
ENTRYPOINT ["/app/application"]
