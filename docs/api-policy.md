# API Policy

## Stability Levels

- Public API: classes and methods not marked with `@Internal` are intended for external use.
- Internal API: any symbol marked with `@org.dynamisengine.vectrix.core.Internal` is implementation detail and may change or be removed between releases.

## Compatibility Guarantees

- Public API aims for source compatibility across minor releases.
- Behavioral or signature changes in public API require release notes and migration guidance.
- Internal API has no compatibility guarantee.

## Package Guidance

- Primary stable packages:
  - `org.dynamisengine.vectrix.core`, `org.dynamisengine.vectrix.affine`, `org.dynamisengine.vectrix.geometry`, `org.dynamisengine.vectrix.gpu`, `org.dynamisengine.vectrix.soa`
  - `org.dynamisengine.vectrix.curve`, `org.dynamisengine.vectrix.sh`, `org.dynamisengine.vectrix.fft`, `org.dynamisengine.vectrix.optics`
  - `org.dynamisengine.vectrix.renderingmath`, `org.dynamisengine.vectrix.sampling`, `org.dynamisengine.vectrix.color`, `org.dynamisengine.vectrix.easing`, `org.dynamisengine.vectrix.sdf`, `org.dynamisengine.vectrix.hash`, `org.dynamisengine.vectrix.ltc`
- Experimental/perf-control surfaces (`org.dynamisengine.vectrix.experimental`, backend probing in `org.dynamisengine.vectrix.simd`) are opt-in and may evolve more aggressively unless explicitly documented as stable.

## Performance Contract

- Any change to hot-path public APIs must include benchmark evidence.
- CI smoke benchmark gate protects against major regressions.
- Nightly long-run benchmarks are used for drift detection and baseline refresh decisions.
