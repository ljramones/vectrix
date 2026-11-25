# State estimation

This guide shows how to estimate a hidden state, such as the position and velocity of a tracked object, from noisy
measurements. It covers the linear Kalman filter (`dsp.Kalman`), the extended and unscented Kalman filters for
nonlinear models (`dsp.ExtendedKalman`, `dsp.UnscentedKalman`, with model functions written as `dsp.VectorFunction`),
the particle filter building blocks (`dsp.ParticleFilter`), and the lightweight trackers in `dsp.Smoothers`.

Everything here is in the `dsp` package and works in `double`, except `Smoothers.median`, which filters floats.
The filter state (estimate and covariance) is always in your arrays, so one model can drive any number of tracked
objects.

## Which class for which job

| Task | Class | Notes |
|---|---|---|
| Linear motion and measurement models, Gaussian noise | `Kalman` | Static `predict` and `update` over your arrays; the optimal estimator for this case |
| Mildly nonlinear models, Jacobians available | `ExtendedKalman` | Linearizes your model functions at the current estimate |
| Clearly nonlinear models, or Jacobians hard to write | `UnscentedKalman` | No Jacobians; `2n + 1` model evaluations per step |
| Multimodal or non-Gaussian beliefs, constraints | `ParticleFilter` | Weight normalization, effective sample size, resampling, weighted mean |
| Model functions for the nonlinear filters | `VectorFunction` | `apply(in, out)`: a transition, a measurement function or a Jacobian |
| Smoothing one noisy input signal (pointer, controller, sensor) | `Smoothers` | `alphaBeta`, `oneEuro`; no matrices needed |
| Smoothing a recorded signal offline | `Smoothers` | `savitzkyGolay` coefficients, `median` |

## Choosing an estimator

All the Kalman variants describe the belief about the state as a mean `x` and a covariance `P` (a Gaussian). They
differ in how they push that belief through the model:

- **Linear model, Gaussian noise: `Kalman`.** If the next state is a matrix times the current state
  (`x' = F·x + w`) and the measurement is a matrix times the state (`z = H·x + v`), the Kalman filter is exact. Use
  it for constant-velocity or constant-acceleration tracking with position measurements, sensor fusion of linear
  quantities, and similar problems.
- **Mildly nonlinear: `ExtendedKalman`.** If `f` or `h` is nonlinear but close to linear over the size of the
  uncertainty (for example range and bearing to a target that is not too close), the EKF linearizes them with their
  Jacobians at the current estimate. It costs about as much as `Kalman`, but you write the Jacobians, and it can
  diverge when the uncertainty is large compared to the curvature of the model.
- **Strongly nonlinear: `UnscentedKalman`.** The UKF passes `2n + 1` sigma points through the model functions
  and recovers the mean and covariance from them, exactly to second order. It needs no Jacobians and handles
  stronger nonlinearity than the EKF. For a linear model it gives the same result as `Kalman`.
- **Multimodal or non-Gaussian: `ParticleFilter`.** When the belief cannot be summarized by one mean and covariance
  (the object could be in one of two corridors, the noise has heavy tails, the state has hard constraints), represent
  it with a set of weighted samples. The cost grows with the number of particles, which grows quickly with the state
  dimension, so keep the state small.

If you only need to remove jitter from one input signal and do not have a motion model, use `Smoothers.oneEuro`
instead of a filter.

## Layout of the state and the model

