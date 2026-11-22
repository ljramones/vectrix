# Determinism

Vectrix is used for simulation and replay, where two runs must produce the same bits. This page lists every
setting that can change Vectrix's output bits, and how to configure the library so results are reproducible
across machines.

## Recording the configuration

`CoreConfig.determinismFingerprint()` returns one line naming every bit-affecting setting, for example:

```
vectrix.math.mode=FAST;vectrix.strictMath=true;joml.fastmath=false;joml.sinLookup=false/14;joml.useMathFma=true;
vectrix.simd.enabled=true;vectrix.skinning.simd=false;vectrix.culling.simd=false;os.arch=aarch64;java.vm.version=25.0.4+...
```

Store it in replay files and compare it on playback. Two runs can only be expected to agree bitwise when their
fingerprints are equal. The `os.arch` and `java.vm.version` entries only matter while `vectrix.strictMath` is
false.

## Settings that change output bits

| Setting | Default | Effect |
|---|---|---|
| `-Dvectrix.strictMath=true` | false | All transcendental functions (`sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2`, `exp`, `log`, `pow`) use `StrictMath` (fdlibm), which gives the same bits on every platform. Overrides `joml.fastmath`. |
| `-Djoml.fastmath=true` | false | `Math.sin`/`cos`/`atan2` use polynomial or lookup-table approximations. The lookup table is built with the platform's `sin`. Ignored when `vectrix.strictMath` is set. |
| `-Djoml.sinLookup`, `-Djoml.sinLookup.bits` | false, 14 | Which `fastmath` approximation is used, and the table size. |
| `-Djoml.useMathFma` | true | Whether `Math.fma(a, b, c)` is fused (`java.lang.Math.fma`) or `a * b + c`. Both are exactly specified, so each setting is reproducible, but the two settings differ in the last bit. Every type, including `Affine4f`, follows this flag. |
| `vectrix.math.mode` / `CoreConfig.setMathMode` | FAST | `STRICT` forces scalar kernels and accurate fixed-order reductions; see `MathMode` and `docs/conventions.md`. |
| `-Dvectrix.simd.enabled` | true | Whether batch kernels may take their Vector API path. Vector API kernels perform the scalar operations in the same order, so FAST compose is bitwise identical either way. |
| `-Dvectrix.skinning.simd`, `-Dvectrix.culling.simd` | false | Opt-in experimental SIMD dispatch. Skinning has no SIMD path (the gather kernel was slower); culling classifies identically except for NaN bounds. |

Settings that do **not** change output bits:
- The memory backend (FFM by default; `-Djoml.noffm`, `-Djoml.forceUnsafe`, `-Djoml.nounsafe`): every backend writes the same bytes.
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
which is why it is opt-in.

## Not yet covered

- `CoreConfig`'s mode is process-wide, not per context or per call (Part II §13 of the review).
- There is no cross-architecture golden-hash test in CI yet.
