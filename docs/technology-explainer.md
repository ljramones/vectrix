# Vectrix Technology Explainer

This guide is for developers who are not graphics/math specialists but need to use Vectrix effectively.

## What Vectrix Is

Vectrix is a math foundation library for real-time graphics and simulation workloads.

It provides:
- core 2D/3D/4D math types
- transform and skinning kernels
- curve evaluation and interpolation
- rendering math (SH, FFT, optics, LUT builders)
- compact GPU-friendly data packing

Think of it as the high-performance math layer that engines and tools build on.

## Why This Matters

Most real-time systems are bottlenecked by two things:
- moving too much data
- doing too much math per frame

Vectrix addresses both:
- data layout: SoA containers and compact GPU packing
- compute: batch kernels, SIMD-aware paths, and allocation-free APIs

## Core Concepts (Plain Language)

### Vectors, matrices, quaternions
- `Vector*`: position/direction values
- `Matrix*`: transform and projection math
- `Quaternion*`: robust rotation representation (avoids gimbal lock)

Use quaternions when blending rotations (animation/camera).

### TRS and affine transforms
- TRS = translation + rotation + scale
- Affine transforms are matrix-friendly representations of TRS-like transforms

Use TRS for editing and hierarchy logic; use affine/matrix outputs for kernel and GPU-facing paths.

### SoA vs AoS
- AoS: array of objects (easy to read, slower for batch)
- SoA: one array per component (faster for large loops)

If you process thousands of items per frame, SoA is usually the right choice.

### FAST vs STRICT mode
- `FAST`: optimized for throughput
- `STRICT`: stronger guard rails and reproducibility checks

Typical workflow:
- CI/tests: STRICT
- runtime shipping code: FAST

## Common Tasks and Which APIs to Use

### Animation rotation blending
Use:
- `slerp`/`nlerp` for pairwise interpolation
- `squadControlPoint` + `squad` for smooth multi-keyframe interpolation
- `swingTwist` for axis-constrained decomposition

### Curve-driven motion
Use curve packages:
- Bezier/Hermite/Catmull-Rom/B-spline
- derivatives for velocity/acceleration
- arc-length mapping for near-constant-speed motion

### Skinning and transforms at scale
Use:
- `TransformSoA`, `DualQuatSoA`, `SkinningKernels`
- batch compose and skinning kernels

### Lighting and precomputed rendering math
Use:
- `org.vectrix.sh` for spherical harmonics probes/irradiance
- `org.vectrix.fft` for FFT/convolution workflows
- `org.vectrix.optics` for Fresnel/thin-film calculations
- `org.vectrix.renderingmath` for SSS and atmosphere LUT generation
- `org.vectrix.ltc` for LTC area-light evaluation

## Performance Rules of Thumb

1. Reuse destination objects and scratch arrays.
2. Prefer batch APIs over per-item object methods in hot loops.
3. Measure changes with JMH before and after.
4. Keep STRICT mode checks in tests; avoid in production hot paths unless needed.
5. Use normalized per-op metrics (`ns/call`), not only total `ns/op` for varying batch sizes.

## How to Read the Benchmarks

`BENCHMARKS.md` includes:
- raw JMH time/op numbers
- normalized per-op tables (`ns/call`)

When comparing implementations, normalized per-op is usually the meaningful metric.

## Typical Integration Pattern

1. Prototype with simple object APIs (`Vector3f`, `Quaternionf`, etc.).
2. Move hot loops to SoA/batch kernels.
3. Add benchmark coverage for your path.
4. Keep public contracts stable and document conventions.

## Where to Go Next

- API examples: `API.md`
- Capability inventory: `docs/vectrix-capabilities.md`
- Benchmark methodology/results: `BENCHMARKS.md`
- Release and maintenance process: `docs/release-cut-checklist.md`
