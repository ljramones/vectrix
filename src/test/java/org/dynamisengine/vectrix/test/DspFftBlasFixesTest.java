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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.blas.Gemmd;
import org.dynamisengine.vectrix.blas.Gemmf;
import org.dynamisengine.vectrix.dsp.Biquad;
import org.dynamisengine.vectrix.dsp.BiquadDesign;
import org.dynamisengine.vectrix.dsp.ExtendedKalman;
import org.dynamisengine.vectrix.dsp.FirDesign;
import org.dynamisengine.vectrix.dsp.Kalman;
import org.dynamisengine.vectrix.dsp.Smoothers;
import org.dynamisengine.vectrix.dsp.Spectra;
import org.dynamisengine.vectrix.dsp.Toeplitz;
import org.dynamisengine.vectrix.dsp.UnscentedKalman;
import org.dynamisengine.vectrix.dsp.VectorFunction;
import org.dynamisengine.vectrix.dsp.Wavelet;
import org.dynamisengine.vectrix.dsp.Wiener;
import org.dynamisengine.vectrix.fft.ComplexFft2d;
import org.dynamisengine.vectrix.fft.ComplexFft2f;
import org.dynamisengine.vectrix.fft.RealFft2d;
import org.dynamisengine.vectrix.fft.RealFft2f;
import org.dynamisengine.vectrix.fft.RealFftd;
import org.junit.jupiter.api.Test;

/** The 3.0 fixes in dsp, fft and blas: up-front validation, Kalman gating and Joseph forms, 2D inverse contracts. */
class DspFftBlasFixesTest {

    private static final double SENTINEL = 12345.0;

    private static double[] sentinel(int n) {
        double[] a = new double[n];
        Arrays.fill(a, SENTINEL);
        return a;
    }

    private static void assertUntouched(double[] a, String what) {
        for (double v : a) {
            assertEquals(SENTINEL, v, what + " was written");
        }
    }

    // ---- Spectra ----

    @Test
    void spectraChecksWindowAndScratchBeforeZeroingThePsd() {
        RealFftd plan = RealFftd.plan(64);
        double[] x = new double[256];
        double[] psd = sentinel(33);
        double[] scratch = new double[Spectra.scratchSize(plan)];
        double[] shortWindow = new double[63];
        double[] shortScratch = new double[Spectra.scratchSize(plan) - 1];
        assertThrows(IllegalArgumentException.class,
            () -> Spectra.periodogram(plan, shortWindow, 0, x, 0, psd, 0, scratch, 0));
        assertThrows(IllegalArgumentException.class,
            () -> Spectra.periodogram(plan, null, 0, x, 0, psd, 0, shortScratch, 0));
        assertThrows(IllegalArgumentException.class,
            () -> Spectra.welch(plan, shortWindow, 0, 32, x, 0, 256, psd, 0, scratch, 0));
        assertThrows(IllegalArgumentException.class,
            () -> Spectra.welch(plan, null, 0, 32, x, 0, 256, psd, 0, shortScratch, 0));
        assertThrows(IllegalArgumentException.class,
            () -> Spectra.welch(plan, null, 0, 32, x, 0, 256, psd, 0, scratch, -1));
        assertUntouched(psd, "psd");
        assertEquals(7, Spectra.welch(plan, null, 0, 32, x, 0, 256, psd, 0, scratch, 0));
    }

    // ---- FirDesign and Biquad ----

    @Test
    void firDesignWritesNoTapWhenTheDesignHasNoGain() {
        double[] zeroWindow = new double[15];
        double[] h = sentinel(15);
        assertThrows(IllegalArgumentException.class, () -> FirDesign.lowpass(h, 0, 15, 0.2, zeroWindow, 0));
        assertThrows(IllegalArgumentException.class, () -> FirDesign.highpass(h, 0, 15, 0.2, zeroWindow, 0));
        assertThrows(IllegalArgumentException.class, () -> FirDesign.bandpass(h, 0, 15, 0.1, 0.3, zeroWindow, 0));
        assertThrows(IllegalArgumentException.class, () -> FirDesign.bandstop(h, 0, 15, 0.1, 0.3, zeroWindow, 0));
        assertUntouched(h, "h");
    }

    @Test
    void firDesignIsUnchangedAndAcceptsTheWindowInTheOutputArray() {
        double[] w = new double[31];
        org.dynamisengine.vectrix.dsp.Windows.hann(w, 0, 31, false);
        double[] h = new double[31];
        FirDesign.lowpass(h, 0, 31, 0.2, w, 0);
        // the previous implementation: write the windowed taps, then divide by the magnitude at DC
        double[] ref = new double[31];
        for (int k = 0; k < 31; k++) {
            double t = k - 15;
            double sinc = t == 0 ? 0.4 : org.dynamisengine.vectrix.core.AccurateMath.sin(
                2 * Math.PI * (0.2 * t - Math.rint(0.2 * t))) / (Math.PI * t);
            ref[k] = sinc * w[k];
        }
        double g = FirDesign.magnitude(ref, 0, 31, 0.0);
        for (int k = 0; k < 31; k++) {
            ref[k] /= g;
        }
        assertArrayEquals(ref, h, "bit-identical to writing then normalizing");
        // window before, at and after the taps in the same array
        for (int wOff : new int[] {0, 5, 40}) {
            for (int hOff : new int[] {0, 5, 40}) {
                double[] shared = new double[80];
                System.arraycopy(w, 0, shared, wOff, 31);
                FirDesign.lowpass(shared, hOff, 31, 0.2, shared, wOff);
                assertArrayEquals(h, Arrays.copyOfRange(shared, hOff, hOff + 31), wOff + " -> " + hOff);
            }
        }
    }

