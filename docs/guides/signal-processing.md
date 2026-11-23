# Signal Processing

This guide shows you how to transform, filter and analyse sampled signals with Vectrix: audio, sensor data, control
input and images. It covers two packages. `fft` holds planned Fourier transforms of any size (1D and 2D, complex and
real input) and streaming FIR convolution. `dsp` builds on it: windows, FIR and IIR filter design, spectral
estimates, the short-time Fourier transform, cosine transforms, resampling, wavelets, correlation, Wiener and
adaptive filters, smoothers and Toeplitz solvers.

The state estimators in `dsp` (`Kalman`, `ExtendedKalman`, `UnscentedKalman`, `ParticleFilter`) have their own
guide: [state-estimation.md](state-estimation.md).

## Which class for which job

| Task | Class | Notes |
|---|---|---|
| Fourier transform of complex data | `ComplexFftd`, `ComplexFftf` | Any size; interleaved re/im; in place |
| Fourier transform of real data | `RealFftd`, `RealFftf` | Half spectrum of `n/2 + 1` bins; about twice as fast for even sizes |
| 2D transforms (images, grids) | `ComplexFft2d`, `RealFft2d` (and `...2f`) | Row-major grids |
| Filter a stream with an FIR kernel | `BlockConvolverd`, `BlockConvolverf` | Picks direct or FFT overlap-add; caller-held state |
| Window functions | `Windows` | Symmetric for filter design, periodic for spectral analysis |
| Design an FIR filter | `FirDesign` | Window method, Kaiser formulas, Parks–McClellan (`FirDesign.remez`) |
| Design and run IIR filters | `BiquadDesign`, `Biquad` | Cookbook sections, Butterworth cascades of any order |
| Power spectral density | `Spectra` | Periodogram, Welch, cross spectrum, analytic signal |
| Time–frequency analysis and resynthesis | `Stftd`, `Stftf` | Exact reconstruction for any windows whose overlap sum has no zero |
| Cosine transforms | `Dctd`, `Dctf`, `Mdctd`, `Mdctf` | DCT-II/III/IV, MDCT with time-domain aliasing cancellation |
| Change the sample rate | `Resampler` | Rational `up/down`, polyphase, streaming |
| Wavelet decomposition | `Wavelet` | Haar and Daubechies 1–10, periodic boundaries |
| Cross- and autocorrelation | `Correlation` | Direct or FFT, chosen by cost |
| One frequency of a long signal, tone detection | `Goertzel` | O(n), accurate near DC and Nyquist |
| Optimal noise removal and deblurring | `Wiener` | FIR, non-causal, per-bin gain, 1D and 2D deconvolution, adaptive image filter |
| Filters that learn | `AdaptiveFilter` | LMS, NLMS, RLS, one sample per call |
| Smooth noisy samples or input devices | `Smoothers` | Savitzky–Golay, median, alpha-beta, One Euro |
| Symmetric Toeplitz systems, linear prediction | `Toeplitz` | Levinson and Levinson–Durbin |
| Track a state from noisy measurements | `Kalman` and friends | See [state-estimation.md](state-estimation.md) |

Unsuffixed `dsp` classes work in double. Classes ending in `f` work in float; where a `dsp` method has a float
overload without a float class (`Biquad.process`, `Resampler.process`, `Goertzel.dft`, `Windows.hann` and the other
windows, `Correlation.correlate`, `Toeplitz.solve`, `Toeplitz.levinsonDurbin`, `Wavelet.forward` and
`Wavelet.inverse`), it computes in double and rounds once: the result is the double method's on the widened inputs,
rounded to float. Their scratch arrays are double.

## Units and conventions

- **Frequencies are in cycles per sample.** Divide hertz by the sample rate: 80 Hz at 1000 samples per second is
  `0.08`. The useful range is 0 to 0.5 (Nyquist). Every design and response method in `dsp` uses this unit. The one
  exception is `Smoothers.oneEuro`, whose cutoffs are in hertz and whose time step is in seconds.
- **FFT bins.** Bin `k` of an `n`-point transform is at `k/n` cycles per sample, which is `k·rate/n` hertz.
- **Complex data is interleaved:** `data[2k]` is the real part of element `k`, `data[2k + 1]` its imaginary part.
  A half spectrum, a cross spectrum and a complex filter response all use this layout.
