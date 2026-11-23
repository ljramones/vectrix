# Known issues

Defects and API oddities found while documenting every public method for 2.6.0. Each was found by reading the
code. None has been reproduced yet unless the Status column says so; reproduce before fixing. Fixed items move to
the CHANGELOG and are deleted here.

Status: **open**, a fix not yet started. **Documented**, the Javadoc now describes the behavior. **3.0**, the fix
changes public API and waits for the next major version.

## Non-breaking fixes

### Correctness

| Where | Issue | Status |
|---|---|---|
| `geometry/Intersectionf`, `Intersectiond` | `intersectSphereSphere` and `intersectCircleCircle` have no concentric guard: equal concentric spheres give 0/0 and return false, while `testSphereSphere` returns true | Documented |
| `geometry/Intersectionf`, `Intersectiond` | `testCircleCircle` is true when one circle contains the other; `testSphereSphere` is false in that case | Documented |
| `geometry/Contactsf.nearestFace` (and d) | For a point outside the box, returns the face the point is farthest beyond | Documented |
| `geometry/RobustPredicates` | A NaN or infinite coordinate can reach the exact path, where `new BigDecimal` throws `NumberFormatException` | Open |
| `geometry/SpatialCodes.morton2dX`/`Y` | A 32-bit code with y bit 15 set is negative and decodes wrongly unless widened with `Integer.toUnsignedLong` | Documented |
| `world/WorldPos.normalize` | Not atomic: an `ArithmeticException` on a later axis leaves earlier axes reduced without updating their cells | Open |
| `affine/DualQuatTransformf.blend` (and d) | Reads `transforms[0]` when `count` is 0; `blendScaled` gives Inf/NaN when the weights sum to 0 | Open |
| `affine/Transformf.compose` (and d) | Per-axis scale product is only an approximation for non-uniform parent scale under rotation (TRS cannot hold shear) | Documented |
| `affine/Transformf.invertRigidFast` (and d) | Ignores `scale` and returns scale 1 | Documented |
| `affine/Affine4f.getTransform` (and d) | Gives a non-unit quaternion for reflections or shear | Documented |
| `gpu/QuatCompression` | A zero or NaN quaternion encodes to 0, which decodes to (1, 0, 0, 0), a half turn, not the identity | Open |
| `gpu/OctaNormal` | A zero or NaN direction decodes to (0, 0, 1) silently | Documented |
| `gpu/GpuTransformLayout.write` | Uses the float form only for FLOAT4 + FLOAT3; any other mix gets the compact encoding regardless of the layout | Documented |
| `gpu/InstanceSubmissionPipeline` | Bounds are written per instance (`order[i]`) but uploads per slot (`i`), so instances that `order` skips keep stale bounds | Open |
| `gpu/ShaderLayout` | Two different `Struct`s with the same name are declared once, without an error | Open |
| `physics/PdControllersf` (and d) | A negative `maxTorque` reverses the torque instead of being rejected | Open |
| `physics/PbdConstraintsf` (and d) | The degeneracy thresholds are absolute, so bending stops correcting above edge lengths of about 10³ and volume below about 10⁻³ | Documented |
| `atmosphere/Atmosphere.densityIntegrals` | A negative `length` integrates the segment behind the start point | Open |
| `atmosphere/Atmosphere.transmittanceLutUv`, `transmittanceLutRMu` | Divide by zero for a table width or height of 1; table sizes are not validated | Open |
| `color/TransferFunctionsd.pqDecode` (and f) | Returns NaN for signals above about 1.99 | Documented |
| `optics/Fresnel*`, `Ior*`, `ThinFilm*` | The IOR checks use `<= 0`, so a NaN IOR passes and gives NaN | Open |
| `spherical/SphericalGaussiansd.irradianceFitted` (and f) | Sharpness 0 gives NaN | Documented |
| `brdf/DfgLutBuilder.integrate`, `integrateCloth` | No validation: a null model means Karis, `sampleCount <= 0` or roughness 0 gives NaN | Open |
| `ik/JointLimitsf.clampSwingTwist` (and d) | A limit of 0 with zero swing on that axis gives 0/0, and the clamp is skipped | Documented |
| `ik/DampedLeastSquaresf` (and d) | `lambda = 0` with a straight chain gives a NaN step | Documented |
| `camera/Projectionsf.ndcDepth` (and d) | Ignores the x and y terms, so it is exact only on the view axis of oblique projections | Documented |
| `fft/ComplexFft2d.inverse` (and f) | Divides once per axis, rounding twice, instead of once by rows·cols | Open |
| `sh/ShWindowing.weight` | A null window means LANCZOS; a negative width acts like its absolute value | Documented |
| `nn/Formats.quantizeInt8` | A NaN in a block gives a NaN scale and all-zero codes | Open |
| `nn/Sampler.argmax` | NaN handling differs from `Pool2d.max` (a leading NaN wins, other NaNs are skipped) | Open |
| `core/CoreConfig.determinismFingerprint` | Reports the `vectrix.simd.enabled` property, not the runtime `SimdSupport.setEnabled` state, and labels entries with `joml.*` names | Open |

### Validation (a bad argument fails partway, or not at all, instead of throwing up front)

