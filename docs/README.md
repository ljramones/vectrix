# Docs Index

This folder contains the current project documentation for Vectrix.

## Canonical Docs
- `API.md` (repo root)
  Practical API reference with up-to-date usage examples by module.
- `docs/vectrix-capabilities.md`
  Consumer-oriented capability inventory for downstream libraries.
- `docs/conventions.md`
  Math and engine-facing conventions (handedness, layout, mutability model).
- `docs/api-policy.md`
  Public/internal API stability and compatibility expectations.
- `docs/performance-strategy.md`
  Performance charter, benchmark tier taxonomy, and naming conventions.
- `docs/transform-representation-policy.md`
  Runtime/default transform form policy (TRS, packed affine, boundary matrix usage).
- `docs/skinning-runtime-policy.md`
  Default versus quality skinning runtime policy and measurement priorities.
- `docs/hot-path-policy.md`
  Enforceable rules for batch-first, locality-first hot code.
- `docs/performance-phase2-findings.md`
  Initial Phase 2 benchmark coverage and early representation observations.
- `docs/performance-phase2-decision-memo.md`
  Constrained full-run observations and architecture recommendations.
- `docs/performance-phaseB-findings.md`
  Pass B locality + packed-affine implementation findings and recommendations.
- `docs/performance-phaseB-decision-memo.md`
  Pass B architecture defaults, keep/discard table, and Pass C targeting.
- `docs/performance-phaseB-packed-affine-postmortem.md`
  Short profiler-backed explanation of why packed-affine kernels won.
- `docs/performance-phaseC-palette-locality-memo.md`
  Initial Pass C skinning locality results and vector-path status.
- `docs/performance-phaseC-findings.md`
  Initial Pass C skinning implementation outcomes and decisions.
- `docs/performance-integration-slice-findings.md`
  End-to-end composed-path validation of transform/skinning/upload winners.
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
- `docs/transform-materialization-audit.md`
  Initial classification of transform materialization APIs by boundary/hot-path intent.
- `docs/v1-readiness-checklist.md`
  Final V1 readiness status snapshot (kept for release tracking history).
- `docs/rendering-roadmap.md`
  Historical roadmap record; implementation has already surpassed this plan.
- `docs/skinning-work-equivalence-audit.md`
  Legacy-vs-kernel skinning workload equivalence analysis.

## Benchmark and Release Docs (repo root)
- `BENCHMARKS.md`
  Benchmark methodology plus latest baseline and normalized per-op results.
- `CHANGELOG.md`
  Release change history.
