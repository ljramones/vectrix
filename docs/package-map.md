# Package map

Vectrix has 29 top-level packages under `org.dynamisengine.vectrix`. `curve` has four subpackages (`scalar`, `vec2`,
`vec3`, `vec4`), so there are 33 packages in all.

Since 4.0 they ship in four jars, each its own JPMS module:

| Jar | Module | Packages |
|---|---|---|
| `vectrix-core` | `org.dynamisengine.vectrix` | `core`, `simd`, `affine`, `animation`, `curve`, `easing`, `hash`, `ik`, `linalg`, `physics`, `sdf`, `soa`, `world`, `geometry`, `blas` |
| `vectrix-render` | `org.dynamisengine.vectrix.render` | `color`, `brdf`, `optics`, `atmosphere`, `renderingmath`, `ltc`, `sh`, `spherical`, `sampling`, `camera`, `gpu` |
| `vectrix-signal` | `org.dynamisengine.vectrix.signal` | `fft`, `dsp` |
| `vectrix-ml` | `org.dynamisengine.vectrix.ml` | `nn` |

- **Dependencies between jars:** `vectrix-render`, `vectrix-signal` and `vectrix-ml` each depend on `vectrix-core` only,
  and their modules require it transitively. `vectrix-core` depends on nothing.
- **The whole library:** the `vectrix` artifact holds no classes and depends on all four jars, so one dependency still
  brings everything.
- **Moving from 3.x:** [migrating-to-4.0.md](migrating-to-4.0.md).

The packages form layers: each depends only on packages in the layers below it, and `core` depends on nothing.
`PackageDependencyTest` keeps it that way, so the graph has no cycles. The same test checks that every package depends
only on packages in its own jar or in `vectrix-core`.

## Layers

| Layer | Package | Jar | What it holds | Depends on (besides `core`) |
|---|---|---|---|---|
| 0 | `core` | core | Vectors, matrices, quaternions in float/double/int/long; `Math`, `AccurateMath`, `ReproducibleMath`; `DepthRange`, `Complexf`/`Complexd`; configuration (`CoreConfig`, `MathMode`) | |
| 1 | `affine` | core | Affine, rigid and TRS transforms (`Transformf`, `Affine4f`, dual quaternions), packed 3×4 arrays | |
| 1 | `animation` | core | Quaternion key processing, SQUAD and spherical splines over key arrays, rotation averaging, critically damped springs | |
| 1 | `atmosphere` | render | Phase functions, scattering media, transmittance and sky lookup tables | |
| 1 | `brdf` | render | Microfacet BRDF terms, diffuse lobes, the split-sum DFG table | |
| 1 | `color` | render | Color spaces, transfer functions, CIE colorimetry, spectra | |
| 1 | `curve` | core | Bézier, Hermite, Catmull–Rom and B-spline curves (subpackages `scalar`, `vec2`, `vec3`, `vec4`), arc length | |
| 1 | `easing` | core | Easing functions | |
| 1 | `hash` | core | Hashes (`PcgHash`, `SpatialHash`) and counter-based random numbers (`Philox`) | |
| 1 | `ik` | core | Two-bone, FABRIK and CCD solvers, damped least squares, joint limits, aim | |
| 1 | `linalg` | core | Small dense decompositions and solvers: eigen, SVD, polar, QR, Cholesky, LU, Lie-group maps | |
| 1 | `optics` | render | Indices of refraction, Fresnel equations, thin-film interference | |
| 1 | `renderingmath` | render | Lookup-table builders, interpolation, visibility approximations | |
| 1 | `sdf` | core | Signed distance functions | |
| 1 | `sh` | render | Real spherical harmonics: projection, convolution, rotation, evaluation | |
| 1 | `simd` | core | The SIMD switch and Vector API detection (`SimdSupport`) | |
| 1 | `spherical` | render | Spherical Gaussians and direction mappings (octahedral, cube map) | |
| 1 | `world` | core | Large-world positions (`WorldPos`) and camera-relative conversion | |
| 2 | `blas` | core | BLAS levels 1–3 over arrays: dot products, GEMM, GEMV | `simd` |
| 2 | `fft` | signal | Planned real and complex FFTs of any size, 2D transforms, streaming convolution | `simd` |
| 2 | `ltc` | render | Linearly transformed cosines for area lights | `renderingmath` |
| 2 | `physics` | core | Mass properties, inertia tensors, constraint and integration helpers | `linalg` |
| 2 | `sampling` | render | Low-discrepancy sequences, point-set and importance sampling | `brdf` |
| 2 | `soa` | core | Structure-of-arrays containers and batch kernels (transform composition, skinning) | `affine`, `simd` |
| 3 | `camera` | render | Projections for every depth range, depth conversion, jitter, shadow cascades, cluster grids | `sampling` |
| 3 | `dsp` | signal | Windows, FIR/IIR filters, spectra, transforms, Wiener filtering, Kalman and particle filters | `fft`, `hash` |
| 3 | `geometry` | core | Intersections, closest points, contacts, bounding volumes, frustum culling | `linalg`, `simd`, `soa` |
| 3 | `nn` | ml | Neural-network kernels: activations, softmax, norms, attention, convolution, block quantization, losses, optimizers | `blas`, `hash`, `simd` |
| 4 | `gpu` | render | GPU data encodings: half floats, packed unorm/snorm and float formats, octahedral normals, quaternion compression, meshlet normal cones | `color`, `geometry` |

## Where to start

- **New to the library:** [getting-started.md](getting-started.md), then [conventions.md](conventions.md).
- **For a task:** the guides in [guides/](guides/).