| Where | Missing check | Status |
|---|---|---|
| `dsp/Spectra.periodogram`, `welch` | `psd` is zero-filled before the window and scratch are checked | Documented |
| `dsp/FirDesign` | `normalize` throws after writing taps; `kaiserBeta`/`kaiserTaps` accept any `attenuationDb`; `response` does not check `dest` | Open |
| `dsp/Biquad.response` | `dest` length not checked | Open |
| `dsp/Toeplitz` | `levinsonDurbin` does not check `reflection` and leaves partial writes on instability; `solve` leaves `x` partly written when singular | Open |
| `dsp/Wavelet` | Multilevel transforms check only `n` and `levels`; `fromLowpass` does not check orthonormality | Open |
| `dsp/Smoothers.median` | No `n >= 0` or offset checks | Open |
| `dsp/Wiener.deconvolve`, `deconvolve2d` | Offsets and `nsrPerBin` length not checked | Open |
| `dsp/UnscentedKalman` | Constructor does not check `n + kappa > 0`; a non-positive-definite `P` is reported as an innovation-covariance failure | Open |
| `blas/Gemmf.scratchSize` (and d) | Accepts negative sizes (negative `k` gives too small a size); transposed `gemv` checks scratch per row | Open |
| `nn/Attention`, `Formats.gemvInt8`, `Norms`, `Conv2d`, `Pool2d.maxBackward` | Some arrays (`stats`, `bias`, gradients, `argmax` indices) checked late or not at all | Open |
| `sh/ShProduct` | `multiplyScalarL2`/`L3` do not check lengths; `gaunt(i, j, k)` has no range check | Open |
| `sampling/AliasTable.sample` | `u` not validated | Open |
| `geometry/BoundingVolumesf.minimalAreaRectangle` (and d) | `count` not checked | Open |
| `hash/Philox.block` | Array range not checked | Open |
| `affine/PackedAffineKernels` | No index range checks and no output offset for the upload kernels | Open |
| `gpu/GpuTransformLayout`, `VertexLayout` | Constructors validate little; a null attribute throws `NullPointerException` | Open |
| `core/IntegratorRK4f` (and d) | `checkBounds` can overflow | Open |
| `gpu/PackedFormats.qTangent` | `bits` not validated; degenerate frames give NaN | Open |

### Allocation on paths documented or expected to be allocation-free

| Where | Allocation | Status |
|---|---|---|
| `geometry/SpatialCodes.hilbert3d`, `hilbert3dToXyz` | An `int[3]` per call | Documented |
| `geometry/MeshMath.vertexNormals`, `vertexTangents`, `triangleMinAngle` | Small arrays per triangle or call | Documented |
| `geometry/BoundingVolumesf.kDopDirections`, `kDop` (and d) | Direction tables per call | Documented |
| `ltc/LtcEvalf.formFactorRectClipped`, `formFactorPolygonClipped` | A `Clip` per call | Documented |
| `atmosphere/Atmosphere` | Quadrature temporaries per panel; `multipleScatteringLut` builds a 0.8 MB table per call | Documented |
| `gpu/PackedFormats.qTangent` | A `Quaterniond` and a `Matrix3d` per call | Documented |
| `gpu/InstanceSubmissionPipeline` | The `Matrix4f` array grows even when only the packed path is used | Open |
| `linalg/LinalgKernels.leastSquares` | A full rows × rows Q | Documented |

## Needs 3.0 (changes public API)

| Where | Issue |
|---|---|
| `core/Rangef.clamp(value, min, max)` | The opposite argument order to `core.Math.clamp(min, max, value)` |
| `affine/Affine4d.toMatrix4f(Matrix4d)` | Named for float, fills a `Matrix4d` |
| `affine/PackedAffineArray(int)` | Starts all-zero, not identity |
| `sh/ShCoeffs9f.get(int band, int channel)` (and d, set) | The `band` parameter is a coefficient index |
| `sh/ShCoeffs16f`, `ShCoeffs16d` | Lack the `get`/`set`/`set(other)` methods the 9-coefficient classes have |
| `sh/ShConvolution.evaluateIrradiance` | Only evaluates the coefficients it is given; the name suggests it convolves |
| `geometry/Intersectionf` (and d) | Mixed radius and squared-radius parameters across sphere and circle methods |
| `geometry/Intersectionf.testObOb`, `testMovingCircleCircle`; `physics/PbdConstraints*`; `renderingmath/Interpolationf.bilinearVec3` | Take mutable `Vector3f`/`Vector2f` where a read-only `...c` interface would do |
| `curve/scalar/UniformBSpline1f`, `1d` | Keep the caller's array; the 2D–4D versions copy it |
| `core/Matrix4dc` | Lacks `frustumAabb`, which `Matrix4fc` declares |
| `fft/RealFft2d.inverse` (and f) | Overwrites its input spectrum; the 1D inverse does not |
| `color/ColorSpacesd` vs `ColorSpacesf` | Twin classes with different allocation and table behavior (partly fixed in 2.6.0) |
| `gpu/GpuTransformWriteKernels.writePackedAffine` | Duplicates `affine.PackedAffineKernels.uploadPackedAffine` |
| `gpu/StdLayout` | std140 and std430 functions are identical for the types it supports; `ShaderLayout` is the complete one |
| `dsp/Spectra`, `Correlation`, `Wavelet`, `Wiener`, `AdaptiveFilter`, `Toeplitz` | No float overloads, unlike the other `dsp` classes |
| `hash/Philox` | Only float `uniform`/`fillNormal`; `ParticleFilter` works in double |
| The deprecated APIs listed in the CHANGELOG | Removed in 3.0 |
