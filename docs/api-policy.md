# API Policy

## Stability Levels

- Public API: classes and methods not marked with `@Internal` are intended for external use.
- Internal API: any symbol marked with `@org.vectrix.core.Internal` is implementation detail and may change or be removed between releases.

## Compatibility Guarantees

- Public API aims for source compatibility across minor releases.
- Behavioral or signature changes in public API require release notes and migration guidance.
- Internal API has no compatibility guarantee.

## Package Guidance

- Primary stable packages:
  - `org.vectrix.core`, `org.vectrix.affine`, `org.vectrix.geometry`, `org.vectrix.gpu`, `org.vectrix.soa`
  - `org.vectrix.curve`, `org.vectrix.sh`, `org.vectrix.fft`, `org.vectrix.optics`
  - `org.vectrix.renderingmath`, `org.vectrix.sampling`, `org.vectrix.color`, `org.vectrix.easing`, `org.vectrix.sdf`, `org.vectrix.hash`, `org.vectrix.ltc`
- Experimental/perf-control surfaces (`org.vectrix.experimental`, backend probing in `org.vectrix.simd`) are opt-in and may evolve more aggressively unless explicitly documented as stable.

## Performance Contract

- Any change to hot-path public APIs must include benchmark evidence.
- CI smoke benchmark gate protects against major regressions.
- Nightly long-run benchmarks are used for drift detection and baseline refresh decisions.
