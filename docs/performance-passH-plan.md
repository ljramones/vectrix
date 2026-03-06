# Pass H Plan (Geometry Intersections + Rendering LUT Builders)

Date: 2026-03-06

## Objective
Execute a narrow optimization pass on the two promoted Pass G hotspots:
1. Geometry intersection kernels
2. Rendering LUT builders

Pass H remains benchmark-driven and ends with explicit keep/change decisions.

## Scope

### Track A: Geometry Intersections
Primary APIs:
- `RayAabIntersection`
- `Intersectionf` / `Intersectiond`
- `PolygonsIntersection`

Benchmark focus:
- `hitHeavy`
- `missHeavy`
- `mixed`
- `accessPattern=sequential,random`

Work items:
1. Profile current kernels for branch behavior and allocation signal.
2. Implement targeted tight-path changes where data indicates leverage.
3. Benchmark with consistent workload shapes and compare against baseline.
4. Document whether win comes from kernel specialization or workload policy.

Success criteria (any one qualifies):
1. A tighter kernel shape that clearly wins in hit-heavy or miss-heavy cases.
2. A stable batch/layout policy decision backed by benchmark data.
3. A data-backed conclusion that current path is already good enough and should be protected rather than rewritten.

### Track B: Rendering LUT Builders
Primary APIs:
- `SssLutBuilder`
- `TransmittanceLutBuilder`

Benchmark focus:
- representative resolutions (`32,64,128,256`)
- quality presets where available
- allocation profile and total build time

Work items:
1. Profile where time is spent (arithmetic vs transcendental vs memory vs allocation).
2. Hoist loop-invariant math and remove repeated work.
3. Evaluate reduced-allocation write shapes.
4. Optionally evaluate bounded parallel path if profiler data suggests payoff.

Success criteria (any one qualifies):
1. Materially lower build time.
2. Lower allocation footprint.
3. Clearer separation of invariant math from per-cell work.
4. Data-backed proof that parallelization is worthwhile.

## Commit Boundaries
1. `Add Pass H plan document` (this file)
2. Geometry intersection implementation + benchmark updates
3. Geometry findings memo
4. LUT builder implementation + benchmark updates
5. LUT findings memo
6. Pass H final ranking/decision summary

## Measurement Discipline
- Keep constrained runs for iteration speed.
- Preserve both raw JSON and normalized CSV for every Pass H run.
- Use profiled sweeps (`-prof gc -prof stack`) on representative shapes before final conclusions.
- Do not broaden benchmark surface during Pass H; optimize only promoted targets.

## Deliverables
- Code changes for Track A and Track B
- Updated benchmark artifacts under `benchmarks/results/<date>/`
- `docs/performance-passH-geometry-findings.md`
- `docs/performance-passH-lut-findings.md`
- `docs/performance-passH-summary.md`
