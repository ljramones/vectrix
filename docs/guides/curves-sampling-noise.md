# Curves, sampling and noise

This guide covers smooth curves for animation and camera paths, moving along a curve at constant speed, easing
functions, interpolation across a triangle, sample patterns for rendering and Monte Carlo integration, hashing,
random numbers and simplex noise. The packages are `curve` (with the subpackages `curve.scalar`, `curve.vec2`,
`curve.vec3` and `curve.vec4`), `easing`, `sampling` and `hash`, plus `Random`, `SimplexNoise` and `Interpolationf`
in `core`.

All curve, easing, sequence and hash functions are static and keep no state. Results go into a `dest` object you
pass, and batch versions write into arrays you own. These rules, the `f`/`d` suffixes and the read-only `...c`
interfaces are described once in [conventions.md](../conventions.md).

## Which class for which job

| Task | Class | Notes |
|---|---|---|
| A curve through or near a few points, with derivatives | `Bezier3f`, `Hermite3f`, `CatmullRom3f`, `UniformBSpline3f` | Also 1D (`Bezier1f`), 2D (`Bezier2f`), 4D (`Bezier4f`) and `d` versions |
| The cubic basis functions on plain numbers | `CubicCurveMath` | Float and double overloads |
| Move along a curve at constant speed | `CurveReparameterizer3f`, `CurveReparameterizer3d` | Arc-length tables for vec3 curves |
| Ease-in, ease-out, bounce, spring | `Easingf`, `Easingd` | Input in `[0, 1]` |
| Remap or clamp a parameter into `[0, 1]` | `Rangef`, `Ranged` | `Rangef.inverseLerp`, `Rangef.saturate` |
| Interpolate a value across a triangle | `core.Interpolationf`, `core.Interpolationd` | Barycentric weights from 2D vertex positions |
| Well-spread sample points (TAA jitter, Monte Carlo) | `HaltonSequence`, `SobolSequence`, `R2Sequence` | Deterministic, indexed |
| Poisson-disk, stratified, uniform, spiral, best-candidate point sets | `PoissonSampling`, `StratifiedSampling`, `UniformSampling`, `SpiralSampling`, `BestCandidateSampling` | Seeded; results through a callback or array |
| Directions with a known density (cosine hemisphere, cone, sphere) | `Warpf`, `Warpd` | With the matching `pdf...` functions |
| GGX microfacet importance sampling | `GgxSamplingf`, `GgxSamplingd` | Visible-normal sampling |
| Sample a spherical triangle or rectangle light | `SphericalSamplingf`, `SphericalSamplingd` | Uniform in solid angle |
| Pick from a discrete distribution in constant time | `AliasTable` | Build once, sample many |
| Hash an integer or a 3D grid cell | `PcgHash`, `SpatialHash` | 32-bit results |
| Reproducible random numbers in any order, from any thread | `hash.Philox` | Counter-based, no state |
| A conventional seeded generator | `core.Random` | One instance per thread |
| Smooth noise in 2D, 3D or 4D | `SimplexNoise` | Values in `[-1, 1]` |

## Cubic curves

All four curve families are cubic polynomials in a parameter `t` from 0 to 1. They differ in what you specify:

| Family | You give | Passes through | Typical use |
|---|---|---|---|
| Bézier (`Bezier3f`) | Four control points `p0..p3` | `p0` at `t = 0` and `p3` at `t = 1`; `p1`, `p2` are handles that pull the curve | Authored paths with handles |
| Hermite (`Hermite3f`) | Two end points `p0`, `p1` and their tangents `m0`, `m1` | Both end points | Animation curves: the tangents are the velocities at the ends |
| Catmull-Rom (`CatmullRom3f`) | Four points `p0..p3` and a tension | `p1` at `t = 0` and `p2` at `t = 1` | A smooth path through a list of points |
| Uniform B-spline (`UniformBSpline3f`) | An array of control points | None of them, in general | Smooth camera and procedural paths; second derivative continuous across segments |

Each family exists for scalars (`curve.scalar`: `Bezier1f`, `Hermite1f`, `CatmullRom1f`, `UniformBSpline1f`), 2D
points (`curve.vec2`: `Bezier2f` and so on), 3D points (`curve.vec3`) and 4D values (`curve.vec4`), each with a
double twin ending in `d`. The vector versions run the same scalar formula on each component.

