# Benchmarking Guide

## Build
- `mvn -q clean package -Pbench -DskipTests`

## Run Profiles
- Quick sanity run:
  - `./scripts/bench-quick.sh`
- Full decision-grade run:
  - `./scripts/bench-full.sh`
- Profiled run (`gc` and `stack` profilers):
  - `./scripts/bench-prof.sh`
- Regression gate run:
  - `./scripts/bench-regression.sh`

All profile scripts call `scripts/bench-run.sh` and write outputs to:
- `benchmarks/results/YYYY-MM-DD/<profile>.json`
- `benchmarks/results/YYYY-MM-DD/<profile>.txt`

`target/benchmarks/latest.*` is also updated for convenience.

## Benchmark Modes
- Latency benchmarks inherit `LatencyBenchmark` (`Mode.AverageTime`).
- Throughput benchmarks inherit `ThroughputBenchmark` (`Mode.Throughput`).
- Shared defaults are defined in `BaseBenchmark`:
  - `@Fork(3)`
  - `@Warmup(iterations = 6, time = 1s)`
  - `@Measurement(iterations = 10, time = 1s)`

## JSON Normalization
Generate normalized metrics (`items`, `ns/item`, `items/sec`) from JSON output:
- `./scripts/bench-normalize.py benchmarks/results/2026-03-05/full.json`
- Output: `benchmarks/results/2026-03-05/full.normalized.csv`

The normalizer also emits canonical benchmark IDs using the naming format:
- `vectrix.<category>.<kernel>.<variant>`

## Allocation Monitoring
Use profiled runs to inspect allocation metrics:
- `gc.alloc.rate`
- `gc.alloc.rate.norm`

For hot kernels, target `0 B/op` and treat any persistent allocation as a defect to investigate.

## Regression Compare
Compare two JMH JSON runs:
- `tools/bench-compare benchmarks/baselines/full.json benchmarks/results/2026-03-05/full.json`

The comparer flags regressions beyond configured thresholds and exits non-zero on failure.

## Maven Benchmark Preset Profiles
The project exposes benchmark preset profiles for repeatable settings:
- `bench-quick`
- `bench-full`
- `bench-prof`
- `bench-regression`

Use together with `-Pbench` when building benchmark artifacts.

## Named-Module Note
- The prior `-Pbench` named-module visibility blocker is resolved.
- Resolution details are documented in [benchmark-modulepath-resolution.md](/Users/larrymitchell/Dynamis/vectrix/docs/benchmark-modulepath-resolution.md).
