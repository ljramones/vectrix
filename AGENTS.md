# Repository Guidelines

## Project Context
This repository is a maintained fork of JOML, used to add engine-specific capabilities while preserving JOML’s allocation-free API design for rendering workloads across graphics APIs (OpenGL, Vulkan, and others). Use upstream JOML if it covers your needs; use this fork when you need ownership of rendering-critical capabilities such as long-typed vectors, custom SIMD-style batched operations, SOA layouts for GPU upload, and dual-quaternion-oriented workflows. Current fork-specific scope includes long-typed vectors (`Vector2L`, `Vector3L`, `Vector4L` and `*Lc` interfaces) plus experimental SIMD/JVMCI/FFM paths behind Maven profiles and preprocessor guards.

## Project Structure & Module Organization
- `src/main/java/org/vectrix/`: core math library (vectors, matrices, quaternions, intersections, sampling).
- `src/test/java/org/vectrix/test/`: JUnit 5 unit tests; experimental tests live under `src/test/java/org/vectrix/experimental/test/`.
- `src/jmh/java/org/vectrix/jmh/` and `src/bench/java/org/vectrix/bench/`: benchmark sources used by benchmark profiles.
- `buildhelper/`: post-compile tooling (`Java6to2`, `ModuleInfoGenerator`).
- `config/`: quality gates (`checkstyle.xml`, `suppressions.xml`, `licenseheader`).

## Build, Test, and Development Commands
- `mvn verify`: full validation (checkstyle, compile, tests, packaging).
- `mvn test`: run test suite only.
- `mvn package -DskipTests`: build jars quickly without tests.
- `mvn clean verify -Pexperimental`: run experimental profile (Vector API/JVMCI/preview features; JDK 19+).
- `mvn clean package -Pbench -DskipTests && java -jar target/benchmarks.jar`: build and run JMH benchmarks.

Use `clean` when switching between default and experimental profiles to avoid stale `target/` artifacts.

## Coding Style & Naming Conventions
- Java style: 4-space indentation, LF line endings, no tabs.
- Keep MIT license header on Java files; Checkstyle enforces this.
- Public types require Javadoc (`JavadocType` check).
- Follow existing naming patterns: `*f`, `*d`, `*i`, `*L` for numeric variants; read-only interfaces end with `c` (for example, `Matrix4fc`).
- Preserve preprocessor markers (`// #ifdef ...`, `// #endif`) used by JCP.

## Testing Guidelines
- Framework: JUnit Jupiter (JUnit 5) via Maven Surefire.
- Test classes should end with `Test` and mirror target classes (for example, `Matrix4fTest`).
- Add focused coverage for precision-sensitive math paths, edge cases, and API compatibility.
- Run `mvn test` before opening a PR; run `mvn verify` for final validation.

## Commit & Pull Request Guidelines
- Git history is not available in this workspace snapshot, so follow a consistent format: imperative, concise subject (for example, `Add Vector3L normalization tests`).
- Keep commits scoped to one concern.
- PRs should include: summary of behavior changes, fork-impact statement (what differs from upstream JOML), affected math types/modules, test evidence (`mvn test`/`mvn verify`), and benchmark deltas when performance paths are changed.
