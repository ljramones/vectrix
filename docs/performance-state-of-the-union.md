# Performance State Of The Union

Date: 2026-03-06

## What Is Settled
1. Packed-affine is the default bulk transform execution form.
2. Packed-affine is the default upload-prep form.
3. `skinLbs4` is the default CPU skinning baseline.
4. `Matrix4f` is boundary/interoperability format, not hot-path bulk default.
5. Locality remains first-order for large workloads.

## What Lost
1. Matrix-centric bulk transform/update/upload paths as runtime defaults.
2. Generic chunk wrappers as presumed performance optimizations.
3. Immediate promotion of vector skinning (`skinLbs4Vector`) to default.

## What Is Experimental
1. `skinLbs4Vector` and SoA SIMD skinning variants.
2. Broader SoA skinning layout expansion beyond benchmark-proven wins.
3. Any dual-quaternion default path for performance mode (remains quality mode).

## Evidence Ladder Completed
1. Primitive and batch benchmarks.
2. Representation policy benchmarks.
3. Profile-backed packed-affine mechanism checks.
4. Composed integration slice validating winners survive composition.

## Regression Protection In Place
1. Phase B kernel gates:
   - `scripts/bench-regression-phaseb.sh`
2. Skinning gates:
   - `scripts/bench-regression-skinning.sh`
3. Composed integration gate:
   - `scripts/bench-regression-integration.sh`

## Current Runtime Doctrine
1. Runtime transforms stay ergonomic (`Transformf`/TRS-oriented input).
2. Materialize late into packed-affine for bulk work.
3. Run skinning via `skinLbs4` baseline.
4. Update bounds and prepare uploads through packed-affine-first paths.
5. Keep matrix path available as explicit fallback/interoperability path.

## Parked Work
1. Vector skinning promotion is parked pending clear repeated wins.
2. Large benchmark-suite expansion is parked in favor of subsystem integration.
3. Matrix-default discussions are parked unless new contradictory data appears.

## Next Focus
Subsystem integration and API routing so the fast path is the natural path in production use.