- **Forward transforms are unscaled; inverses divide by `n`** (by `rows·cols` in 2D), so inverse(forward(x)) is x.
- **Plans, state and scratch.** You create a plan once (`ComplexFftd.plan`, `BlockConvolverd.plan`,
  `Stftd.plan`, ...). A plan is immutable and can be shared by any number of threads. Every call takes a scratch
  array that you own (ask the plan for its `scratchSize()`), and streaming classes take a state array that you own
  (`stateSize()`, zero at the start of a stream). One plan can therefore drive many channels and threads: give each
  stream its own state and each thread its own scratch.
- Arrays are passed with an offset (and a count where it varies). See [conventions](../conventions.md) for the
  rules shared by all array APIs, for type suffixes and for exceptions.

## Fourier transforms

`ComplexFftd.plan(n)` makes a plan for `n` complex values, for any `n` from 1 to `ComplexFftd.MAX_SIZE` (2²⁸).
`forward(data, off, scratch, scratchOff)` computes `X[k] = Σ x[j]·exp(-2πi jk/n)` in place;
`inverse` uses `exp(+2πi jk/n)` and divides by `n`.

`RealFftd.plan(n)` transforms `n` real values. Because the spectrum of a real signal is Hermitian
(`X[n-k] = conj(X[k])`), it stores only bins `0..n/2`: `spectrumLength()` doubles, interleaved. The imaginary parts
of bin 0 and (for even `n`) bin `n/2` are always zero, and `inverse` treats them as zero. `forward(in, inOff, out,
outOff, scratch, scratchOff)` does not modify `in`; `in` and `out` may be the same array at the same offset if it
has room for `spectrumLength()` values.

Sizes whose prime factors are only 2, 3 and 5 are the fastest. `ComplexFftd.fastSize(min)` returns the smallest
such size of at least `min`; pad to it for convolution and spectral analysis. Sizes with a prime factor above 61 use
Bluestein's algorithm and are limited to 2²⁷.

**2D.** `ComplexFft2d.plan(rows, cols)` and `RealFft2d.plan(rows, cols)` transform row-major grids. Complex element
`(r, c)` is at `off + 2·(r·cols + c)`. `RealFft2d` keeps `cols/2 + 1` complex values per row
(`spectrumRowLength()` doubles), so the spectrum is `rows × spectrumRowLength()`. For `RealFft2d`, input and output
must not overlap; neither `forward` nor `inverse` modifies its input. The 2D inverses divide once by `rows·cols`,
after both passes.

**Off-heap data.** `ComplexFftd`, `ComplexFftf`, `RealFftd` and `RealFftf` have `MemorySegment` overloads of
`forward` and `inverse`. They read native-order values from the start of the segment and need a heap scratch array
of `segmentScratchSize()` values. The 2D plans, the convolver and the `dsp` classes take arrays only.

## Streaming FIR convolution

`BlockConvolverd.plan(kernel, off, length, blockSize)` copies the filter taps `h` and prepares to filter blocks of up
to `blockSize` samples: `y[t] = Σ h[j]·x[t-j]` over the whole stream. Then, per block:

- `process(in, inOff, count, out, outOff, state, stateOff, scratch, scratchOff)` filters `count <= blockSize()`
  samples and writes `count` outputs. `in` and `out` may be the same array at the same offset.
- `state` holds `stateSize()` = `kernelLength - 1` values carried between blocks; it is zero at the start.
- `flush(out, outOff, state, stateOff)` writes the last `kernelLength - 1` outputs at the end of the stream and
  clears the state.

Blocks may be any size up to the block size, so you can feed audio as it arrives. The plan convolves directly
(fused multiply-adds) when that is cheaper, which is always the case for kernels of at most 16 taps, and otherwise
uses real FFTs (overlap-add). `usesFft()` tells you which path it chose. For one-shot correlation of two fixed
sequences, use `Correlation` instead.

## Windows

A window tapers a segment to reduce spectral leakage, or shapes an ideal filter into a finite one. Each method
writes `n` values to `dest[off .. off + n)`:

| Method | Character |
|---|---|
| `Windows.hann` | 31.5 dB side lobes, 1.5 bins noise bandwidth; the usual default for spectra |
| `Windows.hamming` | 42.7 dB side lobes |
| `Windows.blackman` | 58 dB side lobes |
| `Windows.blackmanHarris` | 92 dB side lobes (4 terms) |
| `Windows.nuttall` | 98 dB side lobes |
| `Windows.flatTop` | Amplitude error below 0.01 dB for tones between bins: use it to measure tone levels |
| `Windows.kaiser` | Shape `β` trades main-lobe width for side-lobe level; `FirDesign.kaiserBeta` picks it |
| `Windows.tukey` | Flat middle with Hann tapers over a fraction `alpha` |
| `Windows.gaussian` | Standard deviation `sigma` in samples |
| `Windows.chebyshev` | Narrowest main lobe for equiripple side lobes at a given attenuation (allocates) |
| `Windows.sine` | The MDCT analysis/synthesis window |

