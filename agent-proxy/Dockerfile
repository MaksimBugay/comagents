# syntax=docker/dockerfile:1

# ---- build: GraalVM native image ----
# pinned: the floating ":25" tag is stuck on 25.0.2 (Jan 2026); 25i5 tracks current JDK 25 CPU updates
FROM ghcr.io/graalvm/native-image-community:25i5-25.0.5.0.3-ol9 AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml aot-*.properties ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
# mostly-static: link libz & co. into the binary, only glibc stays dynamic (provided by distroless/base)
ENV NATIVE_IMAGE_OPTIONS="--static-nolibc"
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -Dpackaging=native-image -DskipTests

# ---- runtime: distroless (glibc only, no shell), non-root ----
FROM gcr.io/distroless/base-debian13:nonroot
COPY --from=build /workspace/target/application /app/application
EXPOSE 8080
USER 65532:65532
ENTRYPOINT ["/app/application"]
