# Phase C Findings (Initial)

Date: 2026-03-06  
Scope: focused skinning pass (`skinLbs4Vector`, palette locality variants, work-equivalence audit).

## Completed
1. Added vectorized packed-input LBS kernel:
   - `SkinningKernels.skinLbs4Vector(...)`
2. Extended `SkinningKernelBenchmark` with palette-locality dimensions:
   - `paletteAccess=contiguous|clustered|random`
   - `paletteSize=32|64|256`
3. Added parity test:
   - `SkinningKernelsTest.lbsPackedScalarAndVectorMatch`
4. Added audit doc:
   - `docs/skinning-work-equivalence-audit.md`
5. Added skinning regression gate script:
   - `scripts/bench-regression-skinning.sh`

## Measured Outcome (Constrained Quick Pass)
Source: `benchmarks/results/2026-03-06/phaseC-skinning-kernel.normalized.csv`

- `skinLbs4` remains fastest in tested slices:
  - roughly `~9.7-9.9 ns/item`
- `skinLbs4Vector` is currently slower:
  - roughly `~10.0-10.13 ns/item`
- forced SoA SIMD path is also slower than packed scalar baseline in this pass.
- palette locality effects are present but modest for this workload shape.

## Decisions
1. Keep `skinLbs4` as default fast path.
2. Keep `skinLbs4Vector` experimental until it beats baseline in constrained full runs.
3. Continue Pass C with deeper palette/locality stress and stripped work-equivalence harnessing before policy expansion.

## Known Constraint
- Targeted JUnit invocation currently fails without explicit Vector API module availability in Surefire runtime (`jdk.incubator.vector`); JMH bench runs remain valid with benchmark JVM args.
