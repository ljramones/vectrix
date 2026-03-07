# Perfasm Notes

Date: 2026-03-06

## Purpose
Capture practical guidance for assembly-level inspection of hot JMH kernels.

This is not a primary benchmark mode. Use it after normal full/prof runs identify a kernel worth deeper investigation.

## When To Use
Use perfasm notes for questions like:
1. Did the JIT inline the hot loop?
2. Is the kernel shape showing expected load/store behavior?
3. Did a “faster” variant regress due to hidden instruction overhead?

## Recommended Workflow
1. Build benchmark jar:
   - `mvn -q clean package -Pbench -DskipTests`
2. Run the narrowest benchmark regex possible.
3. Keep parameters fixed to one representative shape (`count`, `vertices`, `palette`, etc.).
4. Compare generated output between baseline and candidate kernels.

## JMH Perfasm Invocation
Example:

```bash
java --add-modules jdk.incubator.vector -jar target/benchmarks.jar \
  "org.dynamisengine.vectrix.bench.SkinningKernelBenchmark.skinLbs4MatrixPaletteTight" \
  -p vertices=16384 -p paletteAccess=random -p paletteSize=64 \
  -f 1 -wi 3 -i 5 -prof perfasm
```

For transform/upload kernels:

```bash
java --add-modules jdk.incubator.vector -jar target/benchmarks.jar \
  "org.dynamisengine.vectrix.bench.GpuTransformLayoutBenchmark.writeTransformLayoutPath" \
  -p count=16384 -p accessPattern=RANDOM -p sourceRep=packedAffine \
  -f 1 -wi 3 -i 5 -prof perfasm
```

## Practical Constraints
1. `perfasm` support is platform/toolchain dependent.
2. On some macOS setups, `perfasm` may be unavailable or incomplete.
3. If unavailable, keep using:
   - `-prof stack`
   - `-prof gc`
   - throughput/alloc deltas from normalized JSON outputs
4. Do not block decisions on perfasm if kernel/integration evidence is already clear.

## What To Look For
1. Tight loop body with minimal call-outs.
2. Inlined arithmetic for the hot kernel path.
3. No accidental object-heavy slow path in the measured method.
4. Relative instruction/memory access shape consistent with observed `ns/item`.

## Current Golden Targets
Use perfasm checks primarily on:
1. Packed-affine transform/upload kernels.
2. `skinLbs4MatrixPalette` and close competitors.
3. Composed integration path kernels when regressions appear.

## Policy
Perfasm evidence is supporting evidence, not the release gate by itself.

Primary decision chain remains:
1. benchmark methodology discipline (`BENCHMARKS.md`)
2. normalized result deltas (`ns/item`, `items/sec`)
3. allocation signal (`gc.alloc.rate.norm`)
4. composed-path regression gates
