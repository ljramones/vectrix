# Pass E GPU Layout Decision

Date: 2026-03-06

## Scope
Focused optimization and validation for runtime transform layout writes:
- Specialized write kernels added in `GpuTransformWriteKernels`.
- Specialized `GpuTransformLayout` write methods (`writeFloatTrs`, `writeCompactTrs`).
- Focus benchmark:
  - `GpuTransformLayoutBenchmark.writeTransformLayoutPath`
  - params: `count=16384`, `accessPattern=RANDOM`, `sourceRep=packedAffine`

Artifacts:
- `benchmarks/results/2026-03-06/passE-gpu-layout.{json,txt,normalized.csv}`
- `benchmarks/results/2026-03-06/passE-prof-gpu-layout.{json,txt}`

## Results
Normalized throughput (`ns/item`):
- `layout=packedAffine`: `3.192 ns/item`
- `layout=matrix4f`: `3.461 ns/item`
- `layout=instanceCompact`: `17.568 ns/item`

Relative:
- packedAffine is ~7.8% faster than matrix4f in this shape.
- packedAffine is ~5.5x faster than instanceCompact.

Allocation signal (`gc.alloc.rate.norm`):
- packedAffine: `27.998 B/op`
- matrix4f: `29.827 B/op`
- instanceCompact: `140.656 B/op`

Compact path remains a large outlier in both throughput and allocation due packing/conversion work.

## Decision
Default:
- packed-affine runtime write path for transform upload staging.

Allowed:
- matrix4f write path for compatibility/interoperability boundaries.

Discouraged:
- compact TRS write path as default runtime-prep path (keep as niche format path only).

Regression gate target:
- Keep `GpuTransformLayoutBenchmark.writeTransformLayoutPath` in focused regression checks with:
  - `count=16384`
  - `accessPattern=RANDOM`
  - `sourceRep=packedAffine`
  - compare `layout=packedAffine` against `layout=matrix4f` and `layout=instanceCompact`.

## Notes
- Compact path is retained, but not promoted to default until it closes both throughput and allocation gap materially.
