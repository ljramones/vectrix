# Vectrix documentation

Start with [getting-started.md](getting-started.md), then [conventions.md](conventions.md). Every example in these
pages is quoted from the tested programs in [`../examples`](../examples/README.md).

## Reference

- [Getting started](getting-started.md): install, modules, JVM flags, a first program.
- [Conventions](conventions.md): precision suffixes, destination parameters, arrays and scratch, matrices,
  FAST/STRICT, threading, errors, configuration properties.
- [Package map](package-map.md): the 30 packages, in layers.
- [Determinism](determinism.md): which settings change result bits, and how to get identical results everywhere.
- [Performance](performance.md): why it is fast, hot-path rules, and which paths to use. Measured numbers are in
  [benchmarks.md](benchmarks.md).
- [API policy](api-policy.md): stability, `@Internal`, deprecation and versioning.
- [JOML divergence](joml-divergence.md): why Vectrix forked JOML, and what has changed since.

## Guides

| Guide | Covers |
|---|---|
| [Scenes: transforms, bounds and culling](guides/scene-and-culling.md) | Transform hierarchies in batch, world bounds, frustum culling |
| [Cameras and projections](guides/camera-and-projection.md) | Depth conventions, reverse-Z, jitter, oblique clipping, shadow cascades, clustered lighting |
| [Large worlds](guides/large-worlds.md) | Exact positions far from the origin, camera-relative rendering |
| [Animation](guides/animation.md) | Rotation interpolation, poses, IK, skinning |
| [Geometry and physics](guides/geometry-and-physics.md) | Intersections, closest points, bounding volumes, SDFs, mass properties, small linear algebra |
| [Curves, easing, sampling and noise](guides/curves-sampling-noise.md) | Splines and arc length, easing, low-discrepancy sampling, hashes, noise |
| [Rendering math](guides/rendering-math.md) | Spherical harmonics, LTC area lights, BRDFs, optics, atmosphere, color |
| [GPU data](guides/gpu-data.md) | Buffer layouts, packed formats, writing to buffers and memory segments |
| [Signal processing](guides/signal-processing.md) | FFTs, filters, spectra, transforms, Wiener filtering |
| [State estimation](guides/state-estimation.md) | Kalman, extended and unscented Kalman, particle filters |
| [Machine-learning kernels](guides/machine-learning.md) | GEMM, activations, losses, optimizers, attention, reproducibility |

## For maintainers

- [Release checklist](maintainers/release-cut-checklist.md)
- [Benchmarking guide](maintainers/benchmarking-guide.md) and [benchmark environment](maintainers/benchmark-environment.md)
- [Benchmark policy](../benchmarks/POLICY.md)
