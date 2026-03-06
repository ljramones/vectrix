# Engine Runtime Defaults

Date: 2026-03-06

## Default Runtime Choices
1. Bulk transform materialization: packed affine (`3x4`, 12-float).
2. Bulk bounds/update kernels: packed-affine-first paths.
3. Instance upload prep: packed-affine upload path.
4. CPU skinning baseline: matrix-palette tight LBS (`SkinningKernels.skinLbs4MatrixPalette`) with `buildRigidMatrixPalette12(...)`.

## Boundary-Only / Interop Choices
1. `Matrix4f` remains valid for API boundaries and interoperability.
2. Full matrix bulk path is not the default hot-path representation.

## Locality Rules
1. Locality-aware traversal/scheduling is first-order for large batches.
2. Random/scattered access remains a known throughput penalty.

## Experimental Paths
1. `SkinningKernels.skinLbs4Vector` remains experimental until it beats `skinLbs4MatrixPalette` in repeated constrained/full runs.
2. SoA SIMD skinning variants remain experimental.
3. Dual quaternion skinning remains quality mode, not default performance mode.

## Backing Evidence
1. Packed-affine transform/upload wins in kernel and composed integration slices.
2. Integration slice confirms no hidden conversion overhead erased packed-affine gains.
3. Pass E equivalence runs promote `kernelMatrixTight` shape (`skinLbs4MatrixPalette`) as skinning baseline.

## Policy Status
These defaults are current engine law until a new benchmark pass provides stronger contradictory evidence.

## Reference Subsystem
Current reference implementation of this doctrine:
- `org.vectrix.gpu.InstanceSubmissionPipeline`
