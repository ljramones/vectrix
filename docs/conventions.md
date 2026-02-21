# Conventions

This project is rendering-first and defaults to these conventions unless an API explicitly states otherwise:

- Coordinate system: right-handed.
- Matrix storage: column-major.
- Transform composition: post-multiplication style for chained transforms.
- Quaternion convention: `(x, y, z, w)` with `w` as scalar term.
- Clip-space: API-dependent; keep projection building explicit at call sites.

## Numeric Policy

- Use `MathMode.FAST` for throughput-sensitive rendering paths.
- Use `MathMode.STRICT` for reproducibility-sensitive workflows (capture/replay/tests).
- Use `Epsilonf`/`Epsilond` for tolerant comparisons instead of ad-hoc literals.

## Threading Policy

- Mutable vectors/matrices/quaternions/transforms are not thread-safe.
- Read-only interfaces (`*c`) are safe for concurrent reads.
- Batch kernels are pure with respect to provided inputs/outputs; call-site synchronization remains the caller responsibility.

## GPU Layout Policy

- Validate vertex stream layouts via `VertexLayout`.
- Use `StdLayout` for std140/std430 offset and size calculations.
- Keep packed formats explicit (`Half`, `PackedNorm`, `OctaNormal`, `QuatCompression`) and benchmark all conversion hot paths.
