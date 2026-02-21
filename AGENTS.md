# Repository Guidelines

## Project Structure & Module Organization
`vectrix` is a rendering-first math library forked from JOML and now organized by capability:
- `src/main/java/org/vectrix/core`: scalar-safe vector/matrix/quaternion primitives.
- `src/main/java/org/vectrix/affine`, `.../soa`, `.../simd`: transform kernels, SoA containers, Vector API paths.
- `src/main/java/org/vectrix/geometry`, `.../gpu`, `.../experimental`: culling/intersections, packing/compression/layouts, opt-in fast modes.
- `src/test/java/org/vectrix/test`: unit and parity tests.
- `src/bench/java/org/vectrix/bench` and `src/jmh/java`: JMH benchmarks.

## Build, Test, and Development Commands
- `mvn -q test`: run all JUnit tests.
- `mvn -q verify`: full validation (style + tests + packaging).
- `mvn -q clean package -Pbench -DskipTests`: build `target/benchmarks.jar`.
- `./scripts/bench-run.sh`: run benchmark suite (CSV output in `target/benchmarks/`).
- Example smoke run:
  `java --add-modules jdk.incubator.vector -jar target/benchmarks.jar ".*FrustumCullingBenchmark.*" -f 0 -wi 1 -i 1`

## Coding Style & Naming Conventions
- Java, 4-space indentation, no tabs.
- Keep existing license headers and JCP preprocessor guards (`// #ifdef ...`).
- Mirror established type suffixes (`*f`, `*d`, `*i`, `*L`; read-only `*c`).
- Prefer explicit package placement by domain (`core`, `affine`, `geometry`, `gpu`, `soa`, `simd`), not by feature branch.

## Testing Guidelines
- Framework: JUnit 5 (Surefire).
- Name tests `*Test` and colocate coverage with changed APIs.
- For SIMD or fast-mode changes, add scalar parity tests and edge-case coverage (NaN, zero-length, opposite vectors, large batches).
- Performance-sensitive changes should include/extend JMH benchmarks in `src/bench/java/org/vectrix/bench`.

## Commit & Pull Request Guidelines
- Commit messages: imperative and scoped (example: `Add Affine4f SIMD multiply benchmark`).
- Keep one concern per commit (API, perf kernel, test, docs).
- PRs should include:
  - behavior change summary,
  - benchmark deltas for hot-path changes,
  - exact validation commands run (for example `mvn -q test`),
  - notes on FAST vs STRICT behavior if touched.
