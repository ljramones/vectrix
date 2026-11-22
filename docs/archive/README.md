# Archived working notes

These are dated performance and migration memos from Vectrix's development ("Pass E/G/H", "Phase 2/B/C",
the namespace migration). They record how decisions were reached and are kept for history; they are not
reference documentation. The current references are listed in [`../README.md`](../README.md).

Caveats when reading them:

- **SIMD numbers.** Before the Batch 4b fix (review finding M-15), `SimdSupport` never detected the Vector API,
  and the benchmark forks did not add `--add-modules jdk.incubator.vector`, so every "SIMD" or "Vector API"
  result in these memos measured the scalar code.
- **Packed affine layout.** Memos that describe `PackedAffineArray` predate the row-major convention adopted for
  review finding M-1 (see `docs/conventions.md`).
