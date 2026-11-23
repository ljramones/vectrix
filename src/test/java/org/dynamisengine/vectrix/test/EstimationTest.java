/*
 * The MIT License
 *
 * Copyright (c) 2026 Vectrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.dsp.ExtendedKalman;
import org.dynamisengine.vectrix.dsp.Kalman;
import org.dynamisengine.vectrix.dsp.ParticleFilter;
import org.dynamisengine.vectrix.dsp.Spectra;
import org.dynamisengine.vectrix.dsp.UnscentedKalman;
import org.dynamisengine.vectrix.dsp.Wiener;
import org.dynamisengine.vectrix.dsp.Windows;
import org.dynamisengine.vectrix.fft.RealFft2d;
import org.dynamisengine.vectrix.fft.RealFftd;
import org.junit.jupiter.api.Test;

/** Wiener deconvolution and image filtering, the non-causal Wiener filter, EKF, UKF and particle filters (2.5.0). */
class EstimationTest {

    @Test
    void wienerDeconvolutionUndoesAKnownBlur() {
        SplittableRandom r = new SplittableRandom(1);
        int n = 256;
        RealFftd plan = RealFftd.plan(n);
        double[] x = new double[n];
        for (int t = 0; t < n; t++) {
            x[t] = java.lang.Math.sin(0.11 * t) + 0.5 * java.lang.Math.cos(0.37 * t) + 0.2 * r.nextGaussian();
        }
        double[] kernel = {0.6, 0.3, 0.1}; // |H| >= 0.4 everywhere: invertible
        double[] y = new double[n];
        for (int t = 0; t < n; t++) {
            for (int k = 0; k < 3; k++) {
                y[t] += kernel[k] * x[(t - k + n) % n];
            }
        }
        double[] s = new double[Wiener.deconvolveScratchSize(plan)];
        double[] back = new double[n];
        Wiener.deconvolve(plan, y, 0, kernel, 0, 3, 0.0, back, 0, s, 0);
        for (int t = 0; t < n; t++) {
            assertEquals(x[t], back[t], 1e-13, "exact inverse at " + t);
        }
        // with noise and a strong blur, the Wiener estimate with the true per-bin NSR (σ²/|X|²) beats both the
        // blurred observation and plain inverse filtering
        double[] smooth = new double[n];
        for (int t = 0; t < n; t++) {
            smooth[t] = java.lang.Math.sin(2 * java.lang.Math.PI * 3 * t / n) + 0.6 * java.lang.Math.cos(2 * java.lang.Math.PI * 17 * t / n)
                + 0.3 * java.lang.Math.sin(2 * java.lang.Math.PI * 40 * t / n);
        }
        double[] blur = {0.1, 0.2, 0.4, 0.2, 0.1};
        double sigma = 0.02;
        double[] yb = new double[n];
        for (int t = 0; t < n; t++) {
            for (int k = 0; k < 5; k++) {
                yb[t] += blur[k] * smooth[(t - k + n) % n];
            }
            yb[t] += sigma * r.nextGaussian();
        }
        double[] spec = new double[plan.spectrumLength()];
        plan.forward(smooth, 0, spec, 0, new double[plan.scratchSize()], 0);
        double[] nsr = new double[n / 2 + 1];
        for (int k = 0; k <= n / 2; k++) {
            double px = spec[2 * k] * spec[2 * k] + spec[2 * k + 1] * spec[2 * k + 1];
            nsr[k] = n * sigma * sigma / java.lang.Math.max(px, 1e-12); // both as |DFT|² powers
        }
        double[] wiener = new double[n];
        double[] inverse = new double[n];
        Wiener.deconvolve(plan, yb, 0, blur, 0, 5, nsr, wiener, 0, s, 0);
        Wiener.deconvolve(plan, yb, 0, blur, 0, 5, 0.0, inverse, 0, s, 0);
        double eBlur = 0;
        double eWiener = 0;
        double eInverse = 0;
        for (int t = 0; t < n; t++) {
            // compare with the signal delayed by the blur's centre (2 samples)
            double want = smooth[(t - 2 + n) % n];
            eBlur += (yb[t] - want) * (yb[t] - want);
            eWiener += (wiener[t] - smooth[t]) * (wiener[t] - smooth[t]);
            eInverse += (inverse[t] - smooth[t]) * (inverse[t] - smooth[t]);
        }
        assertTrue(eWiener < 0.2 * eBlur, "Wiener " + eWiener + " blurred " + eBlur);
        assertTrue(eWiener < 0.2 * eInverse, "Wiener " + eWiener + " inverse " + eInverse);
    }

