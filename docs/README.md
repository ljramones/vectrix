# Docs Index

This folder contains the current project documentation for Vectrix.

## Canonical Docs
- `API.md` (repo root)
  Practical API reference with up-to-date usage examples by module.
- `docs/vectrix-capabilities.md`
  Consumer-oriented capability inventory for downstream libraries.
- `docs/conventions.md`
  Math and engine-facing conventions (handedness, layout, mutability model), the packed 3x4 affine layout and
  the STRICT mode contract.
- `docs/determinism.md`
  Every setting that changes output bits, `StrictMath` mode and the replay fingerprint.
- `docs/joml-divergence.md`
  Why Vectrix forked JOML, the defects the 2.4.0 audit found in the inherited code, and behaviour differences when
  porting from JOML.
- `docs/api-policy.md`
  Public/internal API stability and compatibility expectations.
- `docs/transform-representation-policy.md`
  Runtime/default transform form policy (TRS, packed affine, boundary matrix usage).
- `docs/skinning-runtime-policy.md`
  Default versus quality skinning runtime policy and measurement priorities.
- `docs/hot-path-policy.md`
  Enforceable rules for batch-first, locality-first hot code.
- `docs/performance-showcase.md`
  One-page evidence summary of runtime defaults, key benchmark wins, and regression discipline.
- `docs/why-vectrix-is-fast.md`
  Concise technical explainer of the mechanisms behind current Vectrix performance.
- `docs/engine-runtime-defaults.md`
  Current default runtime doctrine (bulk transforms, upload, skinning, experimental paths).
- `docs/performance-state-of-the-union.md`
  Snapshot of settled defaults, experimental areas, active gates, and parked work.
- `docs/benchmarking-guide.md`
  How to build, run, profile, normalize, and compare benchmark runs.
- `docs/benchmark-environment.md`
  Recorded benchmark environment and run discipline requirements.
- `docs/benchmark-modulepath-resolution.md`
  Named-module/JMH benchmark build failure diagnosis and resolved approach.
- `docs/release-cut-checklist.md`
  Release process and validation commands.
- `docs/technology-explainer.md`
  Educational guide for non-expert users (what the tech is and when to use it).

## Supporting Docs
- `docs/v1-readiness-checklist.md`
  Final V1 readiness status snapshot (kept for release tracking history).
- `docs/rendering-roadmap.md`
  Historical roadmap record; implementation has already surpassed this plan.

## Benchmark and Release Docs (repo root)
- `BENCHMARKS.md`
  Benchmark methodology plus latest baseline and normalized per-op results.
- `CHANGELOG.md`
  Release change history.

## Archived Working Notes
Dated memos kept for history; see `docs/archive/README.md` for caveats (their SIMD numbers measured scalar code).
- `docs/archive/performance-strategy.md`
  Performance charter, benchmark tier taxonomy, and naming conventions.
- `docs/archive/performance-phase2-findings.md`
  Initial Phase 2 benchmark coverage and early representation observations.
- `docs/archive/performance-phase2-decision-memo.md`
  Constrained full-run observations and architecture recommendations.
- `docs/archive/performance-phaseB-findings.md`
  Pass B locality + packed-affine implementation findings and recommendations.
- `docs/archive/performance-phaseB-decision-memo.md`
  Pass B architecture defaults, keep/discard table, and Pass C targeting.
- `docs/archive/performance-phaseB-packed-affine-postmortem.md`
  Short profiler-backed explanation of why packed-affine kernels won.
- `docs/archive/performance-phaseC-palette-locality-memo.md`
  Initial Pass C skinning locality results and vector-path status.
- `docs/archive/performance-phaseC-findings.md`
  Initial Pass C skinning implementation outcomes and decisions.
- `docs/archive/performance-passE-summary.md`
  Consolidated Pass E hotspot resolution summary and promoted defaults.
- `docs/archive/benchmark-dashboard-summary.md`
  Compact scorecard of architecture-defining kernel deltas and active regression gates.
- `docs/archive/performance-integration-slice-findings.md`
  End-to-end composed-path validation of transform/skinning/upload winners.
- `docs/archive/subsystem-integration-memo.md`
  Transform-update + instance-submission subsystem replacement memo (packed default vs matrix fallback).
- `docs/archive/perfasm-notes.md`
  Assembly-level inspection workflow for hot JMH kernels (`-prof perfasm`) and interpretation guardrails.
- `docs/archive/transform-materialization-audit.md`
  Initial classification of transform materialization APIs by boundary/hot-path intent.
- `docs/archive/skinning-work-equivalence-audit.md`
  Legacy-vs-kernel skinning workload equivalence analysis.