Every family has the same four operations:
- `evaluate`: the point at `t`.
- `derivative`: the first derivative with respect to `t` (the velocity if `t` is time scaled to `[0, 1]`).
- `secondDerivative`: the second derivative (the acceleration).
- `evaluateBatch`: many `t` values at once, from a `tValues` array (with an offset and a count) into one output
  array per component (`outX`, `outY`, `outZ`, each with its own offset). Use it to bake a path into a buffer.

Bézier, Hermite and Catmull-Rom are static: `Bezier3f.evaluate` takes the four points, `t` and `dest`. They describe one
segment. For a path of many segments, choose the segment and its local `t` yourself, then call the function with that
segment's points.

**Catmull-Rom tension.** `CatmullRom3f.evaluate` takes `p0..p3`, then `t`, then `tension`, then `dest` (in
`CatmullRom3f.evaluateBatch` the tension comes before the `t` array). The tangent at `p1` is
`(1 − tension) · (p2 − p0) / 2`, and likewise at `p2`. Tension 0 gives the standard uniform Catmull-Rom spline.
Tension 1 gives zero tangents, so the curve stops at every point. `CubicCurveMath.cardinalTangent` computes this
tangent on its own.

**Uniform B-spline.** `UniformBSpline3f` is an object built from an array of at least four control points. A curve
with `n` control points has `UniformBSpline3f.segmentCount` = `n − 3` segments. Methods take a segment index from 0
to `segmentCount() − 1` and a local `t`; an index out of range throws `IllegalArgumentException`. Neighbouring segments
meet with matching position, first and second derivative. The 2D, 3D and 4D classes copy the control points when you
construct them, so later changes to your array do not affect the curve. The scalar `UniformBSpline1f` and
`UniformBSpline1d` keep a reference to your array: changing the array changes the curve.

`CubicCurveMath` holds the scalar formulas the classes use: `CubicCurveMath.bezier`, `CubicCurveMath.hermite`,
`CubicCurveMath.bspline` and their `...Derivative` and `...SecondDerivative` forms. Use them on your own data
layouts, such as one channel of an animation curve stored in an array.

For rotations, do not run a curve on quaternion components. Use the quaternion splines in the `animation` package,
such as `QuaternionCurvesf`.

## Constant speed along a curve

Equal steps in `t` do not give equal steps in distance: a curve moves faster where its control points are far apart.
To move at a constant speed, map a fraction of the curve's length `s` (0 at the start, 1 at the end) to the `t` that
reaches it. `CurveReparameterizer3f` and `CurveReparameterizer3d` do this with a table, for one segment of a vec3
curve. There are three steps.

**1. Build a table** once per segment, when the curve changes:

| Curve | Method |
|---|---|
| Bézier segment | `CurveReparameterizer3f.buildArcLengthTableForBezier` (`p0..p3`, `samples`, `dest`) |
| Hermite segment | `CurveReparameterizer3f.buildArcLengthTableForHermite` (`p0, m0, p1, m1`, `samples`, `dest`) |
| Catmull-Rom segment | `CurveReparameterizer3f.buildArcLengthTableForCatmullRom` (`p0..p3`, `tension`, `samples`, `dest`) |
| One segment of a `UniformBSpline3f` | `CurveReparameterizer3f.buildArcLengthTable` (`curve`, `segmentIndex`, `samples`, `dest`) |

**The table length rule.** A table built with `samples` = N needs a `float[]` of at least N + 1 entries. The builder
evaluates the curve at the N + 1 parameters `t = i / N` for `i = 0..N` and writes the normalized cumulative chord
length at each: entry 0 is 0 and entry N is 1. A shorter array throws `IllegalArgumentException`, and so does N <= 0.
For the default `CurveConstants.DEFAULT_ARCLEN_SAMPLES` (64), allocate 65 floats.

**2. Map `s` to `t`** each frame with `CurveReparameterizer3f.mapArcLengthToT`. The two-argument form
`mapArcLengthToT(s, table)` assumes the table was built with N = `table.length − 1`. If your array is longer than
N + 1 (a reused or oversized buffer), pass N explicitly with the three-argument form `mapArcLengthToT(s, table, N)`;
otherwise the lookup reads entries the builder never wrote. Values of `s` below 0 or above 1 clamp; NaN gives NaN.
The lookup is a binary search plus a linear interpolation.

**3. Evaluate** the curve at the returned `t` with the family's `evaluate`. For a `UniformBSpline3f`,
`CurveReparameterizer3f.evaluateByArcLength` maps and evaluates in one call. It uses the two-argument mapping, so its
table must be exactly N + 1 long.

