# API Policy

## Stability Levels

- Public API: classes and methods not marked with `@Internal` are intended for external use.
- Internal API: any symbol marked with `@org.vectrix.core.Internal` is implementation detail and may change or be removed between releases.

## Compatibility Guarantees

- Public API aims for source compatibility across minor releases.
- Behavioral or signature changes in public API require release notes and migration guidance.
- Internal API has no compatibility guarantee.

## Package Guidance

- Primary stable packages: `org.vectrix.core`, `org.vectrix.affine`, `org.vectrix.geometry`, `org.vectrix.gpu`, `org.vectrix.soa`.
- Experimental/perf-control surfaces (`org.vectrix.experimental`, backend probing in `org.vectrix.simd`) should be treated as unstable unless explicitly documented otherwise.

## Performance Contract

- Any change to hot-path public APIs must include benchmark evidence.
- CI smoke benchmark gate protects against major regressions.
- Nightly long-run benchmarks are used for drift detection and baseline refresh decisions.
