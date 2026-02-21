# Vectrix Rendering Roadmap

## Mission
Build Vectrix into a rendering-first math kernel with strict correctness, explicit data layout, and measurable performance gains on CPU and GPU upload paths.

## Non-Negotiables
- Every feature ships with unit tests + JMH benchmarks.
- No merge if `mvn test` fails.
- No merge of hot-path changes without baseline comparison.
- APIs document coordinate/clip-space conventions.

## M1: Contract + Core Currency
- Add global math/space policy doc:
  - handedness, multiplication order, clip-space Z, matrix layout.
- Introduce `Affine4f` (3x4) as first-class transform currency.
- Add conversions:
  - `Transformf <-> Affine4f`
  - `Affine4f <-> Matrix4f` (only when needed).
- Benchmarks:
  - `Affine4f.mul`, `invertRigid`, `transformPoint` vs current paths.
- DoD:
  - parity tests against current `Matrix4x3f` behavior.

## M2: SoA + Culling Kernels
- Add `AABBSoA`, `SphereSoA`, and packed frustum plane representation.
- Implement batch kernels:
  - `frustumCullAabbBatch`
  - `frustumCullSphereBatch`
  - scalar + SIMD-dispatch path.
- Add branch-stable “conservative” variant for culling.
- DoD:
  - randomized correctness tests and benchmark wins at 1k/10k/100k objects.

## M3: Skinning + GPU Layouts
- Extend skinning kernels:
  - SoA 4-weight fast path, DQ blend policies (sign-fix, renorm).
- Add explicit GPU packing/layout descriptors:
  - half, snorm/unorm, octa normal, quaternion compression.
- Add upload-ready SoA views/strides.
- DoD:
  - benchmark coverage for LBS vs DQ, packed vs unpacked throughput.

## M4: Determinism + Regression Gating
- Apply `MathMode.FAST/STRICT` consistently across transform, culling, skinning.
- Add JMH baseline JSON and regression checker script (tolerance bands).
- CI:
  - already runs smoke; add threshold-based pass/fail stage.
- DoD:
  - deterministic replay tests pass on repeated runs.

## Immediate Next 3 Tasks
1. Implement `Affine4f` + tests + JMH microbench.
2. Add `AABBSoA` + scalar `frustumCullAabbBatch`.
3. Add baseline-compare script for benchmark regression gates.
