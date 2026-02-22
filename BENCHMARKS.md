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
  - quaternion rotation hot paths (`QuaternionRotationBenchmark`)
  - SH zero-allocation paths (`ShHotPathBenchmark`)
  - LTC table/evaluation (`LtcBenchmark`)
  - low-discrepancy scrambled Sobol (`LowDiscrepancyBenchmark`)
  - SoA skinning parity (`SkinningKernelBenchmark`)

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

Normalized per-call costs (`ns/op-per-call = ns/op ÷ count`):

| Benchmark | 256 | 4096 | 16384 |
|---|---:|---:|---:|
| `bezierEvaluateLoop` | 1.809 | 1.820 | 1.840 |
| `mapArcLengthLoop` | 4.468 | 4.102 | 3.734 |
| `circularConvolution` | 20.190 | 24.435 | 29.188 |
| `forwardInverse` | 13.096 | 15.793 | 18.971 |
| `fresnelDielectricLoop` | 1.762 | 1.761 | 1.776 |
| `thinFilmRgbLoop` | 17.672 | 23.401 | 26.461 |
| `evaluateL3` | 9.300 | 9.387 | 9.401 |
| `projectL3` | 13.436 | 12.968 | 11.794 |

Interpretation notes:
- `thinFilmRgbLoop` shows a size-dependent rise in per-call cost; this was optimized by sharing interface/Fresnel terms across RGB channels.
- FFT paths (`forwardInverse`, `circularConvolution`) also rise with problem size as expected for increasing memory/compute pressure.
- `mapArcLengthLoop` and `projectL3` do not regress with larger counts; both trend slightly cheaper per call at scale.

## Full Coverage Pass (Latest)
Latest end-to-end capture profile:
- `BENCH_REGEX='org.vectrix.bench.(CurveBenchmark|FftBenchmark|OpticsBenchmark|ShBenchmark|QuaternionRotationBenchmark|ShHotPathBenchmark|LtcBenchmark|LowDiscrepancyBenchmark|SkinningKernelBenchmark).*'`
- `FORKS=1 WARMUP_ITERS=1 MEASURE_ITERS=1 THREADS=1 TIME_UNIT=ns`

Result artifacts:
- `target/benchmarks/jmh-20260222-153429.csv`
- `target/benchmarks/jmh-20260222-153429.txt`

Thin-film scaling after optimization (`thinFilmRgbLoop`):
- `count=256`: `4287.360 ns/op` (`16.748 ns/call`)
- `count=4096`: `98247.842 ns/op` (`23.986 ns/call`)
- `count=16384`: `402556.168 ns/op` (`24.570 ns/call`)

Representative new benchmark families (AverageTime, ns/op):
- `QuaternionRotationBenchmark.swingTwistLoop(count=4096)` = `26891.263`
- `QuaternionRotationBenchmark.angularVelocityLoop(count=4096)` = `39889.576`
- `QuaternionRotationBenchmark.integrateAngularVelocityLoop(count=4096)` = `19030.103`
- `ShHotPathBenchmark.projectSampleZeroAllocLoop(count=4096)` = `31328.699`
- `ShHotPathBenchmark.evaluateIrradianceZeroAllocLoop(count=4096)` = `18695.931`
- `LtcBenchmark.ltcTableSampleLoop(count=4096)` = `39771.203`
- `LtcBenchmark.ltcFormFactorRectClippedLoop(count=4096)` = `187210.109`
- `LowDiscrepancyBenchmark.sobolScrambledLoop(count=4096)` = `58707.729`
- `LowDiscrepancyBenchmark.sobolScrambledBatch2DLoop(count=4096)` = `67525.768`
- `SkinningKernelBenchmark.skinDualQuat4(vertices=4096)` = `45042.917`

## Notes
- Baselines should be compared only across matching hardware/JVM settings.
- Use longer runs and isolated machine conditions for release-grade publication.
