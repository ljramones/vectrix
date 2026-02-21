# V1 Readiness Checklist

## API and Semantics
- [x] Stable domain packages (`core`, `affine`, `geometry`, `gpu`, `simd`, `soa`, `experimental`)
- [x] Explicit conventions doc (`docs/conventions.md`)
- [ ] Mark internal-only APIs and lock public compatibility policy

## Numeric and Determinism
- [x] Global epsilon policy (`Epsilonf`, `Epsilond`)
- [x] FAST/STRICT runtime mode (`KernelConfig` + `MathMode`)
- [x] Deterministic-aware reduction utility (`Reduction`)

## Mesh and Geometry Pipeline
- [x] Tangent/bitangent helpers (`GeometryUtils`)
- [x] Mesh helpers: barycentric/closest/winding (`MeshMath`)
- [ ] Tangent-space generation parity with DCC-standard MikkTSpace
- [ ] BVH/Morton helpers for large mesh acceleration

## GPU Interop
- [x] Packed types (`Half`, `PackedNorm`, `OctaNormal`, `QuatCompression`)
- [x] Transform instance layouts (`GpuTransformLayout`)
- [x] Vertex stream validation (`VertexLayout`)
- [x] std140/std430 offset helpers (`StdLayout`)

## Testing and Benchmarking
- [x] Unit tests for new math and layout primitives
- [x] JMH microbenchmarks for transform/culling/packing/skinning and new mesh/layout helpers
- [x] CI smoke benchmark run + optional baseline regression gate
- [ ] Long-run benchmark profile for nightly performance drift tracking

## Operational
- [ ] Publish versioning policy and migration notes
- [ ] Add artifact/module split plan for external consumers
- [ ] Add architecture decision records for SIMD/FFM dispatch strategy
