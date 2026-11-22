# Pass H LUT Findings (2026-03-06)

## Scope
Pass H LUT work targeted:
- `SssLutBuilder.build(...)`
- `TransmittanceLutBuilder.build(...)`
- `RenderingMathBenchmark` LUT methods

Implemented changes:
1. `SssLutBuilder`: precompute per-sample diffusion kernels once per build and hoist per-axis invariants.
2. `TransmittanceLutBuilder`: inline optical-depth integration in the main loop, remove temp-buffer usage in hot path, and hoist atmosphere parameters to local scalars.

## How It Was Measured
- Build: `mvn -q -Pbench -DskipTests package`
- Constrained full LUT sweep:
  - `benchmarks/results/2026-03-06/passH-rendering-lut.{json,txt,normalized.csv}`
  - methods: `buildSssLut`, `buildTransmittanceLut`
  - params: `count=1024`, `precision=float`, `mode=arrayBatch`, `distribution=uniform`, `accessPattern=sequential`, `resolution=64,128,256`, `quality=medium,high`
- Profiled representative sweep:
  - `benchmarks/results/2026-03-06/passH-prof-rendering-lut.{json,txt,normalized.csv}`
  - params: `resolution=256`, `quality=high`

## Key Results

### 1) SSS LUT build improved materially on comparable shape
Comparable baseline (`resolution=64`, `quality=medium`):
- Pass G: `2515.40 ns/item`
- Pass H: `549.42 ns/item`
- Improvement: `~4.58x`

Main reason:
- previous code evaluated the diffusion profile for each pixel/sample pair.
- new code evaluates diffusion per sample once, then reuses kernel weights across all pixels.

### 2) Transmittance LUT stayed roughly flat on comparable shape
Comparable baseline (`resolution=64`, `quality=medium`):
- Pass G: `2706.63 ns/item`
- Pass H: `2641.26 ns/item`
- Improvement: `~1.02x` (effectively neutral)

Interpretation:
- current transmittance cost remains dominated by transcendental math (`sqrt`/`exp`) in integration.
- loop-structure cleanup improved code clarity and locality but did not deliver a large throughput jump.

### 3) Heavy-shape costs are now clearly characterized
Pass H (`resolution=256`, `quality=high`):
- `buildSssLut`: `15597.31 ns/item`
- `buildTransmittanceLut`: `80684.43 ns/item`

This identifies transmittance as the heavier builder for high-quality/high-resolution settings.

## Profile Signal
Representative profiled shape (`resolution=256`, `quality=high`):
- `buildSssLut:gc.alloc.rate.norm`: `10214.667 B/op`
- `buildTransmittanceLut:gc.alloc.rate.norm`: `43124.923 B/op`

Note:
- these large `B/op` figures occur with very low ops/sec and include profiler/JMH overhead effects.
- they are useful as comparative signal, not strict allocation accounting for these heavy kernels.

## Answers To Pass H LUT Questions

### Lower build time?
Yes for SSS (clear win), not materially for transmittance.

### Lower allocation?
No clear actionable improvement signal from profiled `B/op`; throughput and stack profiles were more informative for this pass.

### Better separation of invariant vs per-cell work?
Yes. SSS now explicitly separates invariant kernel precomputation from per-cell accumulation.

### Parallelization worth it?
Not decided in this pass. Current data says transmittance is the stronger candidate if a parallel path is explored next.

## Policy Impact
1. Keep current SSS builder changes as the default path.
2. Keep transmittance builder optimized for scalar clarity and locality; treat further wins as a dedicated follow-up.
3. If we run a Pass H.1 for LUTs, prioritize transmittance parallel/chunked build experiments.
