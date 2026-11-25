# Migrating to 3.0

3.0 removes everything deprecated in 2.6.0, and fixes in place every known issue that needed an API change.
Most changes are compile errors with an obvious fix. The changes in the first section are not: they keep their
signatures and change their results, so search your code for them.

## Changes the compiler will not catch

| API | Before | Now | What to do |
|---|---|---|---|
| `Intersectionf`/`Intersectiond` sphere and circle methods: `intersectSphereSphere`, `testSphereSphere`, `testAabSphere`, `intersectRaySphere`, `testRaySphere`, `testLineSegmentSphere`, `intersectCircleCircle`, `testCircleCircle`, `testAarCircle`, `intersectRayCircle`, `testRayCircle`, `testPointCircle`, `testCircleTriangle` | Most took the squared radius | Every one takes the plain radius | Pass `r` where you passed `r * r` |
| `Intersectionf.testSphereSphere` (and d) | False when one sphere contains the other | True: containment counts as intersecting | Use `intersectSphereSphere` if you need the surfaces to meet |
| `Rangef.clamp`, `Ranged.clamp` | `(value, min, max)` | `(min, max, value)`, like `core.Math.clamp` | Move the value to the last argument |
| `SkinningKernels.skinLbs4`, `SkinningKernels.skinLbs4SoA` | Joint scale ignored | Joint scale applied (T·R·S); unit scale gives the same bits | Nothing, unless you relied on scale being ignored |
| `new PackedAffineArray(n)` | All zeros | n identity transforms | Remove code that set the identity after construction |
| `Contactsf.nearestFace` (and d) | Outside the box: minus the distance to the face's plane | Minus the distance to the box | Nothing for contact generation |
| `InstanceSubmissionPipeline.processFrame` (now in DynamisGPU's `dynamis-gpu-vectrix`) | Bounds written at `order[i]` | Bounds written at slot `i`, like the uploads | Read bounds by slot |
| `Easingf.spring` (and d) | Ended slightly off 1 | Ends exactly at 1 | Nothing |
| `Sampler.argmax` | A NaN won only in the first position | The first NaN wins anywhere, like `Pool2d.max` | Nothing |
| `DirectionMappingsf.hemiOctahedralEncode` (and d) | Meaningless for z < 0 | Folds z < 0 onto the upper hemisphere | Nothing |
| `TransferFunctionsd.pqDecode` (and f) | NaN above about 1.99 | Clamps the signal to [0, 1] | Nothing |
| `SphericalGaussiansf.irradianceFitted` (and d) | Wrong, even negative, for sharpness below 1 | Exact below sharpness 1 | Nothing |
| `ComplexFft2d.inverse` (and f), `UnscentedKalman.update` | Two roundings; textbook covariance update | One rounding; Joseph-form covariance | Last-bit differences only |

## Removed

| Removed | Use |
|---|---|
| `nn.NnMath` | `core.ReproducibleMath`. The double functions are overloads: `expD` is `exp(double)`, and so on |
| `nn.Philox` | `hash.Philox`, and `nn.Activations.dropout` |
| `fft.FFT1f`, `fft.FFT1d`, `fft.Convolutionf`, `fft.Convolutiond` | `ComplexFftf`/`ComplexFftd`, `RealFftf`/`RealFftd`, `BlockConvolverf`/`BlockConvolverd` |
| `fft.Complexf`, `fft.Complexd` | `core.Complexf`, `core.Complexd` |
| `camera.DepthRange`, and every overload taking it | `core.DepthRange` |
| `affine.TransformKernels` | `soa.TransformKernels` |
| `core.QuaternionfInterpolator`, `core.QuaterniondInterpolator` | `QuaternionCurvesf.weightedMean` (and d) |
| `renderingmath.AtmosphereParams`, `renderingmath.TransmittanceLutBuilder` | The `atmosphere` package |
| `simd.Vector4fa` | `Vector4f` |
| `gpu.StdLayout` | DynamisGPU's `org.dynamisengine.gpu.api.layout.ShaderLayout` (the 3.0 replacement, gpu.ShaderLayout, moved there in 3.3 and was removed in 4.0) |
| `GpuTransformWriteKernels.writePackedAffine` | `PackedAffineKernels.uploadPackedAffine` |
| `GpuTransformLayout.writeFloatTrs`, `writeCompactTrs`, and the same in `GpuTransformWriteKernels` | DynamisGPU's `org.dynamisengine.gpu.vectrix.GpuTransformLayout` and `org.dynamisengine.gpu.vectrix.GpuTransformWriteKernels` (moved there in 3.3; removed from Vectrix in 4.0) |
| The `*Scalar`/`*Simd`/`*Vector` variants in `CullingKernels` and `SkinningKernels`, and `CullingKernels.frustumCullAabbBatch(Matrix4fc, ...)` | The plain entry points, with planes from `FrustumPlanes.set(m, normalize, depthRange)`. `SimdSupport.setEnabled` forces scalar |
| `ShCoeffs9f.BANDS` and the other `ShCoeffs*.BANDS` | `COEFFS` |
| `SpringDamperf.overdamped`, `suspensionForce` (and d) | `SpringDamperf.criticallyDamped` or your own formula |
| The `vectrix.skinning.simd` property | Nothing: there is no SIMD skinning kernel |
| Public constructors of the static utility classes | Call the static methods. The classes are now `final` |

## Renamed or re-signed

| Before | Now |
|---|---|
| `Affine4d.toMatrix4f(Matrix4d)` | `Affine4d.toMatrix4d(Matrix4d)` |
| `ShConvolution.evaluateIrradiance(coeffs, nx, ny, nz, scratch, rgb)` | `ShConvolution.evaluateL2(coeffs, nx, ny, nz, scratch, rgb)` |
| `SphericalGaussiansf.fit(dirs, weights, count, dest)` (and d) | `fit(dirs, dirsOffset, weights, weightsOffset, count, dest)`; pass 0 for both offsets |
| `Atmosphere.multipleScatteringLut(m, w, h, dirs, steps, out)` | Adds `scratch, scratchOffset` before `out`; size it with `Atmosphere.multipleScatteringLutScratchSize()` |
| `MeshMath.vertexTangents(...)` | Takes `double[] scratch, int scratchOffset`; size it with `MeshMath.vertexTangentsScratchSize(vertexCount)` |
| `CurveReparameterizer3f.buildArcLengthTableForBezier` and the other table builders (and 3d) | Return the segment's arc length instead of `dest`; use the `dest` you passed |
| `Toeplitz.levinsonDurbin` | Needs `Toeplitz.levinsonDurbinScratchSize(p)` doubles of scratch (was p + 1) |
| `GpuTransformWriteKernels.writeMatrix4fFromPackedAffine` | Takes an `outOff` before `count`; pass 0 (now in DynamisGPU's `dynamis-gpu-vectrix`) |
| `Intersectionf.testObOb`, `testMovingCircleCircle`, `PbdConstraintsf`, `Interpolationf.bilinearVec3` (and d) | Take read-only `Vector3fc`/`Vector2fc` parameters; recompile |

## Stricter validation

Many methods now check their arguments up front and throw `IllegalArgumentException` before writing anything.
Before, they failed partway with `ArrayIndexOutOfBoundsException`, or silently produced NaN. Code that passes
valid arguments is unaffected. The CHANGELOG lists each case. The ones most likely to surface:
- `Transformf.invertRigidFast` (and d) throws for a non-unit scale. Invert scaled transforms as matrices.
- `DualQuatTransformf.blend` (and d) throws for no transforms or all-zero weights.
- `PoseKernelsf` (and d) need both scale arrays or neither.
- `UnscentedKalman` rejects `n + kappa <= 0`.
- `Wavelet.fromLowpass` rejects taps that are not orthonormal.
- `FirDesign.kaiserBeta` and `FirDesign.kaiserTaps` reject an attenuation that is not positive and finite.
- `Formats.quantizeInt8` rejects NaN and infinite input.
- `AtmosphereModel` rejects non-finite fields.