`n` is the number of state values and `m` the number of measured values. All matrices are row-major `double[]`
starting at index 0 (element `(i, j)` of an `r×c` matrix is at `i·c + j`). This differs from the column-major
`Matrix4f`; see [conventions](../conventions.md#coordinates-angles-and-matrices).

| Array | Size | Meaning |
|---|---|---|
| `x` | `n` | The state estimate. Updated in place |
| `p` | `n×n` | The state covariance `P`. Updated in place, kept exactly symmetric |
| `f` | `n×n` | The transition matrix `F` (linear filter only) |
| `q` | `n×n` | The process noise covariance `Q` |
| `h` | `m×n` | The measurement matrix `H` (linear filter only) |
| `r` | `m×m` | The measurement noise covariance `R` |
| `z` | `m` | One measurement |

Units are yours, but they must agree: if `x` holds metres and metres per second, `P` and `Q` hold squared metres,
metre-seconds and so on, and `R` holds the squared units of the measurement.

## Track an object with the linear Kalman filter

The example `KalmanTracking` tracks an object moving at constant velocity from noisy position measurements. The state
is position and velocity, and the filter measures the position only:

<!-- snippet: KalmanTracking#model -->
```java
int n = 2;
int m = 1;
double[] x = {0, 0};                     // initial guess: at rest at the origin
double[] p = {10, 0, 0, 10};             // ... with large uncertainty
double[] f = {1, DT, 0, 1};              // p' = p + v·dt, v' = v
double q0 = 0.01;                        // process noise: small random accelerations
double[] q = {q0 * DT * DT * DT / 3, q0 * DT * DT / 2, q0 * DT * DT / 2, q0 * DT};
double[] h = {1, 0};                     // we measure the position only
double[] r = {NOISE * NOISE};
double[] scratch = new double[Kalman.scratchSize(n, m)];
```

`Kalman.scratchSize(n, m)` gives the number of doubles of scratch that `Kalman.predict` and `Kalman.update` need.
Allocate it once per thread. Each step then predicts with the motion model and corrects with the measurement:

<!-- snippet: KalmanTracking#loop -->
```java
Kalman.predict(n, x, p, f, q, scratch);
// Gate before updating: innovationDistance gives the squared Mahalanobis distance of the measurement
// without changing the state, so an implausible measurement is simply skipped.
if (Kalman.innovationDistance(n, m, x, p, h, r, z, scratch) <= GATE) {
    Kalman.update(n, m, x, p, h, r, z, scratch);
} else {
    rejected++;
}
```

- `Kalman.predict(n, x, p, f, q, scratch)` computes `x = F·x` and `P = F·P·Fᵀ + Q`.
- `Kalman.update(n, m, x, p, h, r, z, scratch)` computes the innovation `y = z - H·x`, the gain
  `K = P·Hᵀ·S⁻¹` with `S = H·P·Hᵀ + R`, then `x += K·y` and the new covariance. It returns the squared Mahalanobis
  distance `yᵀ·S⁻¹·y` of the innovation.
- `Kalman.innovationDistance(n, m, x, p, h, r, z, scratch)` returns the same distance without changing `x` and `p`.

If several sensors measure at different times, call `update` once per measurement with that sensor's `h`, `r` and
`m`. If measurements are missing for a step, call `predict` only. If the time step varies, rebuild `F` and `Q` for
each step's `dt`.

`update` throws `IllegalStateException` when `S` is not positive definite. In practice this means `R` is not
positive definite (a zero variance, for example) or `P` has been corrupted by NaN. `x` and `p` are unchanged when it
throws.

## Set the noise covariances Q and R

The two covariances decide how much the filter trusts the model versus the measurements.

- **`R`, measurement noise.** The variance of the sensor error, in squared measurement units. Take it from the sensor
  specification, or record the sensor while the true value is constant and compute the variance of the readings. The
  example uses `NOISE * NOISE` for a position noise with a standard deviation of `NOISE` metres. Off-diagonal entries
  are the covariances between measured values; leave them 0 if the errors are independent.
- **`Q`, process noise.** How far the true state can drift from the model's prediction in one step. Use 0 for
  quantities that cannot change unpredictably and larger values for those that can.

For a constant-velocity model driven by random accelerations with spectral density `q0`, the standard choice is the
one in the example:

```text
Q = q0 · | dt³/3  dt²/2 |
         | dt²/2  dt    |
```

`Q` scales with `dt`, so recompute it when the step length changes.

The ratio between `Q` and `R` matters more than their absolute values. A larger `Q` (or a smaller `R`) makes the
filter follow the measurements more closely: it responds faster to manoeuvres but passes more noise through. A
smaller `Q` gives a smoother estimate that lags behind real changes.

To check your values, watch the distance that `update` returns. For a consistent filter its average is about `m`,
the number of measured values. A much larger average means the filter is overconfident (`Q` or `R` too small); a
much smaller average means it is too cautious (`Q` or `R` too large).

Both matrices must be symmetric. `R` must be positive definite; `Q` may be positive semi-definite (zero noise on some
state values).

## Reject outliers with innovation gating

The value `update` returns, `d² = yᵀ·S⁻¹·y`, measures how surprising the measurement is given the current estimate,
in units of standard deviations squared. For a correct measurement it follows a chi-square distribution with `m`
degrees of freedom, so you can reject measurements whose `d²` exceeds a threshold:

| Measured values `m` | 95 % of good measurements pass | 99 % pass |
|---|---|---|
| 1 | 3.84 | 6.63 |
| 2 | 5.99 | 9.21 |
| 3 | 7.81 | 11.34 |

To gate, compute the distance first with `Kalman.innovationDistance`, which leaves `x` and `p` unchanged and returns
exactly what `update` would, and call `update` only when the measurement passes. The example loop above does this
with a threshold of 16, four standard deviations for one measured value.
`ExtendedKalman.innovationDistance` and `UnscentedKalman.innovationDistance` do the same for the nonlinear filters,
whose `update` returns the same distance.

Gate only after the filter has settled. While `P` is still large, almost every measurement passes, and a filter that
rejects too many measurements in a row can lose track; count consecutive rejections and reinitialize when they pile
up.

## Nonlinear models with the extended Kalman filter

Write the model as `VectorFunction`s. `VectorFunction.apply(in, out)` reads `in` and writes `out`, and must not
keep either array. An `ExtendedKalman` takes four of them:

| Constructor argument | Maps | Writes |
|---|---|---|
| `f` | state → next state | `n` values |
| `fJacobian` | state → `∂f/∂x` at that state | `n×n`, row-major |
| `h` | state → predicted measurement | `m` values |
| `hJacobian` | state → `∂h/∂x` at that state | `m×n`, row-major |

Create the filter once with `new ExtendedKalman(n, m, f, fJacobian, h, hJacobian)`, then call
`predict(x, p, q)` and `update(x, p, r, z)` each step. `x`, `p`, `q`, `r` and `z` have the layouts of the table
above. The update uses the same numerics as `Kalman` and returns the same gating distance.

If the step length varies, let `f` and `fJacobian` read `dt` from a field you set before each `predict`.

Angles need care. By default the filter computes the innovation as `z - h(x)`, so a bearing measured as +179°
against a prediction of -179° gives an innovation of 358°. Pass an `InnovationFunction` as the seventh constructor
argument, `new ExtendedKalman(n, m, f, fJacobian, h, hJacobian, innovation)`, to form the innovation yourself, for
example wrapping the angle difference into [-π, π] (the `InnovationFunction` Javadoc shows one). Wrap angle state
values back into range after `predict` and `update` (they are in your `x` array).

## Nonlinear models with the unscented Kalman filter

`new UnscentedKalman(n, m, f, h, alpha, beta, kappa)` needs only the transition `f` and the measurement function
`h`, no Jacobians. `predict(x, p, q)` and `update(x, p, r, z)` work as in the EKF and `update` returns the same gating
distance.

The three parameters place the sigma points `x ± columns of chol((n + λ)·P)`, with `λ = α²(n + κ) - n`:

| Parameter | Typical value | Meaning |
|---|---|---|
| `alpha` | `1e-3` | Spread of the sigma points around the mean; must be positive |
| `beta` | `2` | Prior knowledge of the distribution; 2 is optimal for Gaussians |
| `kappa` | `0` | Secondary scaling |

The UKF calls each model function `2n + 1` times per step, so its cost grows with the state size faster than the
EKF's. It throws `IllegalStateException` when `P` or `S` is not positive definite, and the message says which; this
usually means `Q` or `R` is too small or contains NaN. The constructor requires `n + kappa > 0`. Angles need the same care as in the EKF; sigma points spread across ±π average to the
wrong angle, so keep the angle uncertainty well below π.

## Multimodal beliefs with a particle filter

`ParticleFilter` provides the steps of a bootstrap particle filter. You hold the particles in a `double[]` of
`count × dim` values and their log-weights in a `double[]` of `count` values. One filter step is:

1. **Move.** Pass every particle through your transition and add process noise. Draw the noise from
   `hash.Philox`, with an index derived from the step number and the particle index (for example
   `hash.Philox.fillNormal(seed, (step * count + i) * dim, 0f, sigma, noise, 0, dim)`). The filter is then
   reproducible and you can move particles on several threads.
2. **Weigh.** Add each particle's measurement log-likelihood to its log-weight (for Gaussian measurement noise,
   `-d²/2`, with `d²` the squared Mahalanobis distance of the measurement from that particle's predicted
   measurement).
