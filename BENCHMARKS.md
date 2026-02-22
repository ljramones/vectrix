# Benchmarks

This document defines the benchmark methodology and records current baseline results for Vectrix.

## Tooling
- Harness: JMH
- Java: `--add-modules jdk.incubator.vector`
- Entry: `./scripts/bench-run.sh`
- Bench artifact: `target/benchmarks.jar`

## Methodology
- Build benchmark jar:
  - `mvn -q clean package -Pbench -DskipTests`
- Run benchmark suite:
  - `BENCH_REGEX='org.vectrix.bench.*' ./scripts/bench-run.sh`
- Recommended publication profile:
  - `FORKS=2 WARMUP_ITERS=5 MEASURE_ITERS=8 THREADS=1 TIME_UNIT=ns`
- Output artifacts:
  - `target/benchmarks/latest.txt`
  - `target/benchmarks/latest.csv`

## Coverage
- Existing domains:
  - transforms, culling, GPU packing/layout, skinning, reductions, mesh math
- Added domains in this release:
  - curves (`CurveBenchmark`)
  - spherical harmonics (`ShBenchmark`)
  - FFT/convolution (`FftBenchmark`)
  - optics (`OpticsBenchmark`)

## Current Baseline
Baseline capture profile used:
- `BENCH_REGEX='org.vectrix.bench.(CurveBenchmark|ShBenchmark|FftBenchmark|OpticsBenchmark).*'`
- `FORKS=1 WARMUP_ITERS=1 MEASURE_ITERS=1 THREADS=1 TIME_UNIT=ns`

Hardware/software:
- CPU model: Apple M4 Max
- OS: macOS 26.3 (Darwin 25.3.0 arm64)
- JVM: Temurin OpenJDK 25.0.1+8 LTS
- Date: 2026-02-22 15:14:08 EST

Result artifacts:
- `target/benchmarks/jmh-20260222-150521.csv`
- `target/benchmarks/jmh-20260222-150521.txt`

Representative results (AverageTime, ns/op):
- CurveBenchmark:
  - `bezierEvaluateLoop(count=256)` = 463.155
  - `bezierEvaluateLoop(count=4096)` = 7455.766
  - `bezierEvaluateLoop(count=16384)` = 30153.349
  - `mapArcLengthLoop(count=256)` = 1143.668
  - `mapArcLengthLoop(count=4096)` = 16802.590
  - `mapArcLengthLoop(count=16384)` = 61175.956
- FftBenchmark:
  - `circularConvolution(complexCount=256)` = 5168.600
  - `circularConvolution(complexCount=1024)` = 25021.071
  - `circularConvolution(complexCount=4096)` = 119555.242
  - `forwardInverse(complexCount=256)` = 3352.452
  - `forwardInverse(complexCount=1024)` = 16172.115
  - `forwardInverse(complexCount=4096)` = 77706.213
- OpticsBenchmark:
  - `fresnelDielectricLoop(count=256)` = 451.077
  - `fresnelDielectricLoop(count=4096)` = 7211.630
  - `fresnelDielectricLoop(count=16384)` = 29080.754
  - `thinFilmRgbLoop(count=256)` = 4524.113
  - `thinFilmRgbLoop(count=4096)` = 95849.576
  - `thinFilmRgbLoop(count=16384)` = 433526.007
- ShBenchmark:
  - `evaluateL3(count=256)` = 2380.876
  - `evaluateL3(count=4096)` = 38450.799
  - `evaluateL3(count=16384)` = 154013.449
  - `projectL3(count=256)` = 3439.568
  - `projectL3(count=4096)` = 53115.972
  - `projectL3(count=16384)` = 193231.278

## Notes
- Baselines should be compared only across matching hardware/JVM settings.
- Use longer runs and isolated machine conditions for release-grade publication.