The `periodic` flag matters. A **symmetric** window (`periodic = false`) has `w[k] == w[n-1-k]` exactly; use it for
FIR design. A **periodic** window is the first `n` values of the symmetric window of length `n + 1`; use it for
spectral analysis (Welch, STFT).

`Windows.coherentGain` (`Σw / n`) is the factor by which a window scales a tone's amplitude.
`Windows.equivalentNoiseBandwidth` (`n·Σw² / (Σw)²`, in bins) is its noise power relative to a rectangular window.

## Spectra

Signal processing usually starts with "what frequencies are in this signal?". The example below estimates the
power spectral density (PSD) with Welch's method: it splits the signal into overlapping Hann-windowed segments,
computes each segment's periodogram and averages them.

<!-- snippet: SignalProcessing#spectrum -->
```java
// Welch's estimate: Hann-windowed segments of 1024 samples, overlapping by half, averaged.
RealFftd plan = RealFftd.plan(SEGMENT);
double[] window = new double[SEGMENT];
Windows.hann(window, 0, SEGMENT, true);
double[] psd = new double[SEGMENT / 2 + 1];
double[] scratch = new double[Spectra.scratchSize(plan)];
Spectra.welch(plan, window, 0, SEGMENT / 2, x, 0, LENGTH, psd, 0, scratch, 0);

int peak = 0;
for (int k = 1; k < psd.length; k++) {
    peak = psd[k] > psd[peak] ? k : peak;
}
double peakHz = peak * RATE / SEGMENT; // bin k is k·rate/segment hertz
```

The methods of `Spectra`:

- `Spectra.periodogram(plan, window, windowOff, x, xOff, psd, psdOff, scratch, scratchOff)`: one segment of
  `plan.size()` samples. Pass `null` as the window for a rectangular one.
- `Spectra.welch(plan, window, windowOff, hop, x, xOff, length, psd, psdOff, scratch, scratchOff)`: the average over
  segments advancing by `hop` (half the segment length is the usual choice with Hann). Samples after the last whole
  segment are ignored. Returns the number of segments.
- `Spectra.crossWelch`: the cross spectral density `P_xy[k] = c·conj(X[k])·Y[k] / Σw²` of two signals, as
  interleaved complex values. Its scratch size is `Spectra.crossScratchSize(plan)`.
- `Spectra.analytic(plan, x, xOff, out, outOff, scratch, scratchOff)`: the analytic signal `x + i·H{x}` (`H` is the
  Hilbert transform), with a `ComplexFftd` plan. Its magnitude is the envelope and its argument the instantaneous
  phase. The FFT treats the signal as periodic, so expect edge effects at both ends.

Scratch for the first two is `Spectra.scratchSize(RealFftd)`, for `analytic` `Spectra.scratchSize(ComplexFftd)`.

**Scaling.** PSDs are one-sided and density-scaled for frequencies in cycles per sample:

```text
P[k] = c · |X[k]|² / Σ w[j]²        c = 1 at DC and (even n) at Nyquist, c = 2 elsewhere
```

With this scaling, `Σ P[k] / n` over all bins is the mean power of the signal (Parseval). So the power in a band is
the sum of `P[k] / n` over its bins, which is how the example measures each tone. For a density per hertz, multiply
by `1/sampleRate`; for decibels, use `10·log10`.

**From a `RealFftd` spectrum to a periodogram.** If you already have a half spectrum, you can scale it yourself:

1. Multiply the `n` samples by the window `w`.
2. Call `RealFftd.forward`. Bin `k` is `re = out[2k]`, `im = out[2k + 1]`.
3. `P[k] = c·(re² + im²) / Σw²`, with `c` as above. With no window, `Σw²` is `n`.

This is exactly what `Spectra.periodogram` does. To read a tone's **amplitude** rather than its power from the raw
spectrum, use `2·|X[k]| / Σw` (`Σw` is `n·Windows.coherentGain`), preferably with a flat-top window so a tone between
bins is not attenuated.

## FIR filters