3. **Normalize.** `ParticleFilter.normalizeLogWeights(logW, count, w)` turns log-weights into weights that sum to 1
   without underflow. It returns the step's log-evidence increment (the log of the mean weight), which you can use
   to compare models.
4. **Estimate.** `ParticleFilter.mean(particles, dim, count, w, mean)` writes the weighted mean.
5. **Resample when needed.** When `ParticleFilter.effectiveSampleSize(w, count)` drops below about `count / 2`, call
   `ParticleFilter.systematicResample(w, count, u, indices)` with one uniform number `u` in [0, 1), or
   `ParticleFilter.systematicResample(w, count, seed, step, indices)`, which draws `u` with
   `hash.Philox.uniformDouble(seed, step)`; then `ParticleFilter.gather(particles, dim, count, indices, out)`. `out` must
   be a second particle array: swap the two arrays after each resampling, and reset all log-weights to 0.

Use `ReproducibleMath.exp` and `ReproducibleMath.log` in your likelihoods if the results must match across machines.
A weighted mean is a poor summary of a multimodal belief; to report the most likely mode, take the particle with the
largest weight or cluster the particles.

## Smoothing

There is no fixed-interval (Rauch–Tung–Striebel) smoother: the Kalman filters give the estimate at each step from
the measurements up to that step. For smoothing a signal, `Smoothers` has:

- **`Smoothers.alphaBeta(state, z, dt, alpha, beta)`**: a fixed-gain position and velocity tracker. It behaves
  like a constant-velocity Kalman filter whose gain has settled, without the matrices. `state`
  is `[x, v]`, updated; it returns the filtered position.
- **`Smoothers.oneEuro(state, x, dt, minCutoff, beta, dCutoff)`**: the One Euro filter for pointer, controller and
  sensor input. Its cutoff rises with speed, so it removes jitter at rest without lagging in fast motion. `state` is
  three doubles, initially zero; cutoffs are in hertz (for example `minCutoff` 1, `beta` 0.007, `dCutoff` 1) and
  `dt` in seconds.
- **`Smoothers.savitzkyGolay(half, order, derivative, coeffs)`**: convolution coefficients that smooth (or
  differentiate) a recorded signal while keeping peak shapes. Compute them once at setup.
- **`Smoothers.median(x, xOff, n, half, y, yOff, scratch)`**: a running median that removes isolated spikes.

## Accuracy, allocation and threading

- **Numerics.** `Kalman.update` and `ExtendedKalman.update` solve for the gain with a Cholesky factorization of `S`
  (no matrix inverse) and update the covariance in Joseph form, `P = (I - KH)P(I - KH)ᵀ + KRKᵀ`, which keeps `P`
  symmetric and positive semi-definite despite rounding. Both filter steps symmetrize `P` exactly.
  `UnscentedKalman.update` computes the unscented `P - K·S·Kᵀ` in the equivalent Joseph form, with the statistically
  linearized `H = Pxzᵀ·P⁻¹` and `R` plus the covariance of the sigma points' linearization residuals, so it too keeps
  `P` symmetric and positive semi-definite (for any `beta >= alpha²`, which includes the usual `beta = 2`).
- **Reproducibility.** Every sum runs in a fixed order with fused multiply-adds, so the same inputs give the same
  bits. `ParticleFilter.normalizeLogWeights` uses `ReproducibleMath` for its exponentials and logarithms.
- **Allocation.** `Kalman` and `ParticleFilter` never allocate. `ExtendedKalman` and `UnscentedKalman` allocate their
  work arrays in the constructor and nothing per step (your model functions must not allocate either if you need an
  allocation-free loop).
- **Threading.** `Kalman` and `ParticleFilter` are static and keep no state: share `f`, `q`, `h` and `r` across
  threads, and give each thread its own `x`, `p` and scratch. An `ExtendedKalman` or `UnscentedKalman` instance owns
  work arrays that every call writes, so it is single-threaded: create one instance per estimated stream and thread.
  Your `x` and `p` arrays still hold the estimate, so you can move a stream between instances.

## See also

- [Conventions](../conventions.md): row-major matrices in `dsp`, scratch arrays, determinism, threading, exceptions.
- [Machine-learning kernels](machine-learning.md): `hash.Philox` and `ReproducibleMath` in more detail.
- [Signal processing](signal-processing.md): the rest of the `dsp` package (filters, spectra, resampling).
- Example: [KalmanTracking](../../examples/src/main/java/org/dynamisengine/vectrix/examples/KalmanTracking.java).
- Source: [dsp](../../vectrix-signal/src/main/java/org/dynamisengine/vectrix/dsp/),
  [hash](../../vectrix-core/src/main/java/org/dynamisengine/vectrix/hash/).
