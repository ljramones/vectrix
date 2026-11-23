# Package map

Vectrix is one module, `org.dynamisengine.vectrix`, with 30 exported packages under `org.dynamisengine.vectrix`.
The packages form layers: each depends only on packages in the layers below it, and `core` depends on nothing.
`PackageDependencyTest` keeps it that way, so the graph has no cycles.

## Layers

| Layer | Package | What it holds | Depends on (besides `core`) |
|---|---|---|---|
| 0 | `core` | Vectors, matrices, quaternions in float/double/int/long; `Math`, `AccurateMath`, `ReproducibleMath`; `DepthRange`, `Complexf`/`Complexd`; configuration (`CoreConfig`, `MathMode`) | |
| 1 | `affine` | Affine, rigid and TRS transforms (`Transformf`, `Affine4f`, dual quaternions), packed 3×4 arrays | |
| 1 | `animation` | Quaternion key processing, SQUAD and spherical splines over key arrays, rotation averaging, critically damped springs | |
| 1 | `atmosphere` | Phase functions, scattering media, transmittance and sky lookup tables | |
| 1 | `brdf` | Microfacet BRDF terms, diffuse lobes, the split-sum DFG table | |
| 1 | `color` | Color spaces, transfer functions, CIE colorimetry, spectra | |
| 1 | `curve` | Bézier, Hermite, Catmull–Rom and B-spline curves (subpackages `scalar`, `vec2`, `vec3`, `vec4`), arc length | |
| 1 | `easing` | Easing functions | |
| 1 | `hash` | Hashes (`PcgHash`, `SpatialHash`) and counter-based random numbers (`Philox`) | |
| 1 | `ik` | Two-bone, FABRIK and CCD solvers, damped least squares, joint limits, aim | |
| 1 | `linalg` | Small dense decompositions and solvers: eigen, SVD, polar, QR, Cholesky, LU, Lie-group maps | |
| 1 | `optics` | Indices of refraction, Fresnel equations, thin-film interference | |
| 1 | `renderingmath` | Lookup-table builders, interpolation, visibility approximations | |
| 1 | `sdf` | Signed distance functions | |
| 1 | `sh` | Real spherical harmonics: projection, convolution, rotation, evaluation | |
| 1 | `simd` | The SIMD switch and Vector API detection (`SimdSupport`) | |
| 1 | `spherical` | Spherical Gaussians and direction mappings (octahedral, cube map) | |
| 1 | `world` | Large-world positions (`WorldPos`) and camera-relative conversion | |
| 2 | `blas` | BLAS levels 1–3 over arrays: dot products, GEMM, GEMV | `simd` |
| 2 | `fft` | Planned real and complex FFTs of any size, 2D transforms, streaming convolution | `simd` |
| 2 | `ltc` | Linearly transformed cosines for area lights | `renderingmath` |
| 2 | `physics` | Mass properties, inertia tensors, constraint and integration helpers | `linalg` |
| 2 | `sampling` | Low-discrepancy sequences, point-set and importance sampling | `brdf` |
| 2 | `soa` | Structure-of-arrays containers and batch kernels (transform composition, skinning) | `affine`, `simd` |
| 3 | `camera` | Projections for every depth range, depth conversion, jitter, shadow cascades, cluster grids | `sampling` |
| 3 | `dsp` | Windows, FIR/IIR filters, spectra, transforms, Wiener filtering, Kalman and particle filters | `fft` |
| 3 | `nn` | Neural-network kernels: activations, softmax, norms, attention, convolution, losses, optimizers | `blas`, `hash`, `simd` |
| 4 | `geometry` | Intersections, closest points, contacts, bounding volumes, frustum culling | `camera`, `linalg`, `simd`, `soa` |
| 5 | `gpu` | GPU data layouts and encodings: std140/std430, half floats, packed normals, instance data | `affine`, `color`, `geometry`, `soa` |

## Deprecated edges

Three edges remain only through deprecated overloads kept for compatibility. All three go in 3.0:
- `core` → `camera`: `Matrix4f`/`Matrix4d` methods taking `camera.DepthRange`, deprecated in favor of
  `core.DepthRange`.
- `affine` → `soa`: the deprecated `affine.TransformKernels`, now `soa.TransformKernels`.
- `optics` → `fft`: the deprecated `Fresnelf.conductor` overloads taking `fft.Complexf`, which are now
  `core.Complexf`.

## Where to start

- **New to the library:** [getting-started.md](getting-started.md), then [conventions.md](conventions.md).
- **For a task:** the guides in [guides/](guides/).
