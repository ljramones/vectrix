# Transform Materialization Audit

Date: 2026-03-05

## Scope
Initial audit of transform-producing APIs in hot packages (`affine`, `soa`, `gpu`, `geometry`) to classify matrix materialization behavior.

Classification:
- `Boundary-required`: needed for interoperability/boundary contracts.
- `Switch-now`: can use packed affine/TRS directly for hot paths.
- `Defer`: keep current form for now; revisit when dependent kernels migrate.

## Inventory

| API / Path | Current Behavior | Classification | Notes |
|---|---|---|---|
| `Transformf.toAffineMat4Fast(Matrix4x3f)` | TRS -> affine matrix | `Boundary-required` | Good boundary form; already avoids full `Matrix4f`. |
| `Transformf.toAffine4fFast(Affine4f)` | TRS -> packed affine | `Switch-now` | Preferred bulk materialization target. |
| `RigidTransformf.toAffineMat4Fast(Matrix4x3f)` | rigid TR -> affine matrix | `Boundary-required` | Acceptable when `Matrix4x3f` is explicitly needed. |
| `Affine4f.toMatrix4x3f(Matrix4x3f)` | packed affine -> affine matrix | `Boundary-required` | Keep for compatibility adapters. |
| `Affine4f.toMatrix4f(Matrix4f)` | packed affine -> full matrix | `Defer` | Keep as boundary helper; avoid in hot loops. |
| `GpuTransformLayout.write(..., Transformf)` | writes TRS payload (float/compact) directly | `Switch-now` | Hot staging path should continue direct write without full-matrix materialization. |
| `TransformKernels.composeBatch(...)` | TRS/SoA batch compose | `Switch-now` | Already aligned with policy; no full matrix required. |
| `CullingKernels.frustumCullAabbBatch(...)` | AABB SoA + frustum planes | `Switch-now` | Locality is main bottleneck; no matrix materialization in kernel body. |
| `SkinningKernels.skinLbs4* / skinDualQuat4*` | TRS SoA / dual quat SoA skinning | `Switch-now` | Keep data-oriented inputs; avoid matrix expansion unless required by external consumers. |

## Immediate Follow-Ups
1. Add call-site guidance so `Affine4f.toMatrix4f` is documented as boundary-only usage.
2. Route new upload and bounds kernels through packed affine inputs by default.
3. Re-run this audit after Phase 3A kernel updates.
