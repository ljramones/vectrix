# Docs Index

User documentation for Vectrix. Start with the repository [`README.md`](../README.md), then [`API.md`](../API.md).

## Using Vectrix
- [`vectrix-capabilities.md`](vectrix-capabilities.md): what the library offers, grouped by use case, with the
  classes to look at.
- [`technology-explainer.md`](technology-explainer.md): the concepts behind the packages, for readers new to the
  maths.
- [`conventions.md`](conventions.md): handedness, layouts, the mutability model, the packed 3x4 affine layout and
  STRICT mode.
- [`determinism.md`](determinism.md): every setting that changes output bits, `StrictMath` mode, and the replay
  fingerprint.
- [`joml-divergence.md`](joml-divergence.md): why Vectrix forked JOML, what has been fixed since, and differences to
  expect when porting from JOML.
- [`signal-and-ml-roadmap.md`](signal-and-ml-roadmap.md): the design of the FFT, `dsp`, `blas` and `nn` packages:
  ownership, threading, storage and determinism.

## Runtime and performance policies
- [`api-policy.md`](api-policy.md): API stability and compatibility.
- [`hot-path-policy.md`](hot-path-policy.md): rules for batch-first, locality-first hot code.
- [`transform-representation-policy.md`](transform-representation-policy.md): which transform form to use where.
- [`skinning-runtime-policy.md`](skinning-runtime-policy.md): default and quality skinning paths.
- [`engine-runtime-defaults.md`](engine-runtime-defaults.md): the runtime defaults engines should use.
- [`why-vectrix-is-fast.md`](why-vectrix-is-fast.md) and [`performance-showcase.md`](performance-showcase.md): how the
  kernels get their speed, and the evidence.

## Benchmarking
- [`benchmarking-guide.md`](benchmarking-guide.md): building, running, profiling and comparing benchmarks.
- [`benchmark-environment.md`](benchmark-environment.md): the recorded benchmark environment.
- [`../BENCHMARKS.md`](../BENCHMARKS.md) and [`../benchmarks/POLICY.md`](../benchmarks/POLICY.md): results and
  regression thresholds.

## Maintaining
- [`release-cut-checklist.md`](release-cut-checklist.md): how a release is cut.
