# Determinism

Vectrix is used for simulation and replay, where two runs must produce the same bits. This page lists every
setting that can change Vectrix's output bits, and how to configure the library so results are reproducible
across machines.

## Recording the configuration

`CoreConfig.determinismFingerprint()` returns one line naming every bit-affecting setting, for example:

```
vectrix.math.mode=FAST;vectrix.strictMath=true;vectrix.fastmath=false;vectrix.sinLookup=false/14;
vectrix.useMathFma=true;os.arch=aarch64;java.vm.version=25.0.4+...
```

Store it in replay files and compare it on playback. Two runs can only be expected to agree bitwise when their
fingerprints are equal. The `os.arch` and `java.vm.version` entries only matter while `vectrix.strictMath` is
false.

## Settings that change output bits

| Setting | Default | Effect |
|---|---|---|
| `-Dvectrix.strictMath=true` | false | All transcendental functions (`sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2`, `exp`, `log`, `pow`) use `StrictMath` (fdlibm), which gives the same bits on every platform. Overrides `joml.fastmath`. |
| `-Djoml.fastmath=true` | false | `Math.sin`/`cos`/`atan2` use polynomial or lookup-table approximations. The lookup table is built with the platform's `sin`. Ignored when `vectrix.strictMath` is set. |
| `-Djoml.sinLookup`, `-Djoml.sinLookup.bits` | false, 14 | Which `fastmath` approximation is used, and the table size. Error bounds against the exact value: the polynomial `sin` within 1e-10 and `cos` within 2e-10 for \|x\| < 1e6; the lookup `sin`/`cos` within 4e-5 for \|x\| < 256 (default table); `atan2` within 2.1e-4 rad. Larger arguments, zeros, infinities and NaN use the accurate path (since 2.5.0). |
| `-Djoml.useMathFma` | true | Whether `Math.fma(a, b, c)` is fused (`java.lang.Math.fma`) or `a * b + c`. Both are exactly specified, so each setting is reproducible, but the two settings differ in the last bit. Every type, including `Affine4f`, follows this flag. |
| `vectrix.math.mode` / `CoreConfig.setMathMode` | FAST | `STRICT` validates inputs and forces scalar kernels; see `MathMode` and [conventions.md](conventions.md). This is the process-wide default; `CoreConfig.runWithMathMode` / `callWithMathMode` override it for one task on one thread (see below). |

Settings that do **not** change output bits:
- The SIMD switches (`-Dvectrix.simd.enabled`, `SimdSupport.setEnabled`, `-Dvectrix.culling.simd`): every Vector API kernel performs the scalar operations in the same order, so FAST results are the same bits either way.
- The memory backend (FFM by default; `-Djoml.noffm`, `-Djoml.forceUnsafe`, `-Djoml.nounsafe`): every backend writes the same bytes, including the raw-address API.
- The JIT: results are the same in the interpreter (`-Xint`) and in compiled code.
- `-Djoml.debug`.

## Why `java.lang.Math` is not reproducible

`java.lang.Math.sin`, `cos` and friends may use CPU-specific intrinsics, and the JDK only promises results within
1 ulp. On arm64 about 7 % of double `sin`/`cos` inputs differ from `StrictMath` in the last bit (measured:
165 and 141 of 2000 random inputs). `sqrt` and `fma` are exactly specified by IEEE 754 and are already
reproducible.

`AccurateMath` is the single place where Vectrix chooses between `java.lang.Math` and `StrictMath`. Every
transcendental call in the library goes through it or through `core.Math`, and a test fails if new code calls
`java.lang.Math` or `StrictMath` transcendentals directly.

## Recommended configuration for lockstep simulation and replay

```
-Dvectrix.strictMath=true -Dvectrix.math.mode=STRICT -Djoml.useMathFma=true
```

Record `CoreConfig.determinismFingerprint()` alongside the replay. `StrictMath` gives up the CPU intrinsics,
which is why it is opt-in. Where speed matters, `ReproducibleMath` is reproducible without that cost.

## Per-task math mode

`CoreConfig.setMathMode` changes the mode for the whole process. To run one simulation step in STRICT mode while
other threads (rendering, tools) keep FAST, scope it to the task:

```text
CoreConfig.runWithMathMode(MathMode.STRICT, () -> simulation.step());
State s = CoreConfig.callWithMathMode(MathMode.STRICT, () -> simulation.stepAndSnapshot());
```

([conventions.md](conventions.md#fast-and-strict) has a complete, tested example.)

The override is a `ScopedValue` binding: it applies to the calling thread and to subtasks forked with
`StructuredTaskScope`, not to other threads or to executors the task submits to. Overrides nest and end when the
task returns or throws. `CoreConfig.mathMode()` reports the mode in effect for the calling thread, and
`determinismFingerprint()` includes it; `defaultMathMode()` reports the process-wide default. While no override is
active anywhere, `mathMode()` costs one extra volatile read.

## Cross-platform test

`CrossPlatformDeterminismTest` calls every public method of the library (about 8 200, three seeded input sets each)
and the batch kernels (in FAST and STRICT mode, at sizes that exercise the SIMD loops and their tails) in a child
JVM under `-Dvectrix.strictMath=true`, and compares a hash of each result with
`src/test/resources/determinism/golden.txt`. CI runs it on x86_64 Linux, arm64 Linux and arm64 macOS, in the default
and the experimental (Vector API) profile, so a result that depends on the CPU, the vector width or the profile
fails the build.

NaN results are compared as one value: the bit pattern of a generated NaN differs between x86 and ARM, and Java
allows that. Everything else, including the sign of zero, must match exactly. Without `vectrix.strictMath`, about
130 of the 8 200 methods give different bits on the same machine, mostly double-precision rotations.

Methods added after the golden was recorded are reported, not failed. After an intended change to results,
re-record the file:

```
mvn test -Dtest=CrossPlatformDeterminismTest -Dvectrix.determinism.write=$PWD/src/test/resources/determinism/golden.txt
```

## Reproducible math and kernels

`ReproducibleMath` does not use `java.lang.Math` or `StrictMath` transcendentals at all. Its float and double
functions (exp, log, sin, cos, pow, tanh, erf, the normal CDF and density) are built from series with exact
rational coefficients, exact argument reductions and fused multiply-adds. The Vector API kernels of `nn` perform the
same operations. The functions are therefore reproducible everywhere, independently of `-Dvectrix.strictMath`, and
the golden hashes them on every CI platform.

The `blas` reductions and matrix products have one documented order (see `Blasf`). Together these make `blas` and
`nn` bit-identical across x86-64 and arm64, both jars and any batch size, in any configuration.

The FFTs and `dsp` kernels also sum in a fixed order, so their results do not depend on the jar. `Biquad` and
`Resampler` give the same bits however a stream is chunked; `BlockConvolverd` does not, because the tail carried
between blocks is added separately. Their twiddle factors, windows and filter designs go through `AccurateMath`, so they are identical
across platforms when `vectrix.strictMath` is set.