An FIR (finite impulse response) filter is a list of taps convolved with the signal. `FirDesign` makes linear-phase
filters: the taps are symmetric, so every frequency is delayed by the same `(n-1)/2` samples and wave shapes are
preserved. The example designs an 80 Hz low-pass with a Kaiser window and streams the signal through it:

<!-- snippet: SignalProcessing#fir -->
```java
// Low-pass at 80 Hz: Kaiser window for 60 dB of stop-band attenuation, 20 Hz transition band.
double attenuation = 60;
double transition = 20 / RATE;
int taps = FirDesign.kaiserTaps(attenuation, transition) | 1; // odd length: integer delay
double[] kaiser = new double[taps];
Windows.kaiser(kaiser, 0, taps, FirDesign.kaiserBeta(attenuation), false);
double[] h = new double[taps];
FirDesign.lowpass(h, 0, taps, 90 / RATE, kaiser, 0); // cutoff mid-transition: 80 to 100 Hz

// Stream the signal through the filter in blocks of 256 samples, as audio would arrive.
int block = 256;
BlockConvolverd fir = BlockConvolverd.plan(h, 0, taps, block);
double[] state = new double[fir.stateSize()];
double[] firScratch = new double[fir.scratchSize()];
double[] firOut = new double[LENGTH];
for (int off = 0; off < LENGTH; off += block) {
    fir.process(x, off, Math.min(block, LENGTH - off), firOut, off, state, 0, firScratch, 0);
}
```

**Window method.** `FirDesign.lowpass`, `FirDesign.highpass`, `FirDesign.bandpass` and `FirDesign.bandstop` write
`n` taps: the ideal response times a window (any `Windows` output of length `n`, or `null` for rectangular), scaled
to exactly unit gain at DC (low-pass, band-stop), Nyquist (high-pass) or the band center (band-pass). High-pass and
band-stop need an **odd** number of taps. Pass a symmetric window (`periodic = false`).

**Kaiser formulas.** Specify the stop-band attenuation in dB and the transition width in cycles per sample.
`FirDesign.kaiserTaps` estimates the length and `FirDesign.kaiserBeta` the window shape; both reject an attenuation
that is not positive and finite. Put the cutoff in the middle of the transition band.

**Parks–McClellan.** `FirDesign.remez(h, off, n, bands, desired, weights)` designs the optimal equiripple filter: for
a given length, it has the smallest maximum weighted error and beats any window design. `n` must be odd and at least
3. `bands` holds pairs of edges in cycles per sample, ascending in `[0, 0.5]`; the gaps between pairs are free
transition bands. `desired` gives each band's gain and `weights` its weight (a band weighted 10 gets a tenth of the
ripple). The method returns the achieved weighted ripple `δ`; an infeasible specification returns its best attempt,
recognisable by a large ripple. For a low-pass with pass band to 0.1 and stop band from 0.15, the inputs are
`bands = {0, 0.1, 0.15, 0.5}`, `desired = {1, 0}`, `weights = {1, 10}`.

**Checking a design.** `FirDesign.response` writes the complex response `H(f)` at any frequency and
`FirDesign.magnitude` returns `|H(f)|`. Run the taps with `BlockConvolverd` (see above).

## IIR filters: biquads and Butterworth

An IIR (infinite impulse response) filter feeds its output back, so a few coefficients give a sharp response. The
price is a phase that varies with frequency. Vectrix runs IIR filters as cascades of second-order sections
(biquads). The example builds a 6th-order Butterworth low-pass:

<!-- snippet: SignalProcessing#iir -->
```java
// The same job with a 6th-order Butterworth low-pass at 80 Hz: three second-order sections, far fewer
// operations than the FIR, but with a non-linear phase.
double[] sos = new double[BiquadDesign.butterworthSections(6) * BiquadDesign.STRIDE];
int sections = BiquadDesign.butterworthLowpass(sos, 0, 6, 80 / RATE);
double[] iirState = new double[sections * Biquad.STATE_STRIDE];
double[] iirOut = new double[LENGTH];
Biquad.process(sos, 0, sections, iirState, 0, x, 0, iirOut, 0, LENGTH);
```

**Layout.** Each section is `BiquadDesign.STRIDE` = 5 doubles:

```text
b0, b1, b2, a1, a2        H(z) = (b0 + b1 z⁻¹ + b2 z⁻²) / (1 + a1 z⁻¹ + a2 z⁻²)
```

A cascade is consecutive sections. The state is `Biquad.STATE_STRIDE` = 2 doubles per section, zero at the start of
a stream.