Things to watch:
- The table is a polyline approximation: the length between samples is the straight chord. More samples give a closer
  match to the true arc length; tight bends need more.
- Each table is normalized to its own segment. It does not record the segment's absolute length. To move at constant
  speed across several segments, measure each segment's length yourself (for example by summing chords) and divide
  the global distance among the segments accordingly.
- A segment of zero length gets the uniform table `i / N`.
- Building a table allocates two temporary vectors. Mapping and evaluating allocate nothing.

## Easing

An easing function reshapes a normalized time `t` in `[0, 1]` so motion starts or ends gently. Each function maps 0
to 0 and 1 to 1. Blend with the result: `a + (b − a) · ease(t)`.

| Method | Shape |
|---|---|
| `Easingf.linear` | `t` |
| `Easingf.smoothStep` | `3t² − 2t³`: slow at both ends |
| `Easingf.easeInQuad`, `Easingf.easeOutQuad`, `Easingf.easeInOutQuad` | Quadratic: slow start, slow end, or both |
| `Easingf.easeInCubic`, `Easingf.easeOutCubic`, `Easingf.easeInOutCubic` | Cubic: the same, more pronounced |
| `Easingf.bounceOut` | Bounces to rest at 1 |
| `Easingf.spring` | A damped oscillation, `1 − e^(−damping·t) · cos(frequency·t)` corrected to end at 1: overshoots and settles |

The functions do not clamp. Inputs outside `[0, 1]` extrapolate the polynomial, so clamp first with
`Rangef.saturate`. To turn a time range into `t`, use `Rangef.inverseLerp(start, end, time)` (it throws when
`start == end`). `Rangef.remap` and `Rangef.remapClamped` map from one range to another in one call.

`Easingf.spring` takes `frequency` in radians per unit of `t` and `damping` per unit of `t`. It reaches exactly 1 at
`t = 1`: a linear term spreads the plain spring's remainder at `t = 1` over the curve, which changes it by less than
0.007 for damping of 5 or more. For a physically based spring that runs on real time steps, use
`SpringDamperf.dampedStep` (see [geometry-and-physics.md](geometry-and-physics.md)).

## Interpolating across a triangle

`core.Interpolationf` and `core.Interpolationd` interpolate values given at the three corners of a triangle, using
barycentric weights computed from the corners' 2D positions. This is how a rasterizer interpolates vertex attributes
in screen space.
- `core.Interpolationf.interpolateTriangle` interpolates a scalar, a 2D vector or a 3D vector at a point `(x, y)`.
- `core.Interpolationf.interpolationFactorsTriangle` returns the three weights `(t0, t1, t2)` so you can apply them
  to any data.
- `core.Interpolationf.dFdxLinear` and `core.Interpolationf.dFdyLinear` return the gradient of a 2D attribute with
  respect to x and y, as `dFdx` and `dFdy` do in a shader.

A degenerate (zero-area) triangle gives NaN. For grid interpolation (bilinear and bicubic), see
`renderingmath.Interpolationf`.

## Low-discrepancy sequences

Random points clump and leave gaps. A low-discrepancy sequence places points so that every prefix covers the domain
evenly. That makes Monte Carlo estimates converge faster and gives stable sub-pixel jitter. All three generators below
are pure functions of an integer index: the same index always gives the same point, and you can generate points in any
order. Float results are rounded down where needed so they stay below 1.

**Halton.** `HaltonSequence.halton` and `HaltonSequence.haltonDouble` return the radical inverse of an index in a
prime base (index >= 0, base >= 2). `HaltonSequence.halton2D` uses bases 2 and 3, `HaltonSequence.halton3D` bases 2,
3 and 5, and `HaltonSequence.haltonBatch2D` fills x and y arrays for a range of indices. Index 0 is the point
`(0, 0)`, so start at 1 when you need interior points. For TAA camera jitter, `Projectionsf.haltonJitter` does the
whole job (see [camera-and-projection.md](camera-and-projection.md)).

**Sobol.** `SobolSequence` supports dimensions 0 to 3; another dimension throws `IllegalArgumentException`.
`SobolSequence.sobol`, `SobolSequence.sobol2D`, `SobolSequence.sobol3D` and `SobolSequence.sobolBatch2D` give the
plain sequence. Two scrambled variants take a `seed`, so each seed gives a different but equally well-spread set:

| Method | Scrambling | Use |
|---|---|---|
| `SobolSequence.sobolScrambled`, `SobolSequence.sobolScrambled2D`, `SobolSequence.sobolScrambledBatch2D` | XOR with a per-dimension value from the seed | Cheap decorrelation |
| `SobolSequence.sobolOwen`, `SobolSequence.sobolOwenDouble`, `SobolSequence.sobolOwen2D` | Owen scrambling with index shuffling (Burley 2020) | Rendering. Keeps the stratification of dimensions 0 and 1 and removes the structured artefacts of plain Sobol |

**R2.** `R2Sequence.r2` gives the 2D R2 sequence (Roberts 2018) for a `long` index, computed in double. It needs no
tables and has no limit on the index. It is a good pattern for dithering and per-pixel offsets.

## Point sets

These generators produce a fixed set of points from a seed. The same seed gives the same set. They report points
through a callback, `Callback2d.onNewSample` with `(x, y)` or `Callback3d.onNewSample` with `(x, y, z)`; the
best-candidate generators can also fill a float array or `FloatBuffer`.

| Generator | Domain | How to call |
|---|---|---|
| `PoissonSampling.Disk` | Disk of `diskRadius`, no two points closer than `minDist` | The constructor generates: seed, radius, minimum distance, `k` (candidates per point; 20 to 30 is typical), callback |
| `StratifiedSampling` | `n × n` points in `[-1, 1]²`, one per grid cell | Construct with a seed, then `StratifiedSampling.generateRandom`, or `StratifiedSampling.generateCentered` to keep each point near its cell center |
| `UniformSampling.Disk`, `UniformSampling.Sphere` | Unit disk; unit sphere surface | The constructor generates: seed, count, callback |
| `SpiralSampling` | Spiral of a maximum radius | Construct with a seed, then `SpiralSampling.createEquiAngle` with radius, rotations, count and an optional jitter. Points concentrate toward the center |
| `BestCandidateSampling.Sphere`, `BestCandidateSampling.Disk`, `BestCandidateSampling.Quad`, `BestCandidateSampling.Cube` | Unit sphere (or hemisphere around +Z), unit disk, `[-1, 1]²`, unit cube | Configure with `seed`, `numSamples`, `numCandidates` (and `onHemisphere` for the sphere), then `generate` |

Best-candidate sampling tries several random candidates per point and keeps the one farthest from the existing
points. This gives a blue-noise-like set; more candidates give a more even set. It allocates, so generate sets once
and store them.

## Importance sampling

These turn two uniform numbers `u1, u2` in `[0, 1)` (from a sequence above) into a direction with a known
probability density. Directions are in a local frame with the surface normal on `+z`. Densities are per unit solid
angle (per unit area for the disk).
- `Warpf` maps the unit square to the disk (`Warpf.squareToUniformDiskConcentric`), the cosine-weighted hemisphere
  (`Warpf.squareToCosineHemisphere`), the uniform hemisphere and sphere, and a cone of directions
  (`Warpf.squareToUniformCone`). Each has a `pdf...` function, for example `Warpf.pdfCosineHemisphere`.
  `Warpf.balanceHeuristic` and `Warpf.powerHeuristic` weight samples when you combine two sampling strategies.
- `GgxSamplingf.sampleVisibleNormal` samples a GGX microfacet normal visible from the view direction `(vx, vy, vz)`
  (unit, `vz > 0`), for widths `alphaX`, `alphaY` (equal for isotropic GGX). Reflect the view direction about the
  returned half vector `h` to get the light direction: `2 (v·h) h − v`. `GgxSamplingf.pdfReflected` gives that
  direction's density, `GgxSamplingf.pdfVisibleNormal` the density of `h`, and
  `GgxSamplingf.pdfVisibleNormalAnisotropic` the anisotropic case.
- `SphericalSamplingf.sampleTriangle` and `SphericalSamplingf.sampleRectangle` give directions uniformly distributed
  over the solid angle of a triangle or rectangle light. `SphericalSamplingf.solidAngleTriangle` and
  `SphericalSamplingf.solidAngleRectangle` give that solid angle, whose reciprocal is the density.
- `AliasTable` draws index `i` with probability `weights[i] / sum(weights)` in constant time, for example to pick
  environment-map pixels by luminance. Construct it with the number of outcomes, call `AliasTable.build` with the
  weights (non-negative, finite, not all zero), then `AliasTable.sample` with one uniform number. `AliasTable.pdf`
  gives the probability of an outcome. Building reuses the table's arrays; sampling does not allocate.