    @Test
    void kaiserFormulasRejectBadAttenuation() {
        for (double a : new double[] {0, -3, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> FirDesign.kaiserBeta(a), "beta " + a);
            assertThrows(IllegalArgumentException.class, () -> FirDesign.kaiserTaps(a, 0.1), "taps " + a);
        }
        assertThrows(IllegalArgumentException.class, () -> FirDesign.kaiserTaps(1e300, 1e-300));
        assertEquals(0.0, FirDesign.kaiserBeta(10));
        assertTrue(FirDesign.kaiserTaps(60, 0.05) > 1);
    }

    @Test
    void responsesCheckTheirDestination() {
        double[] h = {0.25, 0.5, 0.25};
        double[] one = sentinel(1);
        assertThrows(IllegalArgumentException.class, () -> FirDesign.response(h, 0, 3, 0.1, one));
        assertThrows(IllegalArgumentException.class, () -> FirDesign.response(h, 0, 3, 0.1, null));
        double[] bq = new double[BiquadDesign.STRIDE];
        int sec = BiquadDesign.butterworthLowpass(bq, 0, 2, 0.1);
        assertThrows(IllegalArgumentException.class, () -> Biquad.response(bq, 0, sec, 0.1, one));
        assertThrows(IllegalArgumentException.class, () -> Biquad.response(bq, 0, sec, 0.1, null));
        assertThrows(IllegalArgumentException.class, () -> Biquad.magnitude(bq, 0, Integer.MAX_VALUE, 0.1));
        assertUntouched(one, "dest");
        double[] dest = new double[2];
        Biquad.response(bq, 0, sec, 0.0, dest);
        assertEquals(1.0, dest[0], 1e-12);
    }

    // ---- Toeplitz ----

    @Test
    void levinsonDurbinChecksReflectionAndWritesNothingOnFailure() {
        int p = 4;
        double[] r = {1, 0.5, 0.2, 0.1, 0.05};
        double[] scratch = new double[Toeplitz.levinsonDurbinScratchSize(p)];
        assertEquals(2 * p + 1, scratch.length);
        double[] a = sentinel(p + 1);
        double[] shortRefl = sentinel(p - 1);
        assertThrows(IllegalArgumentException.class, () -> Toeplitz.levinsonDurbin(r, p, a, shortRefl, scratch));
        assertThrows(IllegalArgumentException.class,
            () -> Toeplitz.levinsonDurbin(r, p, a, null, new double[2 * p]));
        double[] unstable = {1, 2, 0, 0, 0}; // |k1| = 2
        double[] refl = sentinel(p);
        assertThrows(IllegalArgumentException.class, () -> Toeplitz.levinsonDurbin(unstable, p, a, refl, scratch));
        double[] lateUnstable = {1, 0.9, 0.0, 0.9, 0}; // fails at a later order
        assertThrows(IllegalArgumentException.class,
            () -> Toeplitz.levinsonDurbin(lateUnstable, p, a, refl, scratch));
        assertUntouched(a, "a");
        assertUntouched(refl, "reflection");
        assertThrows(IllegalArgumentException.class, () -> Toeplitz.levinsonDurbinScratchSize(-1));
        assertThrows(IllegalArgumentException.class, () -> Toeplitz.solveScratchSize(0));
    }

    @Test
    void levinsonDurbinMatchesThePreviousRecursionAndMayOverwriteR() {
        int p = 6;
        double[] r = {2.0, 1.3, 0.7, 0.2, -0.1, -0.15, -0.05};
        double[] a = new double[p + 1];
        double[] k = new double[p];
        double err = Toeplitz.levinsonDurbin(r, p, a, k, new double[2 * p + 1]);
        // the previous implementation, which updated a through a copy
        double[] ra = new double[p + 1];
        double[] rk = new double[p];
        double[] copy = new double[p + 1];
        ra[0] = 1;
        double rerr = r[0];
        for (int i = 1; i <= p; i++) {
            double acc = r[i];
            for (int j = 1; j < i; j++) {
                acc = Math.fma(ra[j], r[i - j], acc);
            }
            double ki = -acc / rerr;
            rk[i - 1] = ki;
            System.arraycopy(ra, 0, copy, 0, i);
            for (int j = 1; j < i; j++) {
                ra[j] = Math.fma(ki, copy[i - j], copy[j]);
            }
            ra[i] = ki;
            rerr *= (1 - ki) * (1 + ki);
        }
        assertArrayEquals(ra, a);
        assertArrayEquals(rk, k);
        assertEquals(rerr, err);
        double[] alias = r.clone();
        assertEquals(err, Toeplitz.levinsonDurbin(alias, p, alias, null, new double[2 * p + 1]));
        assertArrayEquals(a, alias);
    }