**Designs** (`BiquadDesign`, all frequencies in cycles per sample, exact at the stated frequency thanks to
pre-warping):

| Method | Response |
|---|---|
| `BiquadDesign.lowpass`, `BiquadDesign.highpass` | `\|H(f0)\| = q`; `q = 1/√2` is a 2nd-order Butterworth |
| `BiquadDesign.bandpass` | Unit gain at `f0`, bandwidth `f0 / q` |
| `BiquadDesign.notch` | Zero gain at `f0` |
| `BiquadDesign.allpass` | Unit gain everywhere, phase −π at `f0` |
| `BiquadDesign.peaking` | Gain `gainDb` at `f0` (parametric EQ) |
| `BiquadDesign.lowShelf`, `BiquadDesign.highShelf` | Gain `gainDb` at DC or Nyquist, half of it (in dB) at `f0` |
| `BiquadDesign.butterworthLowpass`, `BiquadDesign.butterworthHighpass` | Any order; `\|H(f0)\|² = 1/2`; returns the section count |

`BiquadDesign.butterworthSections(order)` tells you how many sections to allocate (`ceil(order / 2)`; an odd order
ends with a first-order section).

**Running.** `Biquad.process(coeffs, coeffOff, sections, state, stateOff, in, inOff, out, outOff, count)` filters a
block in transposed direct form II. `in` may be `out`, and any chunking of a stream gives identical results. The
float overload also computes in double with double state. `Biquad.response` and `Biquad.magnitude` evaluate the
cascade's response at a frequency.

**Very low cutoffs.** A section tuned well below 1e-3 cycles per sample is sensitive to coefficient rounding
(at `f0 = 1e-4` the gain keeps about 9 significant digits). Lower the sample rate for such filters, for example
with `Resampler`.

## Short-time Fourier transform

The STFT cuts a signal into overlapping frames and transforms each one, so you can see or change how the spectrum
evolves (spectrograms, noise reduction, pitch and time effects).

`Stftd.plan(n, hop, analysisWindow, analysisOff, synthesisWindow, synthesisOff)` makes a plan for frames of `n`
samples advancing by `hop`. Pass `null` as the synthesis window to reuse the analysis window. Then:

- `analyze(in, inOff, out, outOff, scratch, scratchOff)` windows frame `in[inOff .. inOff + n)` and writes its half
  spectrum (`spectrumLength()` values, as `RealFftd`). `out` must not overlap `in`.
- `synthesize(spectrum, spectrumOff, out, outOff, state, stateOff, scratch, scratchOff)` inverse-transforms a
  (possibly modified) spectrum, applies the synthesis window, overlap-adds it into `state` (`stateSize()` = `n`
  values, zero at the start) and writes the `hop` samples that are now complete.

Analysis followed by synthesis reconstructs the signal to rounding for **any** windows and hop whose overlap sum
`Σ wa·ws` has no zero (the plan rejects others); the windows do not have to satisfy the constant-overlap-add
condition. Synthesizing frame `t` emits samples `[t·hop, t·hop + hop)`. The first frames lack earlier overlapping
frames, so prepend `n - hop` zeros to the signal if you need it back from its first sample.

