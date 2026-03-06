# Why Vectrix Is Fast

Date: 2026-03-06

This is the short technical explanation of where Vectrix speed comes from and how it is kept stable.

## 1) Data-First Runtime Representation
1. Bulk runtime work defaults to packed-affine (`3x4`) instead of full matrix object paths.
2. Upload and bounds/update paths consume packed layouts directly.
3. `Matrix4f` remains boundary/interoperability format, not default hot-path format.

Why it matters:
- lower bytes moved
- simpler write paths
- fewer conversions in frame-critical loops

## 2) Tight Kernels Over Generic Abstractions
1. Hot loops are specialized when benchmark data proves a gain.
2. Example: skinning baseline is matrix-palette tight LBS, not generic kernel abstraction.
3. Example: geometry ray/AABB path now uses miss-fast rejection to reduce miss-heavy query cost.

Why it matters:
- fewer unpredictable branches
- less per-iteration overhead
- better JIT inlining/optimization opportunities

## 3) Locality-First Execution
1. Benchmarks consistently track sequential vs random/scattered traversal.
2. Policy treats locality as first-order for large workloads.
3. Chunk wrappers are orchestration tools, not assumed optimizations by themselves.

Why it matters:
- cache behavior dominates many engine kernels
- stable `ns/item` scaling at larger batch sizes is easier to maintain

## 4) Benchmark Governance, Not One-Off Tuning
1. Every major optimization pass ends with findings docs and policy updates.
2. Winners are moved into regression gates.
3. Decision memos capture what is default, discouraged, and experimental.

Why it matters:
- performance improvements survive refactors
- architecture is driven by measured behavior, not assumptions

## 5) JVM-Specific Practicality
Vectrix is tuned for current JVM behavior (JDK 25 target):
1. HotSpot JIT specialization/inlining on hot kernels.
2. Escape analysis and allocation elimination where shapes permit.
3. Vector API paths where SIMD shape is beneficial.

This is not “Java is magically fast.” It is measured kernel design aligned to JVM strengths.

## Evidence Anchors
1. [performance-showcase.md](/Users/larrymitchell/Dynamis/vectrix/docs/performance-showcase.md)
2. [BENCHMARKS.md](/Users/larrymitchell/Dynamis/vectrix/BENCHMARKS.md)
3. [engine-runtime-defaults.md](/Users/larrymitchell/Dynamis/vectrix/docs/engine-runtime-defaults.md)
4. [performance-state-of-the-union.md](/Users/larrymitchell/Dynamis/vectrix/docs/performance-state-of-the-union.md)