    @Test
    void toeplitzSolveWritesNothingWhenSingular() {
        double[] r = {1, 1, 1, 0.5}; // the leading 2x2 block is singular
        double[] b = {1, 2, 3, 4};
        double[] x = sentinel(4);
        assertThrows(IllegalArgumentException.class, () -> Toeplitz.solve(r, b, 4, x, new double[8]));
        assertThrows(IllegalArgumentException.class, () -> Toeplitz.solve(r, b, 4, x, new double[7]));
        assertThrows(IllegalArgumentException.class, () -> Toeplitz.solve(r, b, 4, null, new double[8]));
        assertUntouched(x, "x");
        // a regular system, with x aliasing b
        double[] rr = {4, 1, 0.5, 0.25};
        double[] bb = {1, -2, 3, 0.5};
        double[] xs = new double[4];
        Toeplitz.solve(rr, bb, 4, xs, new double[Toeplitz.solveScratchSize(4)]);
        for (int i = 0; i < 4; i++) {
            double s = 0;
            for (int j = 0; j < 4; j++) {
                s += rr[Math.abs(i - j)] * xs[j];
            }
            assertEquals(bb[i], s, 1e-14);
        }
        double[] alias = bb.clone();
        Toeplitz.solve(rr, alias, 4, alias, new double[8]);
        assertArrayEquals(xs, alias);
    }

    // ---- Wavelet, Smoothers, Wiener ----

    @Test
    void waveletMultilevelChecksArraysUpFront() {
        Wavelet w = Wavelet.daubechies(2);
        double[] x = new double[64];
        double[] out = sentinel(64);
        double[] shortScratch = new double[63];
        assertThrows(IllegalArgumentException.class, () -> w.forward(x, 0, 64, 3, out, 0, shortScratch, 0));
        assertThrows(IllegalArgumentException.class, () -> w.forward(x, 1, 64, 3, out, 0, new double[64], 0));
        assertThrows(IllegalArgumentException.class, () -> w.forward(x, 0, 64, 3, out, 0, new double[64], -1));
        assertThrows(IllegalArgumentException.class, () -> w.forward(x, 0, 64, 32, out, 0, new double[64], 0));
        assertThrows(IllegalArgumentException.class, () -> w.inverse(x, 0, 64, 3, out, 0, shortScratch, 0));
        assertThrows(IllegalArgumentException.class, () -> w.inverse(x, 0, 64, 3, out, 1, new double[64], 0));
        assertThrows(IllegalArgumentException.class, () -> w.inverse(x, 0, 64, 33, out, 0, new double[64], 0));
        assertUntouched(out, "out");
    }

    @Test
    void waveletFromLowpassChecksOrthonormality() {
        assertThrows(IllegalArgumentException.class, () -> Wavelet.fromLowpass(new double[] {1, 1}));
        assertThrows(IllegalArgumentException.class, () -> Wavelet.fromLowpass(new double[] {0.5, 0.5, 0.5, 0.5}));
        assertThrows(IllegalArgumentException.class, () -> Wavelet.fromLowpass(new double[] {Double.NaN, 0}));
        for (int p = 1; p <= 10; p++) {
            Wavelet.fromLowpass(Wavelet.daubechies(p).lowpass());
        }
        double s = Math.sqrt(0.5);
        Wavelet haar = Wavelet.fromLowpass(new double[] {s, s});
        double[] x = {3, 1, 4, 1};
        double[] c = new double[4];
        double[] back = new double[4];
        haar.forward(x, 0, 4, c, 0);
        haar.inverse(c, 0, 4, back, 0);
        assertArrayEquals(x, back, 1e-15);
    }

    @Test
    void medianChecksCountsAndOffsets() {
        float[] x = new float[10];
        float[] y = new float[10];
        float[] s = new float[3];
        assertThrows(IllegalArgumentException.class, () -> Smoothers.median(x, 0, -1, 1, y, 0, s));
        assertThrows(IllegalArgumentException.class, () -> Smoothers.median(x, -1, 5, 1, y, 0, s));
        assertThrows(IllegalArgumentException.class, () -> Smoothers.median(x, 0, 5, 1, y, -1, s));
        assertThrows(IllegalArgumentException.class, () -> Smoothers.median(x, 6, 5, 1, y, 0, s));
        assertThrows(IllegalArgumentException.class, () -> Smoothers.median(x, 0, 5, Integer.MAX_VALUE, y, 0, s));
        assertThrows(IllegalArgumentException.class, () -> Smoothers.median(null, 0, 5, 1, y, 0, s));
        Smoothers.median(x, 0, 0, 1, y, 0, s); // nothing to do
        float[] spike = {1, 1, 9, 1, 1};
        float[] out = new float[5];
        Smoothers.median(spike, 0, 5, 1, out, 0, s);
        assertArrayEquals(new float[] {1, 1, 1, 1, 1}, out);
    }