To denoise, combine it with `Wiener.gain` and `Wiener.apply` (see [Wiener filtering](#wiener-filtering)).
`Stftf` is the float twin.

## Cosine transforms: DCT and MDCT

`Dctd.plan(n)` uses FFTW's unnormalized conventions:

```text
forward   (DCT-II, any n):   X[k] = 2·Σ x[j]·cos(π(j + 1/2)k/n)
inverse   (DCT-III / 2n):    exact inverse of forward
forwardIV (DCT-IV, even n):  X[k] = 2·Σ x[j]·cos(π(j + 1/2)(k + 1/2)/n)
inverseIV:                   DCT-IV / 2n
```

For the orthonormal DCT-II, scale `X[0]` by `1/(2√n)` and the other coefficients by `1/√(2n)`. All four methods
allow `in == out` at the same offset.

`Mdctd.plan(n)` (n even) maps `2n` samples to `n` coefficients, the transform behind most audio codecs. `inverse`
includes the factor `2/n`, so time-domain aliasing cancellation is exact: take frames of `2n` samples advancing by
`n`, multiply each by a Princen–Bradley window such as `Windows.sine` before `forward` and again after `inverse`,
and overlap-add. Input and output must not overlap. `Dctf` and `Mdctf` are the float twins.

## Resampling

`Resampler.plan(up, down)` converts the sample rate by `up/down` (44100 → 48000 Hz is `plan(160, 147)`) with a
polyphase Kaiser-windowed low-pass: 32 zero crossings per side and 100 dB stop-band attenuation.
`Resampler.plan(up, down, zeroCrossings, attenuationDb)` sets both: more zero crossings give a narrower transition
band at a higher cost. `up()` and `down()` return the factors reduced by their common divisor.

`process(in, inOff, count, out, outOff, state, stateOff)` consumes any number of input samples and returns the
number of outputs it wrote; size `out` with `maxOutput(count)`. The state (`stateSize()` doubles, zero at the start)
holds the input history and stream position, also for the float overload. Any chunking gives the same output bits.
`out` must not overlap `in`. The output lags the input by `delay()` output samples, the filter's group delay; skip
them if you need the signals aligned.

## Wavelets

`Wavelet.haar()`, `Wavelet.daubechies(p)` (p vanishing moments, 1 to 10, `2p` taps) or `Wavelet.fromLowpass(taps)`
(your own orthonormal low-pass) make an immutable wavelet. `fromLowpass` checks that the taps are orthonormal to
their even shifts within `Wavelet.ORTHONORMALITY_TOLERANCE` (1e-9), the condition for exact reconstruction.
Boundaries are periodic.

- `forward(x, xOff, n, out, outOff)`: one level. `n` is even; `out[0 .. n/2)` receives the approximation
  (low-pass) and `out[n/2 .. n)` the detail (high-pass) coefficients. `out` must not overlap `x`.
- `forward(x, xOff, n, levels, out, outOff, scratch, scratchOff)`: a multilevel transform with `n` divisible by
  `2^levels` and `n` doubles of scratch. The layout is `[aL, dL, d(L-1), ..., d1]`: the coarsest approximation
  first.
- `inverse` with the same arguments reconstructs the signal to rounding.
- The float overloads compute in double. The one-level float `inverse` takes `n` doubles of scratch, and the
  multilevel float overloads `2n`, to keep the intermediate levels in double.

A typical use is denoising: transform, shrink small detail coefficients towards zero, inverse-transform.
`lowpass()` and `highpass()` return copies of the filters.

## Correlation

`Correlation.plan(nx, ny)` computes the full cross-correlation of sequences of fixed lengths:

```text
c[ℓ] = Σ x[i]·y[i + ℓ]      for ℓ = -(nx - 1) .. ny - 1, stored at out[ℓ + nx - 1]
```

`correlate(x, xOff, y, yOff, out, outOff, scratch, scratchOff)` writes `outputLength()` = `nx + ny - 1` values;
`out` must not overlap the inputs. Scratch is `scratchSize()` (0 when the plan sums directly; `usesFft()` tells
you). A float overload takes float sequences and the same double scratch. Correlate a sequence with itself for its
autocorrelation (lag 0 is at `out[nx - 1]`). To find the delay of
`y` relative to `x`, take the index of the largest value and subtract `nx - 1`.

## Goertzel: single frequencies

`Goertzel.dft(x, off, n, f, dest)` computes one DFT value `X(f) = Σ x[k]·exp(-2πi f k)` at any frequency `f` in
cycles per sample (it need not be a bin center), in O(n). `Goertzel.power` returns `|X(f)|²`. Use it for tone
detection (DTMF, pilot tones) or when you need a few frequencies of a long signal; for many frequencies an FFT is
cheaper. Values are unscaled like the FFT. The float overloads compute in double.

## Wiener filtering

A Wiener filter is the linear filter that minimises the mean square error between its output and a desired signal.
`Wiener` offers it in several forms:

| Method | Use |
|---|---|
| `Wiener.fir(rxx, rdx, n, w, scratch)` | Optimal causal `n`-tap FIR from the observation's autocorrelation `rxx` and the cross-correlation `rdx[i] = E[d[t]·x[t - i]]` (lags `0..n-1`); scratch `2n` |
| `Wiener.noncausal(pxx, pxy, bins, h)` | Optimal two-sided filter `H = P_xy / P_xx` per bin, from `Spectra.welch` and `Spectra.crossWelch` |
| `Wiener.impulseResponse(plan, h, taps, scratch)` | Turns a per-bin response into `n` FIR taps, centerd (delay `n/2`); window them to shorten them |
| `Wiener.gain(signalPower, noisePower, n, floor, gain)` | Noise-reduction gain `S/(S + N)` per bin, clamped to `[floor, 1]` |
| `Wiener.apply(spectrum, off, gain, bins)` | Multiplies a half spectrum by per-bin gains (for STFT frames) |
| `Wiener.deconvolve` | Undoes a known blur of a signal: `X = conj(H)·Y / (\|H\|² + nsr)` |
| `Wiener.deconvolve2d` | The same for an image |
| `Wiener.adaptive2d` | Locally adaptive image denoising that keeps edges |

Notes:

- For `Wiener.fir`, you can get `rxx` and `rdx` from `Correlation`: correlate `x` with itself and `x` with `d`, and
  read lags 0 to `n-1` from `out[nx - 1]` on. Scale both the same way; the common scale does not change `w`.
- With `Wiener.gain`, a floor above 0 limits the "musical noise" of spectral subtraction. Bins where `S + N = 0` get
  the floor.
- `Wiener.deconvolve(plan, y, yOff, kernel, kOff, kLen, nsr, out, outOff, scratch, scratchOff)` assumes circular
  convolution with a kernel of `kLen <= n` taps (tap 0 unshifted). `nsr` is the noise-to-signal power ratio: 0 is
  plain inverse filtering, unstable where `H` is small; larger values trade sharpness for less noise. An overload
  takes one ratio per bin (`n/2 + 1` values). Ratios must be `>= 0`. Scratch is `Wiener.deconvolveScratchSize(plan)`.
- `Wiener.deconvolve2d` takes a `RealFft2d` plan, a row-major image and a `kRows × kCols` kernel; scratch is
  `Wiener.deconvolve2dScratchSize(plan)`. Pad the image if you do not want the blur to wrap around the edges.
- `Wiener.adaptive2d(img, rows, cols, half, noiseVariance, out, scratch)` works on `(2·half + 1)²` windows (like
  MATLAB's `wiener2`). Pass a noise variance of 0 or less to estimate it as the mean local variance; the method
  returns the variance it used. Scratch is `2·(rows + 1)·(cols + 1)` doubles.

## Adaptive filters

An adaptive filter adjusts its own taps so its output tracks a desired signal: echo cancellation, system
identification, noise cancellation with a reference microphone. `AdaptiveFilter` provides one step per sample over
arrays you hold:

- `w`: the `n` weights, updated.
- `u`: the `n` most recent inputs, newest first; each step shifts in the new input `x`.
- `d`: the desired sample. Each method returns the a-priori error `e = d - y`.

| Method | Cost per sample | Notes |
|---|---|---|
| `AdaptiveFilter.lms(n, w, u, x, d, mu)` | O(n) | `w += μ·e·u`; the step size depends on the input level |
| `AdaptiveFilter.nlms(n, w, u, x, d, mu, eps)` | O(n) | Step normalized by input power; stable for `0 < μ < 2` at any level |
| `AdaptiveFilter.rls(n, w, u, p, x, d, lambda, scratch)` | O(n²) | Fastest convergence; `p` is the `n×n` row-major inverse correlation matrix, initialized to `δ⁻¹·I` with a small `δ`; forgetting factor `λ` in (0, 1]; scratch `2n` |

Start with `w` and `u` zero.

## Smoothers

`Smoothers` collects smoothing filters for noisy measurements and input devices:

- `Smoothers.savitzkyGolay(half, order, derivative, coeffs)` writes `2·half + 1` coefficients for samples
  `-half..half`: the least-squares polynomial of degree `order` evaluated (or differentiated `derivative` times) at
  the center. Convolve with them, for example with `BlockConvolverd` and the taps reversed. It smooths while keeping
  peak shapes. Divide a derivative by `dt^derivative`. Compute the coefficients once at setup.
- `Smoothers.median(x, xOff, n, half, y, yOff, scratch)` (float) is a running median of `2·half + 1` samples with
  replicated edges. It removes spikes that linear filters smear. `y` must not overlap `x`; scratch is
  `2·half + 1` floats.
- `Smoothers.alphaBeta(state, z, dt, alpha, beta)` is one step of an alpha-beta tracker over `state = [x, v]`.
- `Smoothers.oneEuro(state, x, dt, minCutoff, beta, dCutoff)` is one step of the One Euro filter for pointer and
  controller input: it removes jitter at rest and lag in fast motion. `state` is three doubles, initially zero.
  `dt` is in seconds and the cutoffs in hertz (for example `minCutoff = 1`, `beta = 0.007`, `dCutoff = 1`).

For trackers with a full motion model and uncertainty, see [state-estimation.md](state-estimation.md).

## Toeplitz systems and linear prediction

A symmetric Toeplitz matrix is given by its first row `r` (`T[i][j] = r[|i - j|]`), typically an autocorrelation.
`Toeplitz` solves such systems in O(n²) instead of O(n³):

- `Toeplitz.solve(r, b, n, x, scratch)` solves `T·x = b` (Levinson's algorithm); scratch is
  `Toeplitz.solveScratchSize(n)`, `2n` doubles. `x` may be `b`.
- `Toeplitz.levinsonDurbin(r, p, a, reflection, scratch)` solves the Yule–Walker equations of linear prediction:
  given `r[0..p]` it writes `a[0..p]` with `a[0] = 1` such that `x[t] ≈ -Σ a[k]·x[t - k]`, optionally the `p`
  reflection (PARCOR) coefficients (pass `null` to skip them), and returns the final prediction error power.
  Scratch is `Toeplitz.levinsonDurbinScratchSize(p)`, `2p + 1` doubles. `a` may be `r`.

Both throw `IllegalArgumentException` when a leading submatrix is singular or (for Levinson–Durbin) the recursion
becomes unstable, which cannot happen for a positive-definite autocorrelation. They work in the scratch and write
their outputs only when they succeed, so the outputs are unchanged when they throw. `Wiener.fir` is built on
`Toeplitz.solve`.

## Accuracy, allocation and threading

**Accuracy.**

- FFT twiddle factors are computed exactly (argument reduction within about one ulp) and never by recurrence, so
  the error grows only with `log n`. The measured relative RMS error against an exact DFT is below 6e-16 for
  `ComplexFftd` and below 2.5e-7 for `ComplexFftf`, for every size tested. The DCT and MDCT plans also use exactly
  reduced twiddles, so their error also grows only with `log n`.
- `BlockConvolverd` on its FFT path has an error of about `1e-16·||x||·||h||` (about `1e-7·||x||·||h||` for
  `BlockConvolverf`); the direct path is accurate to a few ulps of each sum of products.
- Windows use exact argument reduction: symmetric windows are bit-exactly symmetric and values are within a few
  ulps. Float windows are computed in double and rounded once.
- `BiquadDesign` avoids the cancellation of the textbook formulas at low frequencies. `Biquad` runs in double for
  float samples too.
- `Goertzel` switches to Reinsch's recurrences near DC and Nyquist, where the classic algorithm loses accuracy;
  there it is about 10⁵ times more accurate than the classic form on 20,000 samples.
- Daubechies filters are computed (not tabulated) and orthonormal to about 1e-15.

**Allocation.** Creating a plan or a wavelet allocates. Processing calls take your scratch and state and do not
allocate. Exceptions: `Windows.chebyshev` allocates; treat it, `FirDesign.remez` and `Smoothers.savitzkyGolay` as
setup code and run them once. `Wavelet.lowpass` and `Wavelet.highpass` return new arrays.

**Threading.** Plans and wavelets are immutable and can be shared by any number of threads. All methods are static
or read only the plan, keep no hidden state and do not retain your arrays. Each thread needs its own scratch, and
each stream its own state. Kernels are single-threaded: to use several cores, give threads disjoint channels or
signal ranges, and never let two threads write overlapping output.

**Reproducibility.** Results are deterministic and do not depend on the thread count. `Biquad` and `Resampler`
give the same bits however you split a stream into blocks. The FFT twiddles depend on the platform's `cos` and
`sin` unless you run with `-Dvectrix.strictMath=true`; with it, the FFT plans give the same bits on every platform.
The `-simd` jar's Vector API kernels give the same bits as the scalar ones. See [determinism](../determinism.md).

## See also

- [Conventions](../conventions.md): type suffixes, array APIs, scratch and plans, row-major matrices in `dsp`,
  exceptions, determinism.
- [State estimation](state-estimation.md): `Kalman`, `ExtendedKalman`, `UnscentedKalman`, `ParticleFilter`.
- [Package map](../package-map.md)
- [Determinism](../determinism.md)
- Example: [SignalProcessing.java](../../examples/src/main/java/org/dynamisengine/vectrix/examples/SignalProcessing.java)
  (Welch spectrum, Kaiser FIR, Butterworth IIR) and
  [KalmanTracking.java](../../examples/src/main/java/org/dynamisengine/vectrix/examples/KalmanTracking.java).
- Sources: [fft](../../src/main/java/org/dynamisengine/vectrix/fft/),
  [dsp](../../src/main/java/org/dynamisengine/vectrix/dsp/)
