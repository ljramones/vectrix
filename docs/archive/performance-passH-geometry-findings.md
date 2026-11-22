# Pass H Geometry Findings (2026-03-06)

## Scope
Pass H geometry work targeted:
- `Intersectionf.testRayAab`
- `Intersectiond.testRayAab`
- `GeometryIntersectionBenchmark` ray/AABB paths

Changes implemented:
1. Added fast miss rejection to `testRayAab` (float/double) when a ray starts outside a slab and points away.
2. Added precomputed-inverse-direction overloads:
   - `Intersectionf.testRayAabInvDir(...)`
   - `Intersectiond.testRayAabInvDir(...)`
3. Added benchmark variant:
   - `GeometryIntersectionBenchmark.rayAabInvDirBatch`

## How It Was Measured
- Build: `mvn -q -Pbench -DskipTests package`
- Constrained full sweep:
  - `benchmarks/results/2026-03-06/passH-geometry-aabb.{json,txt,normalized.csv}`
  - methods: `rayAabBatch`, `rayAabInvDirBatch`, `rayAabSlopeBatch`
  - params: `count=1024,16384`, `precision=float,double`, `distribution=hitHeavy,missHeavy,mixed`, `accessPattern=sequential,random`
- Profiled representative sweep:
  - `benchmarks/results/2026-03-06/passH-prof-geometry-aabb.{json,txt,normalized.csv}`
  - params: `count=16384`, `precision=float`, `distribution=missHeavy`, `accessPattern=random`

## Key Results

### 1) Common miss path became materially cheaper
`rayAabBatch` at `count=16384`, `float`, `verts=16`:
- `missHeavy + random`: `7.164 -> 1.863 ns/item` (~`3.85x` faster vs Pass G)
- `missHeavy + sequential`: `4.148 -> 1.403 ns/item` (~`2.96x` faster vs Pass G)

### 2) Mixed/hit-heavy behavior stayed effectively flat
`rayAabBatch` at `count=16384`, `float`, `verts=16`:
- `mixed + random`: `6.370 -> 6.521 ns/item` (no meaningful change)
- `hitHeavy + random`: `6.453 -> 6.456 ns/item` (no meaningful change)

### 3) Tight inv-dir kernel shape did not win in this benchmark shape
At `count=16384`, `float`, `missHeavy + random`:
- `rayAabBatch`: `1.863 ns/item`
- `rayAabInvDirBatch`: `6.525 ns/item`
- `rayAabSlopeBatch`: `4.770 ns/item`

Interpretation:
- After miss-fast rejection was added, baseline `rayAabBatch` is best for miss-heavy workloads in this data shape.
- Extra inv-dir array loads and slope-dispatch overhead did not beat the optimized baseline path here.

### 4) Profiled allocation signal (representative miss-heavy/random shape)
- `rayAabBatch`: `15.013 B/op`
- `rayAabInvDirBatch`: `53.067 B/op`
- `rayAabSlopeBatch`: `40.799 B/op`

## Answers To Pass H Track Questions

### Is the common miss path cheap enough?
Yes, materially improved. Miss-heavy random/scattered cases now run much cheaper and are no longer the obvious bottleneck within this subset.

### Is there a tighter kernel shape hiding behind generic helpers?
Partially. The winning “tightening” was an early branch-level miss reject inside the baseline kernel, not a separate inv-dir/slope path.

### Does layout/traversal matter as much here as in transforms?
Yes, but distribution (miss-heavy vs mixed/hit-heavy) is the first-order effect in this kernel family. Traversal still shifts cost, but miss-path behavior dominates the large improvement.

## Policy Impact
1. Keep `Intersectionf/d.testRayAab` (with miss-fast rejection) as the default hot path.
2. Keep `testRayAabInvDir` available for specialized experiments; do not promote as default from current data.
3. Keep `rayAabSlopeBatch` as benchmarked reference path; not the default for this workload shape.

## Next Geometry Step
If we continue geometry optimization, prioritize:
1. `rayTriangle` miss/parallel rejection shape.
2. `PolygonsIntersection` branch behavior under clustered/random point sets.
3. Additional miss-heavy distributions closer to picking/culling call patterns.
