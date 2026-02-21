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
