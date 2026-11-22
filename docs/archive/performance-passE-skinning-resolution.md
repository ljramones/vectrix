# Pass E Skinning Resolution

Date: 2026-03-06

## Scope
Resolve skinning equivalence gap by adding a stripped matrix-palette kernel and validating against existing paths.

Implemented:
- `SkinningKernels.skinLbs4MatrixPalette(...)` (12-float matrix palette kernel).
- `SkinningEquivalenceBenchmark` new path: `kernelMatrixTight`.
- `SkinningKernelBenchmark` new method: `skinLbs4MatrixPaletteTight`.

Artifacts:
- `benchmarks/results/2026-03-06/passE-skinning-equivalence.{json,txt,normalized.csv}`
- `benchmarks/results/2026-03-06/passE-prof-skinning-equivalence.{json,txt}`

## Results
Focused params:
- `vertices=16384`, `paletteSize=512`, `writeMode=fullWrite`

Normalized throughput (`ns/item`):
- `legacyLbs`: `6.652 ns/item`
- `kernelLbs`: `9.817 ns/item`
- `kernelMatrixTight`: `6.082 ns/item`

Relative:
- `kernelMatrixTight` is ~8.6% faster than `legacyLbs`.
- `kernelMatrixTight` is ~38.0% faster than `kernelLbs`.

Allocation signal (`gc.alloc.rate.norm`):
- `legacyLbs`: `57.672 B/op`
- `kernelLbs`: `85.169 B/op`
- `kernelMatrixTight`: `53.970 B/op`

`kernelMatrixTight` is now both the fastest and lowest-allocation path in this equivalence shape.

## Decision
Default:
- matrix-palette tight kernel shape (`skinLbs4MatrixPalette`) for CPU LBS baseline in perf-critical paths.

Allowed:
- legacy matrix loop path for parity/debug comparison.

Experimental:
- quaternion-based kernel path (`kernelLbs`/`skinLbs4`) remains functional but is not baseline for this benchmark shape.

Regression gate target:
- Add skinning regression checks including:
  - `SkinningEquivalenceBenchmark.skinningEquivalent`
  - `vertices=16384`, `paletteSize=512`, `writeMode=fullWrite`
  - `path=legacyLbs,kernelMatrixTight` (must retain `kernelMatrixTight <= legacyLbs`)
  - keep `kernelLbs` as tracked non-default comparator.

## Notes
- This resolves the prior “legacy faster than kernel” ambiguity for the tested shape.
- Further work should validate matrix-palette kernel behavior under additional palette locality patterns and integration-level flows.