    @Test
    void wienerDeconvolutionChecksOffsetsAndRatios() {
        RealFftd plan = RealFftd.plan(32);
        double[] y = new double[32];
        double[] k = {0.6, 0.3, 0.1};
        double[] out = sentinel(32);
        double[] scr = new double[Wiener.deconvolveScratchSize(plan)];
        assertThrows(IllegalArgumentException.class,
            () -> Wiener.deconvolve(plan, y, 1, k, 0, 3, 0.1, out, 0, scr, 0));
        assertThrows(IllegalArgumentException.class,
            () -> Wiener.deconvolve(plan, y, 0, k, 1, 3, 0.1, out, 0, scr, 0));
        assertThrows(IllegalArgumentException.class,
            () -> Wiener.deconvolve(plan, y, 0, k, 0, 3, 0.1, out, 1, scr, 0));
        assertThrows(IllegalArgumentException.class,
            () -> Wiener.deconvolve(plan, y, 0, k, 0, 3, 0.1, out, 0, scr, -1));
        assertThrows(IllegalArgumentException.class,
            () -> Wiener.deconvolve(plan, y, 0, k, 0, 3, -0.1, out, 0, scr, 0));
        assertThrows(IllegalArgumentException.class,
            () -> Wiener.deconvolve(plan, y, 0, k, 0, 3, Double.NaN, out, 0, scr, 0));
        assertThrows(IllegalArgumentException.class,
            () -> Wiener.deconvolve(plan, y, 0, k, 0, 3, new double[16], out, 0, scr, 0));
        assertThrows(IllegalArgumentException.class,
            () -> Wiener.deconvolve(plan, y, 0, k, 0, 3, (double[]) null, out, 0, scr, 0));
        double[] negBin = new double[17];
        negBin[5] = -1;
        assertThrows(IllegalArgumentException.class,
            () -> Wiener.deconvolve(plan, y, 0, k, 0, 3, negBin, out, 0, scr, 0));
        RealFft2d plan2 = RealFft2d.plan(8, 8);
        double[] img = new double[64];
        double[] out2 = sentinel(64);
        double[] scr2 = new double[Wiener.deconvolve2dScratchSize(plan2)];
        assertThrows(IllegalArgumentException.class,
            () -> Wiener.deconvolve2d(plan2, img, 1, k, 0, 1, 3, 0.1, out2, 0, scr2, 0));
        assertThrows(IllegalArgumentException.class,
            () -> Wiener.deconvolve2d(plan2, img, 0, k, 1, 1, 3, 0.1, out2, 0, scr2, 0));
        assertThrows(IllegalArgumentException.class,
            () -> Wiener.deconvolve2d(plan2, img, 0, k, 0, 1, 3, 0.1, out2, 1, scr2, 0));
        assertThrows(IllegalArgumentException.class,
            () -> Wiener.deconvolve2d(plan2, img, 0, k, 0, 1, 3, -1, out2, 0, scr2, 0));
        assertUntouched(out, "out");
        assertUntouched(out2, "out2");
        double[] g = sentinel(4);
        assertThrows(IllegalArgumentException.class, () -> Wiener.gain(new double[3], new double[4], 4, 0, g));
        assertThrows(IllegalArgumentException.class, () -> Wiener.apply(new double[7], 0, new double[4], 4));
        assertThrows(IllegalArgumentException.class, () -> Wiener.noncausal(new double[4], new double[7], 4, g));
        assertUntouched(g, "gain");
    }

    // ---- Kalman filters ----

    private static final double[] F = {1, 0.1, 0, 1};
    private static final double[] Q = {1e-4, 0, 0, 1e-4};
    private static final double[] H = {1, 0};
    private static final double[] R = {0.25};

    @Test
    void innovationDistanceEqualsWhatUpdateReturnsWithoutUpdating() {
        double[] x = {0.3, 1.1};
        double[] p = {2, 0.3, 0.3, 1};
        double[] z = {1.7};
        double[] scr = new double[Kalman.scratchSize(2, 1)];
        double[] x0 = x.clone();
        double[] p0 = p.clone();
        double d = Kalman.innovationDistance(2, 1, x, p, H, R, z, scr);
        assertArrayEquals(x0, x);
        assertArrayEquals(p0, p);
        assertEquals(d, Kalman.update(2, 1, x, p, H, R, z, scr));

        ExtendedKalman ekf = linearEkf(null);
        double[] xe = x0.clone();
        double[] pe = p0.clone();
        double de = ekf.innovationDistance(xe, pe, R, z);
        assertArrayEquals(x0, xe);
        assertArrayEquals(p0, pe);
        assertEquals(de, ekf.update(xe, pe, R, z));
        assertEquals(d, de, 1e-14);

        UnscentedKalman ukf = new UnscentedKalman(2, 1, (in, out) -> {
            out[0] = in[0] + 0.1 * in[1];
            out[1] = in[1];
        }, (in, out) -> out[0] = in[0], 1e-3, 2, 0);
        double[] xu = x0.clone();
        double[] pu = p0.clone();
        double du = ukf.innovationDistance(xu, pu, R, z);
        assertArrayEquals(x0, xu);
        assertArrayEquals(p0, pu);
        assertEquals(du, ukf.update(xu, pu, R, z));
        assertEquals(d, du, 1e-9);

        assertThrows(IllegalStateException.class,
            () -> Kalman.innovationDistance(2, 1, x0, p0, H, new double[] {-10}, z, scr));
        assertThrows(IllegalArgumentException.class,
            () -> Kalman.innovationDistance(2, 1, x0, p0, H, R, new double[0], scr));
    }

    private static ExtendedKalman linearEkf(org.dynamisengine.vectrix.dsp.InnovationFunction innovation) {
        return new ExtendedKalman(2, 1, (in, out) -> {
            out[0] = in[0] + 0.1 * in[1];
            out[1] = in[1];
        }, (in, out) -> System.arraycopy(F, 0, out, 0, 4), (in, out) -> out[0] = in[0],
            (in, out) -> System.arraycopy(H, 0, out, 0, 2), innovation);
    }