    @Test
    void imageDeconvolutionAndAdaptiveFiltering() {
        SplittableRandom r = new SplittableRandom(2);
        int rows = 24;
        int cols = 30;
        double[] img = new double[rows * cols];
        for (int i = 0; i < img.length; i++) {
            img[i] = r.nextDouble();
        }
        double[] k = {0.7, 0.1, 0.1, 0.1}; // 2x2, |H| >= 0.4
        double[] y = new double[rows * cols];
        for (int a = 0; a < rows; a++) {
            for (int b = 0; b < cols; b++) {
                double v = 0;
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        v += k[i * 2 + j] * img[((a - i + rows) % rows) * cols + (b - j + cols) % cols];
                    }
                }
                y[a * cols + b] = v;
            }
        }
        RealFft2d plan = RealFft2d.plan(rows, cols);
        double[] back = new double[rows * cols];
        Wiener.deconvolve2d(plan, y, 0, k, 0, 2, 2, 0.0, back, 0, new double[Wiener.deconvolve2dScratchSize(plan)], 0);
        for (int i = 0; i < img.length; i++) {
            assertEquals(img[i], back[i], 1e-12, "pixel " + i);
        }
        // adaptive filter: matches the direct definition, and smooths a noisy flat image
        double[] noisy = new double[rows * cols];
        for (int i = 0; i < noisy.length; i++) {
            noisy[i] = 0.5 + 0.1 * r.nextGaussian() + (i % cols > cols / 2 ? 0.8 : 0);
        }
        double[] out = new double[rows * cols];
        double nu = Wiener.adaptive2d(noisy, rows, cols, 2, 0.01, out, new double[2 * (rows + 1) * (cols + 1)]);
        assertEquals(0.01, nu, 0.0);
        for (int a = 0; a < rows; a++) {
            for (int b = 0; b < cols; b++) {
                double s1 = 0;
                double s2 = 0;
                int cnt = 0;
                for (int i = java.lang.Math.max(0, a - 2); i <= java.lang.Math.min(rows - 1, a + 2); i++) {
                    for (int j = java.lang.Math.max(0, b - 2); j <= java.lang.Math.min(cols - 1, b + 2); j++) {
                        s1 += noisy[i * cols + j];
                        s2 += noisy[i * cols + j] * noisy[i * cols + j];
                        cnt++;
                    }
                }
                double mean = s1 / cnt;
                double var = java.lang.Math.max(0, s2 / cnt - mean * mean);
                double want = mean + java.lang.Math.max(var - 0.01, 0) / java.lang.Math.max(var, 0.01)
                    * (noisy[a * cols + b] - mean);
                assertEquals(want, out[a * cols + b], 1e-12);
            }
        }
    }

    @Test
    void noncausalWienerIdentifiesATwoSidedFilter() {
        SplittableRandom r = new SplittableRandom(3);
        int len = 400_000;
        double[] x = new double[len];
        for (int t = 0; t < len; t++) {
            x[t] = r.nextGaussian();
        }
        double[] d = new double[len];
        for (int t = 2; t < len - 1; t++) {
            d[t] = 0.5 * x[t + 1] + x[t] - 0.3 * x[t - 2]; // uses a future sample
        }
        int n = 64;
        RealFftd plan = RealFftd.plan(n);
        double[] w = new double[n];
        Windows.hann(w, 0, n, true);
        double[] pxx = new double[n / 2 + 1];
        double[] pxy = new double[n + 2];
        Spectra.welch(plan, w, 0, n / 2, x, 0, len, pxx, 0, new double[Spectra.scratchSize(plan)], 0);
        Spectra.crossWelch(plan, w, 0, n / 2, x, 0, d, 0, len, pxy, 0, new double[Spectra.crossScratchSize(plan)], 0);
        double[] h = new double[n + 2];
        Wiener.noncausal(pxx, pxy, n / 2 + 1, h);
        for (int k = 0; k <= n / 2; k++) {
            double f = (double) k / n;
            double gr = 0.5 * java.lang.Math.cos(2 * java.lang.Math.PI * f) + 1 - 0.3 * java.lang.Math.cos(4 * java.lang.Math.PI * f);
            double gi = 0.5 * java.lang.Math.sin(2 * java.lang.Math.PI * f) + 0.3 * java.lang.Math.sin(4 * java.lang.Math.PI * f);
            assertEquals(gr, h[2 * k], 0.05, "re at bin " + k);
            assertEquals(gi, h[2 * k + 1], 0.05, "im at bin " + k);
        }
        double[] taps = new double[n];
        Wiener.impulseResponse(plan, h, taps, new double[plan.spectrumLength() + plan.scratchSize()]);
        assertEquals(0.5, taps[n / 2 - 1], 0.05);
        assertEquals(1.0, taps[n / 2], 0.05);
        assertEquals(-0.3, taps[n / 2 + 2], 0.05);
        assertEquals(0.0, taps[n / 2 + 1], 0.05);
    }

    // constant velocity in 1D: state (position, velocity)
    private static final double DT = 0.1;
    private static final double[] F = {1, DT, 0, 1};
    private static final double[] Q = {1e-3, 0, 0, 1e-3};

    @Test
    void extendedAndUnscentedFiltersEqualKalmanOnLinearModels() {
        double[] h = {1, 0};
        double[] rr = {0.25};
        VectorFunction lin = (in, out) -> {
            out[0] = in[0] + DT * in[1];
            out[1] = in[1];
        };
        VectorFunction meas = (in, out) -> out[0] = in[0];
        ExtendedKalman ekf = new ExtendedKalman(2, 1, lin, (in, out) -> System.arraycopy(F, 0, out, 0, 4), meas,
            (in, out) -> System.arraycopy(h, 0, out, 0, 2));
        UnscentedKalman ukf = new UnscentedKalman(2, 1, lin, meas, 1e-3, 2, 0);
        double[] xk = {0, 1};
        double[] pk = {1, 0, 0, 1};
        double[] xe = xk.clone();
        double[] pe = pk.clone();
        double[] xu = xk.clone();
        double[] pu = pk.clone();
        double[] sc = new double[Kalman.scratchSize(2, 1)];
        SplittableRandom r = new SplittableRandom(4);
        for (int t = 0; t < 40; t++) {
            double[] z = {0.1 * t + 0.5 * r.nextGaussian()};
            Kalman.predict(2, xk, pk, F, Q, sc);
            double mk = Kalman.update(2, 1, xk, pk, h, rr, z, sc);
            ekf.predict(xe, pe, Q);
            double me = ekf.update(xe, pe, rr, z);
            ukf.predict(xu, pu, Q);
            double mu = ukf.update(xu, pu, rr, z);
            for (int i = 0; i < 2; i++) {
                assertEquals(xk[i], xe[i], 1e-12, "EKF state");
                assertEquals(xk[i], xu[i], 1e-9, "UKF state");
            }
            for (int i = 0; i < 4; i++) {
                assertEquals(pk[i], pe[i], 1e-12, "EKF covariance");
                assertEquals(pk[i], pu[i], 1e-9, "UKF covariance");
            }
            assertEquals(mk, me, 1e-10);
            assertEquals(mk, mu, 1e-7);
        }
    }

    @Test
    void nonlinearFiltersLocateATargetFromRanges() {
        double[][] beacons = {{0, 0}, {10, 0}, {0, 10}};
        double[] truth = {3, 4};
        VectorFunction stay = (in, out) -> System.arraycopy(in, 0, out, 0, 2);
        VectorFunction ranges = (in, out) -> {
            for (int b = 0; b < 3; b++) {
                out[b] = java.lang.Math.hypot(in[0] - beacons[b][0], in[1] - beacons[b][1]);
            }
        };
        VectorFunction jac = (in, out) -> {
            for (int b = 0; b < 3; b++) {
                double dx = in[0] - beacons[b][0];
                double dy = in[1] - beacons[b][1];
                double rng = java.lang.Math.hypot(dx, dy);
                out[b * 2] = dx / rng;
                out[b * 2 + 1] = dy / rng;
            }
        };
        ExtendedKalman ekf = new ExtendedKalman(2, 3, stay, (in, out) -> {
            out[0] = 1;
            out[1] = 0;
            out[2] = 0;
            out[3] = 1;
        }, ranges, jac);
        UnscentedKalman ukf = new UnscentedKalman(2, 3, stay, ranges, 1, 2, 0);
        double[] q = {1e-6, 0, 0, 1e-6};
        double[] rr = {0.01, 0, 0, 0, 0.01, 0, 0, 0, 0.01};
        double[] xe = {6, 6};
        double[] pe = {9, 0, 0, 9};
        double[] xu = xe.clone();
        double[] pu = pe.clone();
        SplittableRandom r = new SplittableRandom(5);
        double[] z = new double[3];
        double[] exact = new double[3];
        for (int t = 0; t < 50; t++) {
            ranges.apply(truth, exact);
            for (int b = 0; b < 3; b++) {
                z[b] = exact[b] + 0.1 * r.nextGaussian();
            }
            ekf.predict(xe, pe, q);
            ekf.update(xe, pe, rr, z);
            ukf.predict(xu, pu, q);
            ukf.update(xu, pu, rr, z);
        }
        assertEquals(truth[0], xe[0], 0.05, "EKF x");
        assertEquals(truth[1], xe[1], 0.05, "EKF y");
        assertEquals(truth[0], xu[0], 0.05, "UKF x");
        assertEquals(truth[1], xu[1], 0.05, "UKF y");
    }

    @Test
    void particleFilterMatchesKalmanOnALinearGaussianModel() {
        // random walk x' = x + w (q = 0.2), z = x + v (r = 0.5)
        int count = 20_000;
        double q = 0.2;
        double rv = 0.5;
        double[] parts = new double[count];
        double[] next = new double[count];
        double[] logW = new double[count];
        double[] w = new double[count];
        int[] idx = new int[count];
        float[] noise = new float[count];
        double[] xk = {0};
        double[] pk = {1};
        double[] sc = new double[Kalman.scratchSize(1, 1)];
        org.dynamisengine.vectrix.hash.Philox.fillNormal(11, 0, 0f, 1f, noise, 0, count);
        for (int i = 0; i < count; i++) {
            parts[i] = noise[i]; // prior N(0, 1)
        }
        SplittableRandom r = new SplittableRandom(6);
        double truth = 0;
        for (int t = 1; t <= 30; t++) {
            truth += java.lang.Math.sqrt(q) * r.nextGaussian();
            double z = truth + java.lang.Math.sqrt(rv) * r.nextGaussian();
            org.dynamisengine.vectrix.hash.Philox.fillNormal(11, (long) t * count, 0f, 1f, noise, 0, count);
            for (int i = 0; i < count; i++) {
                parts[i] += java.lang.Math.sqrt(q) * noise[i];
                double e = z - parts[i];
                logW[i] = -0.5 * e * e / rv;
            }
            ParticleFilter.normalizeLogWeights(logW, count, w);
            double[] mean = new double[1];
            ParticleFilter.mean(parts, 1, count, w, mean);
            Kalman.predict(1, xk, pk, new double[] {1}, new double[] {q}, sc);
            Kalman.update(1, 1, xk, pk, new double[] {1}, new double[] {rv}, new double[] {z}, sc);
            assertEquals(xk[0], mean[0], 0.05, "posterior mean at step " + t);
            assertTrue(ParticleFilter.effectiveSampleSize(w, count) > 1);
            ParticleFilter.systematicResample(w, count, org.dynamisengine.vectrix.hash.Philox.uniform(12, t), idx);
            ParticleFilter.gather(parts, 1, count, idx, next);
            double[] tmp = parts;
            parts = next;
            next = tmp;
        }
        // resampling counts lie between floor and ceil of count·w
        double[] ww = {0.1, 0.25, 0.05, 0.6};
        int[] ix = new int[4];
        ParticleFilter.systematicResample(ww, 4, 0.37, ix);
        int[] cnt = new int[4];
        for (int i : ix) {
            cnt[i]++;
        }
        for (int i = 0; i < 4; i++) {
            assertTrue(cnt[i] >= java.lang.Math.floor(4 * ww[i]) && cnt[i] <= java.lang.Math.ceil(4 * ww[i]), "count " + i);
        }
        // extreme log-weights do not underflow
        double[] lw = {-1000, -1001};
        double[] nw = new double[2];
        ParticleFilter.normalizeLogWeights(lw, 2, nw);
        assertEquals(1 / (1 + java.lang.Math.exp(-1)), nw[0], 1e-15);
    }

    /** Local alias so the lambdas above read naturally. */
    private interface VectorFunction extends org.dynamisengine.vectrix.dsp.VectorFunction {
    }
}
