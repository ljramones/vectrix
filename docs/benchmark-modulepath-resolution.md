# Benchmark Module-Path Resolution

Date: 2026-03-05

## Failure Mode
- `mvn -q -Pbench -DskipTests package` failed while compiling `src/bench/java` in named module `org.vectrix`.
- JMH annotation packages were seen in the unnamed module, and `org.vectrix` had no read edge to them.
- Additional read-edge failures appeared for:
  - `jdk.incubator.vector`
  - `jdk.unsupported` (`sun.misc` use via `MemUtil`)

## Root Cause
- Bench sources are added as main sources in the `bench` profile for shading into `target/benchmarks.jar`.
- That keeps benchmark compilation in the same named-module compile shape as production sources.

## Resolution
- Keep current packaging strategy (single `bench` profile + shaded JMH jar).
- Add explicit bench-profile compiler arguments to satisfy module read edges:
  - `--add-modules jdk.incubator.vector,jdk.unsupported`
  - `--add-reads org.vectrix=ALL-UNNAMED`
  - `--add-reads org.vectrix=jdk.incubator.vector`
  - `--add-reads org.vectrix=jdk.unsupported`

## Validation
- `mvn -q -Pbench -DskipTests package` passes.
- Quick JMH runs for latency, throughput, and Phase 2 kernel complete and emit JSON.
- Normalization over generated JSON works.
