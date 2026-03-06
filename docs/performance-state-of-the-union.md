# Performance State Of The Union

Date: 2026-03-06

## What Is Settled
1. Packed-affine is the default bulk transform execution form.
2. Packed-affine is the default upload-prep form.
3. Matrix-palette tight LBS (`skinLbs4MatrixPalette` / `kernelMatrixTight`) is the default CPU skinning baseline.
4. Miss-fast `Intersection*.testRayAab` is the default AABB query path for miss-heavy/scattered workloads.
5. Invariant-hoisted `SssLutBuilder` is the default SSS precompute path.
6. `Matrix4f` is boundary/interoperability format, not hot-path bulk default.
7. Locality remains first-order for large workloads.

## What Lost
1. Matrix-centric bulk transform/update/upload paths as runtime defaults.
2. Generic chunk wrappers as presumed performance optimizations.
3. Immediate promotion of vector skinning (`skinLbs4Vector`) to default.
4. Generic quaternion-LBS kernel (`kernelLbs`) as a default fast path in equivalence shape.
5. `testRayAabInvDir` as a default ray/AABB path for current benchmark shape.
6. Expectation that LUT cleanup would produce equal gains for transmittance and SSS.

## What Is Experimental
1. `skinLbs4Vector` and SoA SIMD skinning variants.
2. Broader SoA skinning layout expansion beyond benchmark-proven wins.
3. Any dual-quaternion default path for performance mode (remains quality mode).
4. Parallel/chunked transmittance LUT build paths.

## Evidence Ladder Completed
1. Primitive and batch benchmarks.
2. Representation policy benchmarks.
3. Profile-backed packed-affine mechanism checks.
4. Composed integration slice validating winners survive composition.
5. Targeted hotspot pass (Pass H) validating geometry miss-path and LUT-builder behavior.

## Regression Protection In Place
1. Phase B kernel gates:
   - `scripts/bench-regression-phaseb.sh`
2. Skinning gates:
   - `scripts/bench-regression-skinning.sh`
3. Composed integration gate:
   - `scripts/bench-regression-integration.sh`
4. Pass H gates:
   - `scripts/bench-regression-passh.sh`

## Current Runtime Doctrine
1. Runtime transforms stay ergonomic (`Transformf`/TRS-oriented input).
2. Materialize late into packed-affine for bulk work.
3. Build rigid matrix palette once per update and run skinning via `skinLbs4MatrixPalette`.
4. Update bounds and prepare uploads through packed-affine-first paths.
5. Use miss-fast `Intersection*.testRayAab` as default ray/AABB query route.
6. Use invariant-hoisted `SssLutBuilder` as default SSS LUT route.
7. Keep matrix path available as explicit fallback/interoperability path.

## Parked Work
1. Vector skinning promotion is parked pending clear repeated wins.
2. Large benchmark-suite expansion is parked in favor of subsystem integration.
3. Matrix-default discussions are parked unless new contradictory data appears.
4. Transmittance LUT deep optimization is parked pending dedicated parallel/transcendental strategy pass.

## Next Focus
Subsystem integration and API routing so the fast path is the natural path in production use, with transmittance LUT follow-up only if profiler-driven need is confirmed.