## Hashing and counter-based random numbers

A hash turns an integer into a well-mixed integer. Use it when you need a random-looking value tied to a position or
an ID, the same every time, with no generator state.
- `PcgHash.hash32` hashes one `int`, or three ints (for a cell or a pixel and frame). To get a float in `[0, 1)`, keep
  the top 24 bits: `(h >>> 8) * 0x1p-24f`.
- `SpatialHash.hashCell3i` hashes integer grid cell coordinates for a spatial hash table. Different cells can share a
  hash, so store the cell coordinates in the bucket and compare them. Reduce the hash to your table size yourself
  (mask the low bits for a power-of-two table).

`hash.Philox` is the Philox4x32-10 generator of Random123. Number `n` of stream `seed` is a pure function of
`(seed, n)`: there is no state, so any thread can draw any element in any order and get the same value. That makes a
Monte Carlo sample, a noise mask or a weight initialization reproducible however the work is split.

| Method | Returns |
|---|---|
| `hash.Philox.word` | 32-bit word `index` of stream `seed` |
| `hash.Philox.uniform` | A float in `[0, 1)` with 24 random bits |
| `hash.Philox.fillUniform` | `dest[off + i] = uniform(seed, start + i)` for `n` elements |
| `hash.Philox.fillNormal` | Normally distributed floats with a mean and standard deviation (Box–Muller, computed in double with `ReproducibleMath`) |
| `hash.Philox.block` | The raw four-word block for a 128-bit counter and 64-bit key |

Outputs match the Random123 known-answer vectors, and nothing allocates. Give each independent stream its own seed,
and use the index for the position within the stream.

## Seeded generator and noise in core

`core.Random` is a small xoroshiro128+ generator. `new Random(seed)` gives a reproducible sequence;
`Random.nextFloat` returns a float in `[0, 1)` and `Random.nextInt` an int in `[0, n)`. The no-argument constructor
seeds from the clock, so its sequence differs on every run. An instance has mutable state and is not thread-safe: give
each thread its own, or use `hash.Philox` when the work is split across threads.

`SimplexNoise.noise` is Stefan Gustavson's simplex noise in 2D, 3D and 4D, taking `float` coordinates and returning a
value in `[-1, 1]`. The noise has a fixed permutation table and no seed: offset the input coordinates to get a
different pattern. The feature size is about one unit, so scale the coordinates to set the frequency. For fractal
noise, sum several calls at doubling frequency and halving amplitude. Use the 4D version to animate 3D noise over time.

## Accuracy, allocation and threading

- The curve, easing, `CubicCurveMath`, sequence, warp, GGX, hash and `hash.Philox` functions are static, keep no state, do
  not allocate, and are safe from any number of threads.
- The arc-length table builders allocate two temporary vectors per table. Map and evaluate in the frame loop; build
  tables when the curve changes.
- The point-set generators (`PoissonSampling`, `StratifiedSampling`, `UniformSampling`, `SpiralSampling`,
  `BestCandidateSampling`) allocate and hold a `core.Random`. Use them offline or at load time, one instance per thread.
- `AliasTable` and `core.Random` instances have mutable state: one thread at a time. After `AliasTable.build`,
  concurrent `AliasTable.sample` calls only read.
- `HaltonSequence`, `SobolSequence` and `R2Sequence` compute in double; the float versions round the double result.
- `hash.Philox.fillNormal` uses `ReproducibleMath`, so its output is the same on every platform. For the general rules on
  reproducible results, see [conventions.md](../conventions.md) and [determinism.md](../determinism.md).

## See also

- [conventions.md](../conventions.md): type suffixes, `dest` parameters, array offsets, threading, errors.
- [geometry-and-physics.md](geometry-and-physics.md): spatial codes, springs and the physics kernels.
- [camera-and-projection.md](camera-and-projection.md): TAA jitter with `Projectionsf.haltonJitter`.
- Source: [curve](../../src/main/java/org/dynamisengine/vectrix/curve/),
  [easing](../../src/main/java/org/dynamisengine/vectrix/easing/),
  [sampling](../../src/main/java/org/dynamisengine/vectrix/sampling/),
  [hash](../../src/main/java/org/dynamisengine/vectrix/hash/),
  [core](../../src/main/java/org/dynamisengine/vectrix/core/) (`Random`, `SimplexNoise`, `Interpolationf`).