    @Test
    void extendedKalmanWrapsAngleInnovationsThroughTheHook() {
        double[] p = {0.01, 0, 0, 0.01};
        double[] r = {0.01};
        double[] z = {-Math.PI + 0.01}; // just across the cut from the predicted angle
        ExtendedKalman plain = linearEkf(null);
        ExtendedKalman wrapped = linearEkf((meas, pred, y) -> y[0] = Math.IEEEremainder(meas[0] - pred[0],
            2 * Math.PI));
        double[] x = {Math.PI - 0.01, 0};
        assertTrue(plain.innovationDistance(x.clone(), p.clone(), r, z) > 1000);
        double d = wrapped.innovationDistance(x.clone(), p.clone(), r, z);
        assertEquals(0.02 * 0.02 / 0.02, d, 1e-9);
        double[] xw = x.clone();
        double[] pw = p.clone();
        assertEquals(d, wrapped.update(xw, pw, r, z));
        assertEquals(Math.PI, xw[0], 1e-9); // moved half way across the cut, not back across the circle
    }

    @Test
    void estimatorsCheckArraysUpFront() {
        ExtendedKalman ekf = linearEkf(null);
        double[] x = sentinel(2);
        double[] p = sentinel(4);
        assertThrows(IllegalArgumentException.class, () -> ekf.predict(x, p, new double[3]));
        assertThrows(IllegalArgumentException.class, () -> ekf.update(x, p, R, new double[0]));
        assertThrows(IllegalArgumentException.class, () -> ekf.update(x, new double[3], R, new double[1]));
        UnscentedKalman ukf = new UnscentedKalman(2, 1, (in, out) -> System.arraycopy(in, 0, out, 0, 2),
            (in, out) -> out[0] = in[0], 1e-3, 2, 0);
        assertThrows(IllegalArgumentException.class, () -> ukf.predict(x, p, new double[3]));
        assertThrows(IllegalArgumentException.class, () -> ukf.update(x, p, new double[0], new double[1]));
        assertUntouched(x, "x");
        assertUntouched(p, "p");
        double[] scr = new double[Kalman.scratchSize(2, 1)];
        assertThrows(IllegalArgumentException.class, () -> Kalman.predict(2, x, p, F, new double[3], scr));
        assertUntouched(x, "x");
        assertThrows(IllegalArgumentException.class, () -> Kalman.scratchSize(0, 1));
        assertThrows(IllegalArgumentException.class, () -> Kalman.scratchSize(1, -1));
        assertThrows(IllegalArgumentException.class, () -> Kalman.scratchSize(50_000, 1));
    }

