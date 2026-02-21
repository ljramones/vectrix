# Vectrix

Vectrix is a Java math library focused on real-time graphics and rendering workloads. It started from a JOML fork and has evolved into a broader rendering math kernel for engine-oriented use cases (OpenGL, Vulkan, and API-agnostic pipelines).

It keeps the familiar mutable/read-only style from classic JOML while adding batch-oriented data layouts, transform pipelines, geometry/culling kernels, GPU packing utilities, and experimental performance paths.

## Core Capabilities

### Core Math (`org.vectrix.core`)
- Vectors, matrices, quaternions, axis-angle, interpolation, noise, utilities.
- Float/double/int/long families (`Vector*`, `Matrix*`, `Quaternion*`, etc.).
- Allocation-free operation style for hot loops.
- Stack variants and read-only interfaces where applicable.

### Affine & Transform System (`org.vectrix.affine`)
- `Transformf` and `RigidTransformf` (TRS / rigid workflows).
- `Affine4f` (3x4 affine matrix type).
- `DualQuatTransformf` and transform kernels for composition and rigid inverse paths.

### SoA and Batch Processing (`org.vectrix.soa`)
- `TransformSoA`, `DualQuatSoA`, `AABBSoA` containers.
- Batch kernels including skinning paths (`SkinningKernels`) with scalar/SIMD-oriented execution.

### SIMD Support (`org.vectrix.simd`)
- Vector API integration and SIMD utility types (for example `Vector4fa`, `SimdSupport`).
- Runtime paths designed to keep scalar fallbacks available.

### Sampling (`org.vectrix.sampling`)
- Best-candidate, Poisson, spiral, stratified, and uniform sampling utilities.
- 2D/3D callback-driven generators for stochastic rendering workflows.

### Geometry & Culling (`org.vectrix.geometry`)
- Frustum extraction and plane handling (`FrustumPlanes`, `FrustumIntersection`, `FrustumRayBuilder`).
- Culling kernels (`CullingKernels`) and mesh math helpers (`MeshMath`).
- Intersection/raycast helpers (`RayAabIntersection`, `Intersectionf/d`).

### GPU-Focused Utilities (`org.vectrix.gpu`)
- Vertex layout and attribute descriptors (`VertexLayout`, `VertexAttribute`, `StdLayout`).
- Packing/compression primitives:
  - `Half` (float16)
  - normalized packed formats (`PackedNorm`)
  - octahedral normal packing (`OctaNormal`)
  - quaternion compression (`QuatCompression`)

### Experimental Controls (`org.vectrix.experimental`)
- `MathMode` (`FAST` / `STRICT`) and kernel mode controls.
- Deterministic vs throughput-oriented reduction paths.
- Experimental JDK feature paths (Vector API, FFM/JVMCI-oriented build profiles).

## Build and Test

Requirements:
- JDK 8+ for default build profile.
- Newer JDK (for example JDK 25) for `bench`/`experimental` workflows.

Common commands:

```bash
mvn clean verify                    # full build + tests + checks
mvn test                            # tests only
mvn -Pexperimental test             # experimental feature path
mvn -Pbench -DskipTests package     # build target/benchmarks.jar
```

## Benchmarks

Vectrix ships two benchmark tracks:
- `src/jmh/java` (legacy/original JMH track)
- `src/bench/java` (expanded rendering-focused microbenchmarks)

Run focused benchmark jar:

```bash
java --add-modules=jdk.incubator.vector -jar target/benchmarks.jar
```

Compare runs against a baseline:

```bash
./scripts/bench-compare.sh \
  benchmarks/baselines/ci-jdk25-smoke.csv \
  target/benchmarks/manual-perf.csv \
  12 20 20
```

### Benchmark Snapshot (2026-02-21)

Latest focused run (`target/benchmarks/manual-perf-after-fix4.csv`), JDK 25.0.1, Apple M4 Pro, JMH non-forked (`-f 0`), 3 warmup / 5 measurement iterations, `1s` each.

Raw JMH scores (unit: `ns/op`, i.e., nanoseconds per benchmark method invocation):

| Benchmark | 64 | 256 | 4096 |
|---|---:|---:|---:|
| `Affine4fBenchmark.mulAffineChain` | 308.770 | 1241.781 | 28092.499 |
| `Affine4fBenchmark.mulMatrix4x3Chain` | 310.097 | 1255.272 | 24076.744 |
| `Affine4fBenchmark.trsToAffine` | 164.866 | 650.433 | 13847.967 |
| `Affine4fBenchmark.trsToMatrix4x3` | 171.147 | 683.317 | 14066.039 |

| Benchmark | 1024 | 16384 | 65536 |
|---|---:|---:|---:|
| `ReductionBenchmark.dotFast` | 176.956 | 2930.368 | 11850.559 |
| `ReductionBenchmark.dotStrict` | 666.641 | 10851.425 | 43281.417 |
| `ReductionBenchmark.sumFast` | 136.997 | 2513.919 | 10126.466 |
| `ReductionBenchmark.sumStrict` | 593.291 | 9802.571 | 39872.787 |

Normalized per-individual-call cost (derived from the batched methods):
- `mulAffineChain` / `mulMatrix4x3Chain`: divided by `(size - 1)` matrix multiplies.
- `trsToAffine` / `trsToMatrix4x3`: divided by `size` conversions.
- `sum*` / `dot*`: divided by `size` processed elements.

Affine/transform normalized scores (unit: `ns per individual call`):

| Affine/Transform Benchmark | 64 | 256 | 4096 |
|---|---:|---:|---:|
| `mulAffineChain` | 4.901 | 4.870 | 6.860 |
| `mulMatrix4x3Chain` | 4.922 | 4.923 | 5.880 |
| `trsToAffine` | 2.576 | 2.541 | 3.381 |
| `trsToMatrix4x3` | 2.674 | 2.669 | 3.434 |

Reduction normalized scores (unit: `ns per element`):

| Reduction Benchmark | 1024 | 16384 | 65536 |
|---|---:|---:|---:|
| `dotFast` | 0.173 | 0.179 | 0.181 |
| `dotStrict` | 0.651 | 0.662 | 0.660 |
| `sumFast` | 0.134 | 0.153 | 0.155 |
| `sumStrict` | 0.579 | 0.598 | 0.608 |

Note: keep fork count and JVM flags consistent when comparing runs. This repo currently uses non-forked JMH in constrained environments; use forked runs on a quiet machine for release-grade baselines.

## Repository Layout

- `src/main/java/org/vectrix/core` – scalar-safe baseline math types.
- `src/main/java/org/vectrix/affine` – TRS/affine/dual-quat transform system.
- `src/main/java/org/vectrix/simd` – Vector API helpers.
- `src/main/java/org/vectrix/soa` – structure-of-arrays containers/kernels.
- `src/main/java/org/vectrix/geometry` – culling/intersection/mesh geometry math.
- `src/main/java/org/vectrix/gpu` – GPU packing/layout/compression helpers.
- `src/main/java/org/vectrix/sampling` – stochastic sampling utilities.
- `src/main/java/org/vectrix/experimental` – fast/strict and experimental kernels.
- `src/test/java` – unit and property-style tests.
- `src/bench/java`, `src/jmh/java` – performance suites.
- `benchmarks/` and `scripts/` – baselines, perf automation, regression checks.

## Project Direction

Vectrix is intended as a foundational math package for higher-level mesh management and rendering libraries. The primary focus is practical rendering performance, deterministic options where needed, and benchmark-driven evolution.

## License

MIT. See [LICENSE](LICENSE).
