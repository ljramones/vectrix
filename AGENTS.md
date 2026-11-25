# Repository Guidelines

## Project Structure & Module Organization
`vectrix` is a rendering-first math library forked from JOML and now organized by capability:
- Four jar modules (4.0): `vectrix-core`, `vectrix-render`, `vectrix-signal`, `vectrix-ml`; `docs/package-map.md` lists each one's packages. Paths below are under `vectrix-core/` unless noted.
- `src/main/java/org/dynamisengine/vectrix/core`: scalar-safe vector/matrix/quaternion primitives.
- `src/main/java/org/dynamisengine/vectrix/affine`, `.../soa`, `.../simd`: transform kernels, SoA containers, Vector API paths.
- `src/main/java/org/dynamisengine/vectrix/geometry`, `.../simd`: culling/intersections, the SIMD switch; `vectrix-render/.../gpu`: packing and compression.
- `vectrix-*/src/test/java/org/dynamisengine/vectrix/test`: each jar's unit and parity tests; `vectrix-tests/`: the tests that span the jars.
- `vectrix-tests/src/bench/java/org/dynamisengine/vectrix/bench` and `vectrix-tests/src/jmh/java`: JMH benchmarks.
- `vectrix-gpu/`: GPU twins of Vectrix kernels (Slang, bit for bit with the CPU; separate Maven project, in the release ZIP since 4.0.1). Methods that compute in double port through `vectrix-gpu/src/main/slang/f64.slang` (binary64 on 64-bit integers; needs `INT64`). See `vectrix-gpu/README.md`.

## Build, Test, and Development Commands
- `mvn -q test`: run all JUnit tests.
- `mvn -q verify`: full validation (style + tests + packaging).
- `mvn -q clean package -Pbench -DskipTests`: build `vectrix-tests/target/benchmarks.jar`.
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
- Performance-sensitive changes should include/extend JMH benchmarks in `vectrix-tests/src/bench/java/org/dynamisengine/vectrix/bench`.

## Commit & Pull Request Guidelines
- Commit messages: imperative and scoped (example: `Add Affine4f SIMD multiply benchmark`).
- Keep one concern per commit (API, perf kernel, test, docs).
- PRs should include:
  - behavior change summary,
  - benchmark deltas for hot-path changes,
  - exact validation commands run (for example `mvn -q test`),
  - notes on FAST vs STRICT behavior if touched.