    @Test
    void unscentedKalmanValidatesItsParametersAndNamesTheFailingCovariance() {
        VectorFunction id = (in, out) -> System.arraycopy(in, 0, out, 0, 2);
        VectorFunction first = (in, out) -> out[0] = in[0];
        assertThrows(IllegalArgumentException.class, () -> new UnscentedKalman(2, 1, id, first, 1e-3, 2, -2));
        assertThrows(IllegalArgumentException.class, () -> new UnscentedKalman(2, 1, id, first, 1e-3, 2, -3));
        assertThrows(IllegalArgumentException.class,
            () -> new UnscentedKalman(2, 1, id, first, 1e-3, 2, Double.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> new UnscentedKalman(2, 1, id, first, Double.POSITIVE_INFINITY, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new UnscentedKalman(2, 1, null, first, 1e-3, 2, 0));
        new UnscentedKalman(2, 1, id, first, 1e-3, 2, -1.5); // n + kappa = 0.5 is fine
        UnscentedKalman ukf = new UnscentedKalman(2, 1, id, first, 1e-3, 2, 0);
        double[] x = {1, 2};
        double[] badP = {1, 0, 0, -1};
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ukf.predict(x, badP, Q));
        assertTrue(e.getMessage().contains("state covariance"), e.getMessage());
        e = assertThrows(IllegalStateException.class, () -> ukf.update(x, badP, R, new double[] {1}));
        assertTrue(e.getMessage().contains("state covariance"), e.getMessage());
        double[] p = {1, 0, 0, 1};
        e = assertThrows(IllegalStateException.class, () -> ukf.update(x, p, new double[] {-5}, new double[] {1}));
        assertTrue(e.getMessage().contains("innovation"), e.getMessage());
        assertArrayEquals(new double[] {1, 2}, x);
        assertArrayEquals(new double[] {1, 0, 0, 1}, p);
    }

    /** Range and bearing from the origin to a 2D position. */
    private static final VectorFunction RANGE_BEARING = (in, out) -> {
        out[0] = Math.hypot(in[0], in[1]);
        out[1] = Math.atan2(in[1], in[0]);
    };

    @Test
    void unscentedUpdateEqualsTheUnscentedCovarianceUpdate() {
        double[] alphas = {1e-3, 0.5, 1};
        for (double alpha : alphas) {
            UnscentedKalman ukf = new UnscentedKalman(2, 2, (in, out) -> System.arraycopy(in, 0, out, 0, 2),
                RANGE_BEARING, alpha, 2, 0);
            double[] x = {3, 4};
            double[] p = {0.5, 0.1, 0.1, 0.3};
            double[] r = {0.01, 0, 0, 0.001};
            double[] z = {5.2, 0.95};
            double[] ref = referenceUkfUpdate(x, p, r, z, alpha, 2, 0);
            ukf.update(x, p, r, z);
            // a small alpha gives a centre weight near -1e6: both sides cancel to about 1e-10
            double tol = alpha < 0.1 ? 1e-8 : 1e-12;
            for (int i = 0; i < 2; i++) {
                assertEquals(ref[i], x[i], tol, "x, alpha " + alpha);
            }
            for (int i = 0; i < 4; i++) {
                assertEquals(ref[2 + i], p[i], tol, "P, alpha " + alpha);
            }
        }
    }

    @Test
    void unscentedUpdateKeepsPPositiveSemiDefinite() {
        // a very precise measurement of a very uncertain state: P - K·S·Kᵀ cancels completely, and the previous
        // form gave a negative variance or determinant in every one of these cases
        for (double alpha : new double[] {1, 1e-3}) {
            UnscentedKalman ukf = new UnscentedKalman(2, 1, (in, out) -> System.arraycopy(in, 0, out, 0, 2),
                (in, out) -> out[0] = in[0], alpha, 2, 0);
            SplittableRandom rnd = new SplittableRandom(7);
            for (int t = 0; t < 200; t++) {
                double[] x = {rnd.nextDouble(), rnd.nextDouble()};
                double c = rnd.nextDouble(-0.999, 0.999);
                double[] p = {1e8, c * 1e8, c * 1e8, 1e8};
                ukf.update(x, p, new double[] {1e-12}, new double[] {rnd.nextDouble()});
                assertEquals(p[1], p[2], "symmetric");
                assertTrue(p[0] >= 0 && p[3] >= 0, "diagonal " + p[0] + ", " + p[3]);
                assertTrue(p[0] * p[3] - p[1] * p[2] >= 0, "determinant, alpha " + alpha + ", step " + t);
            }
        }
    }

    /** The textbook scaled UKF update {@code x += K·(z - z̄)}, {@code P -= K·S·Kᵀ}, written out for n = m = 2. */
    private static double[] referenceUkfUpdate(double[] x, double[] p, double[] r, double[] z, double alpha,
            double beta, double kappa) {
        int n = 2;
        double lambda = alpha * alpha * (n + kappa) - n;
        double c = n + lambda;
        double l00 = Math.sqrt(c * p[0]);
        double l10 = c * p[2] / l00;
        double l11 = Math.sqrt(c * p[3] - l10 * l10);
        double[][] sig = {{x[0], x[1]}, {x[0] + l00, x[1] + l10}, {x[0], x[1] + l11}, {x[0] - l00, x[1] - l10},
            {x[0], x[1] - l11}};
        double[] wm = new double[5];
        double[] wc = new double[5];
        Arrays.fill(wm, 1 / (2 * c));
        Arrays.fill(wc, 1 / (2 * c));
        wm[0] = lambda / c;
        wc[0] = wm[0] + 1 - alpha * alpha + beta;
        double[][] zs = new double[5][2];
        double[] zbar = new double[2];
        for (int i = 0; i < 5; i++) {
            RANGE_BEARING.apply(sig[i], zs[i]);
            zbar[0] += wm[i] * zs[i][0];
            zbar[1] += wm[i] * zs[i][1];
        }
        double[] s = r.clone();
        double[] pxz = new double[4];
        for (int i = 0; i < 5; i++) {
            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    s[a * 2 + b] += wc[i] * (zs[i][a] - zbar[a]) * (zs[i][b] - zbar[b]);
                    pxz[a * 2 + b] += wc[i] * (sig[i][a] - x[a]) * (zs[i][b] - zbar[b]);
                }
            }
        }
        double det = s[0] * s[3] - s[1] * s[2];
        double[] si = {s[3] / det, -s[1] / det, -s[2] / det, s[0] / det};
        double[] k = new double[4];
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                k[a * 2 + b] = pxz[a * 2] * si[b] + pxz[a * 2 + 1] * si[2 + b];
            }
        }
        double y0 = z[0] - zbar[0];
        double y1 = z[1] - zbar[1];
        double[] out = new double[6];
        out[0] = x[0] + k[0] * y0 + k[1] * y1;
        out[1] = x[1] + k[2] * y0 + k[3] * y1;
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                double ksk = 0;
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        ksk += k[a * 2 + i] * s[i * 2 + j] * k[b * 2 + j];
                    }
                }
                out[2 + a * 2 + b] = p[a * 2 + b] - ksk;
            }
        }
        return out;
    }

    // ---- 2D FFT ----

    @Test
    void complexInverse2dDividesOnceByRowsTimesCols() {
        SplittableRandom rnd = new SplittableRandom(3);
        for (int[] size : new int[][] {{3, 5}, {6, 10}, {7, 1}, {1, 9}, {12, 9}}) {
            int rows = size[0];
            int cols = size[1];
            ComplexFft2d plan = ComplexFft2d.plan(rows, cols);
            double[] spec = new double[2 * rows * cols];
            for (int i = 0; i < spec.length; i++) {
                spec[i] = rnd.nextDouble(-1, 1);
            }
            double[] scratch = new double[plan.scratchSize()];
            // reference: the unscaled inverse is conj(forward(conj(X))); then one division
            double[] ref = spec.clone();
            for (int i = 1; i < ref.length; i += 2) {
                ref[i] = -ref[i];
            }
            plan.forward(ref, 0, scratch, 0);
            for (int i = 0; i < ref.length; i++) {
                ref[i] = ((i & 1) == 0 ? ref[i] : -ref[i]) / ((double) rows * cols);
            }
            double[] got = spec.clone();
            plan.inverse(got, 0, scratch, 0);
            assertArrayEquals(ref, got, rows + "x" + cols);

            ComplexFft2f planf = ComplexFft2f.plan(rows, cols);
            float[] specf = new float[spec.length];
            for (int i = 0; i < spec.length; i++) {
                specf[i] = (float) spec[i];
            }
            float[] scratchf = new float[planf.scratchSize()];
            float[] reff = specf.clone();
            for (int i = 1; i < reff.length; i += 2) {
                reff[i] = -reff[i];
            }
            planf.forward(reff, 0, scratchf, 0);
            for (int i = 0; i < reff.length; i++) {
                reff[i] = (float) (((i & 1) == 0 ? reff[i] : -reff[i]) / ((double) rows * cols));
            }
            planf.inverse(specf, 0, scratchf, 0);
            assertArrayEquals(reff, specf, rows + "x" + cols + " float");
        }
    }

    @Test
    void realInverse2dLeavesItsInputUnchanged() {
        SplittableRandom rnd = new SplittableRandom(5);
        for (int[] size : new int[][] {{1, 1}, {1, 2}, {2, 1}, {3, 5}, {4, 6}, {7, 8}, {16, 33}, {5, 1}, {9, 64},
            {2, 2}, {1, 7}, {64, 40}}) {
            int rows = size[0];
            int cols = size[1];
            RealFft2d plan = RealFft2d.plan(rows, cols);
            double[] x = new double[rows * cols];
            for (int i = 0; i < x.length; i++) {
                x[i] = rnd.nextDouble(-1, 1);
            }
            double[] spec = new double[plan.spectrumLength()];
            double[] scratch = new double[plan.scratchSize()];
            plan.forward(x, 0, spec, 0, scratch, 0);
            double[] specCopy = spec.clone();
            double[] back = new double[x.length + 3];
            plan.inverse(spec, 0, back, 3, scratch, 0);
            assertArrayEquals(specCopy, spec, rows + "x" + cols + ": the spectrum was modified");
            // against the complex 2D inverse of the full spectrum
            ComplexFft2d cplan = ComplexFft2d.plan(rows, cols);
            double[] full = new double[2 * rows * cols];
            for (int i = 0; i < x.length; i++) {
                full[2 * i] = x[i];
            }
            double[] cs = new double[cplan.scratchSize()];
            cplan.forward(full, 0, cs, 0);
            cplan.inverse(full, 0, cs, 0);
            for (int i = 0; i < x.length; i++) {
                assertEquals(x[i], back[3 + i], 1e-14, rows + "x" + cols + " at " + i);
                assertEquals(full[2 * i], back[3 + i], 1e-14, rows + "x" + cols + " complex at " + i);
            }

            RealFft2f planf = RealFft2f.plan(rows, cols);
            float[] xf = new float[x.length];
            for (int i = 0; i < x.length; i++) {
                xf[i] = (float) x[i];
            }
            float[] specf = new float[planf.spectrumLength()];
            float[] scratchf = new float[planf.scratchSize()];
            planf.forward(xf, 0, specf, 0, scratchf, 0);
            float[] specfCopy = specf.clone();
            float[] backf = new float[x.length];
            planf.inverse(specf, 0, backf, 0, scratchf, 0);
            assertArrayEquals(specfCopy, specf, rows + "x" + cols + ": the float spectrum was modified");
            for (int i = 0; i < x.length; i++) {
                assertEquals(xf[i], backf[i], 2e-6f, rows + "x" + cols + " float at " + i);
            }
        }
    }

    // ---- BLAS ----

    @Test
    void gemmScratchSizeRejectsNegativeSizesAndGemvChecksScratchUpFront() {
        assertThrows(IllegalArgumentException.class, () -> Gemmf.scratchSize(false, false, 4, 4, -1));
        assertThrows(IllegalArgumentException.class, () -> Gemmf.scratchSize(true, true, -1, 4, 4));
        assertThrows(IllegalArgumentException.class, () -> Gemmd.scratchSize(false, false, 4, -4, 4));
        float[] a = new float[12];
        float[] x = new float[3];
        float[] y = {7, 7, 7, 7};
        // transposed: A is 3x4, op(A) is 4x3; the scratch needs 3 values even when no row is computed
        assertThrows(IllegalArgumentException.class,
            () -> Gemmf.gemv(true, 4, 3, 1f, a, 0, 4, x, 0, 0f, y, 0, new float[2], 0));
        assertThrows(IllegalArgumentException.class,
            () -> Gemmf.gemv(true, 0, 3, 1f, a, 0, 4, x, 0, 0f, y, 0, null, 0));
        assertArrayEquals(new float[] {7, 7, 7, 7}, y);
        Gemmf.gemv(true, 4, 0, 1f, a, 0, 4, null, 0, 0f, y, 0, null, 0); // n == 0: no scratch, no x
        assertArrayEquals(new float[4], y);
        double[] yd = {7, 7};
        Gemmd.gemv(false, 2, 0, 1, new double[0], 0, 0, null, 0, 0.5, yd, 0, null, 0);
        assertArrayEquals(new double[] {3.5, 3.5}, yd);
    }

    // ---- float overloads: the double computation on the widened inputs, rounded once ----

    private static float[] randomFloats(SplittableRandom rnd, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = (float) rnd.nextDouble(-1, 1);
        }
        return a;
    }

    private static double[] widen(float[] a) {
        double[] d = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            d[i] = a[i];
        }
        return d;
    }

    private static float[] narrow(double[] a) {
        float[] f = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            f[i] = (float) a[i];
        }
        return f;
    }

    @Test
    void correlationFloatOverloadRoundsTheDoubleResult() {
        SplittableRandom rnd = new SplittableRandom(11);
        for (int[] size : new int[][] {{5, 7}, {300, 400}}) { // direct and FFT paths
            org.dynamisengine.vectrix.dsp.Correlation c = org.dynamisengine.vectrix.dsp.Correlation.plan(size[0],
                size[1]);
            float[] x = randomFloats(rnd, size[0] + 2);
            float[] y = randomFloats(rnd, size[1] + 1);
            double[] out = new double[c.outputLength()];
            double[] scratch = new double[c.scratchSize() + 3];
            c.correlate(widen(x), 2, widen(y), 1, out, 0, scratch, 3);
            float[] outf = new float[c.outputLength() + 1];
            c.correlate(x, 2, y, 1, outf, 1, scratch, 3);
            assertArrayEquals(narrow(out), Arrays.copyOfRange(outf, 1, outf.length), "usesFft " + c.usesFft());
            assertThrows(IllegalArgumentException.class, () -> c.correlate(x, 3, y, 1, outf, 1, scratch, 3));
        }
    }

    @Test
    void toeplitzFloatOverloadsRoundTheDoubleResults() {
        float[] r = {2.0f, 1.3f, 0.7f, 0.2f, -0.1f, -0.15f, -0.05f};
        int p = 6;
        double[] a = new double[p + 1];
        double[] k = new double[p];
        double err = Toeplitz.levinsonDurbin(widen(r), p, a, k, new double[2 * p + 1]);
        float[] af = new float[p + 1];
        float[] kf = new float[p];
        assertEquals(err, Toeplitz.levinsonDurbin(r, p, af, kf, new double[2 * p + 1]));
        assertArrayEquals(narrow(a), af);
        assertArrayEquals(narrow(k), kf);
        float[] b = {1, -2, 3, 0.5f, 0.25f, -1, 2};
        double[] x = new double[7];
        Toeplitz.solve(widen(r), widen(b), 7, x, new double[14]);
        float[] xf = new float[7];
        Toeplitz.solve(r, b, 7, xf, new double[14]);
        assertArrayEquals(narrow(x), xf);
        float[] singular = {1, 1, 1};
        float[] untouched = {5, 5, 5};
        assertThrows(IllegalArgumentException.class,
            () -> Toeplitz.solve(singular, new float[] {1, 2, 3}, 3, untouched, new double[6]));
        assertThrows(IllegalArgumentException.class,
            () -> Toeplitz.levinsonDurbin(new float[] {1, 2, 0}, 2, untouched, null, new double[5]));
        assertArrayEquals(new float[] {5, 5, 5}, untouched);
    }

    @Test
    void waveletFloatOverloadsRoundTheDoubleResults() {
        SplittableRandom rnd = new SplittableRandom(13);
        Wavelet w = Wavelet.daubechies(3);
        int n = 64;
        float[] x = randomFloats(rnd, n);
        double[] c = new double[n];
        w.forward(widen(x), 0, n, c, 0);
        float[] cf = new float[n];
        w.forward(x, 0, n, cf, 0);
        assertArrayEquals(narrow(c), cf, "one level");
        double[] back = new double[n];
        w.inverse(widen(cf), 0, n, back, 0);
        float[] backf = new float[n];
        w.inverse(cf, 0, n, backf, 0, new double[n], 0);
        assertArrayEquals(narrow(back), backf, "one level inverse");
        double[] scr = new double[n];
        double[] cm = new double[n];
        w.forward(widen(x), 0, n, 3, cm, 0, scr, 0);
        float[] cmf = x.clone();
        w.forward(cmf, 0, n, 3, cmf, 0, new double[2 * n], 0); // out may be x
        assertArrayEquals(narrow(cm), cmf, "three levels");
        double[] bm = new double[n];
        w.inverse(widen(cmf), 0, n, 3, bm, 0, scr, 0);
        float[] bmf = cmf.clone();
        w.inverse(bmf, 0, n, 3, bmf, 0, new double[2 * n], 0); // x may be coeffs
        assertArrayEquals(narrow(bm), bmf, "three levels inverse");
        for (int i = 0; i < n; i++) {
            assertEquals(x[i], bmf[i], 1e-6f);
        }
        float[] out = new float[n];
        assertThrows(IllegalArgumentException.class, () -> w.forward(x, 0, n, 3, out, 0, new double[2 * n - 1], 0));
        assertThrows(IllegalArgumentException.class, () -> w.inverse(x, 0, n, out, 0, new double[n - 1], 0));
        assertThrows(IllegalArgumentException.class, () -> w.forward(x, 1, n, out, 0));
        assertArrayEquals(new float[n], out);
    }
}
