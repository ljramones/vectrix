# Pass H Summary (2026-03-06)

## Phase Goal
Execute narrow optimization on the two promoted Pass G areas:
1. Geometry intersections
2. Rendering LUT builders

## What Changed

### Geometry
- Added miss-fast rejection to:
  - `Intersectionf.testRayAab(...)`
  - `Intersectiond.testRayAab(...)`
- Added precomputed inverse-direction overloads:
  - `testRayAabInvDir(...)` (float/double)
- Expanded benchmark coverage with:
  - `GeometryIntersectionBenchmark.rayAabInvDirBatch`

### Rendering LUTs
- `SssLutBuilder.build(...)` now precomputes sample kernels and hoists invariants.
- `TransmittanceLutBuilder.build(...)` now uses a tighter integration loop with hoisted parameter scalars and no per-pixel temp-buffer pattern.

## Decision-Quality Outcomes

### Geometry
- Common miss path is now materially cheaper:
  - `rayAabBatch` miss-heavy/random (`count=16384`, float): `7.164 -> 1.863 ns/item` (`~3.85x` faster vs Pass G)
- Mixed/hit-heavy are effectively unchanged.
- `rayAabInvDirBatch` did not beat the optimized baseline shape in this workload.

Decision:
1. Keep miss-fast `rayAabBatch` as default AABB test path.
2. Keep inv-dir overloads as specialized API, not default doctrine.

### LUT Builders
- SSS builder improved strongly on comparable shape:
  - `buildSssLut` (`resolution=64`, `quality=medium`): `2515.40 -> 549.42 ns/item` (`~4.58x`)
- Transmittance builder was effectively neutral on comparable shape:
  - `buildTransmittanceLut` (`resolution=64`, `quality=medium`): `2706.63 -> 2641.26 ns/item` (`~1.02x`)

Decision:
1. Promote current SSS builder path as default.
2. Keep transmittance scalar path; treat further gains as focused follow-up work.

## Pass H Classification
- `Promote`:
  - Geometry miss-path optimization (landed)
  - SSS LUT builder invariant-hoisting optimization (landed)
- `Good enough`:
  - Geometry inv-dir specialization as non-default option
  - Current transmittance loop cleanup
- `Parked for next pass`:
  - transmittance parallel/chunked path exploration
  - deeper ray-triangle and polygon branch-shape tightening

## Artifacts
- Plan:
  - `docs/performance-passH-plan.md`
- Geometry:
  - `docs/performance-passH-geometry-findings.md`
  - `benchmarks/results/2026-03-06/passH-geometry-aabb.*`
  - `benchmarks/results/2026-03-06/passH-prof-geometry-aabb.*`
- LUT:
  - `docs/performance-passH-lut-findings.md`
  - `benchmarks/results/2026-03-06/passH-rendering-lut.*`
  - `benchmarks/results/2026-03-06/passH-prof-rendering-lut.*`

## Validation Notes
- `mvn -q -Pbench -DskipTests package` passed after each code-change task.
- Focused `mvn -q -Dtest=RayAabIntersectionTest,PolygonPointIntersectionTest,FrustumIntersectionTest test` failed in this environment due `Java6to2` build-tool invokedynamic incompatibility (known toolchain issue), not due compile errors in modified classes.
