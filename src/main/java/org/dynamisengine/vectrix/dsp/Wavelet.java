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
package org.dynamisengine.vectrix.dsp;

/**
 * Orthogonal discrete wavelet transforms with periodic boundaries: Haar and Daubechies wavelets of any order up to
 * 10 vanishing moments.
 * <p>
 * The Daubechies filters are computed, not tabulated: the half-band polynomial
 * {@code P(y) = Σ C(p-1+k, k)·y^k} is factored (its roots by the Durand–Kerner iteration), the minimum-phase root of
 * each factor is kept, and the product with {@code (1 + z)^p} is normalized to {@code Σh = √2}. The result has the
 * conventional ordering ({@code db2} starts {@code (1 + √3)/(4√2)}), is orthonormal to about 1e-15, and has
 * {@code p} vanishing moments. A level of the transform splits {@code n} samples (even) into {@code n/2}
 * approximation and {@code n/2} detail coefficients; the inverse reconstructs exactly up to rounding. A plan is
 * immutable and shareable; transforms do not allocate.
 *
 * @since 2.5.0
 */
public final class Wavelet {

    private final double[] lo;
    private final double[] hi;

    private Wavelet(double[] lo) {
        this.lo = lo;
        int l = lo.length;
        hi = new double[l];
        for (int m = 0; m < l; m++) {
            hi[m] = ((m & 1) == 0 ? 1 : -1) * lo[l - 1 - m];
        }
    }

    /** The Haar wavelet ({@code db1}). */
    public static Wavelet haar() {
        return daubechies(1);
    }

    /**
     * The Daubechies wavelet with {@code p} vanishing moments ({@code 2p} taps).
     *
     * @param p from 1 (Haar) to 10
     */
    public static Wavelet daubechies(int p) {
        if (p < 1 || p > 10) {
            throw new IllegalArgumentException("vanishing moments must be in [1, 10]: " + p);
        }
        // roots of P(y) = Σ_{k<p} C(p-1+k, k) y^k
        int deg = p - 1;
        double[] coef = new double[p];
        double c = 1;
        for (int k = 0; k < p; k++) {
            coef[k] = c; // C(p-1+k, k)
            c = c * (p + k) / (k + 1);
        }
        double[] re = new double[deg];
        double[] im = new double[deg];
        rootsDurandKerner(coef, deg, re, im);
        // the polynomial in z: (1 + z)^p times Π (z - z_j) over the minimum-phase roots of (2 - z - 1/z)/4 = y_j
        double[] pr = {1};
        double[] pi = {0};
        for (int k = 0; k < p; k++) {
            double[][] m = mul(pr, pi, new double[] {1, 1}, new double[] {0, 0});
            pr = m[0];
            pi = m[1];
        }
        for (int j = 0; j < deg; j++) {
            // z² - (2 - 4y) z + 1 = 0
            double br = 1 - 2 * re[j];
            double bi = -2 * im[j];
            double dr = br * br - bi * bi - 1; // (1 - 2y)² - 1
            double di = 2 * br * bi;
            double[] sq = csqrt(dr, di);
            double z1r = br + sq[0];
            double z1i = bi + sq[1];
            double z2r = br - sq[0];
            double z2i = bi - sq[1];
            boolean first = z1r * z1r + z1i * z1i < z2r * z2r + z2i * z2i;
            double zr = first ? z1r : z2r;
            double zi = first ? z1i : z2i;
            double[][] m = mul(pr, pi, new double[] {-zr, 1}, new double[] {-zi, 0});
            pr = m[0];
            pi = m[1];
        }
        double sum = 0;
        for (double v : pr) {
            sum += v;
        }
        double scale = Math.sqrt(2) / sum;
        double[] h = new double[pr.length];
        for (int k = 0; k < h.length; k++) {
            h[k] = pr[h.length - 1 - k] * scale; // the conventional order: the large taps first
        }
        return new Wavelet(h);
    }

    /** Creates a wavelet from caller-supplied orthonormal low-pass taps (copied). */
    public static Wavelet fromLowpass(double[] lowpass) {
        if (lowpass == null || lowpass.length < 2 || (lowpass.length & 1) != 0) {
            throw new IllegalArgumentException("an orthogonal low-pass filter has an even number of taps");
        }
        return new Wavelet(lowpass.clone());
    }

    /** @return a copy of the low-pass (scaling) filter */
    public double[] lowpass() {
        return lo.clone();
    }

    /** @return a copy of the high-pass (wavelet) filter, {@code g[m] = (-1)^m·h[L-1-m]} */
    public double[] highpass() {
        return hi.clone();
    }

    /**
     * One level: {@code out[0 .. n/2)} receives the approximation {@code a[k] = Σ h[m]·x[(2k + m) mod n]} and
     * {@code out[n/2 .. n)} the detail, with the high-pass filter.
     *
     * @param n the number of samples, even
     * @param out receives {@code n} coefficients; must not overlap {@code x}
     */
    public void forward(double[] x, int xOff, int n, double[] out, int outOff) {
        checkLevel(x, xOff, n, out, outOff);
        int half = n / 2;
        for (int k = 0; k < half; k++) {
            double a = 0;
            double d = 0;
            for (int m = 0; m < lo.length; m++) {
                double v = x[xOff + (int) ((2L * k + m) % n)];
                a = Math.fma(lo[m], v, a);
                d = Math.fma(hi[m], v, d);
            }
            out[outOff + k] = a;
            out[outOff + half + k] = d;
        }
    }

    /** The inverse of one {@link #forward} level: {@code x[i] = Σ_k a[k]·h[i - 2k] + d[k]·g[i - 2k]}, periodically. */
    public void inverse(double[] coeffs, int cOff, int n, double[] x, int xOff) {
        checkLevel(coeffs, cOff, n, x, xOff);
        int half = n / 2;
        java.util.Arrays.fill(x, xOff, xOff + n, 0.0);
        for (int k = 0; k < half; k++) {
            double a = coeffs[cOff + k];
            double d = coeffs[cOff + half + k];
            for (int m = 0; m < lo.length; m++) {
                int i = xOff + (int) ((2L * k + m) % n);
                x[i] = Math.fma(lo[m], a, Math.fma(hi[m], d, x[i]));
            }
        }
    }

    /**
     * A multilevel transform: {@code levels} forward levels on successive approximations. The result is laid out as
     * {@code [aL, dL, d(L-1), ..., d1]}, so {@code out[0 .. n/2^L)} is the coarsest approximation.
     *
     * @param n divisible by {@code 2^levels}
     * @param scratch {@code n} doubles
     */
    public void forward(double[] x, int xOff, int n, int levels, double[] out, int outOff, double[] scratch,
            int scratchOff) {
        checkLevels(n, levels);
        System.arraycopy(x, xOff, scratch, scratchOff, n);
        int len = n;
        for (int lv = 0; lv < levels; lv++) {
            forward(scratch, scratchOff, len, out, outOff);
            System.arraycopy(out, outOff, scratch, scratchOff, len / 2);
            len /= 2;
        }
        System.arraycopy(scratch, scratchOff, out, outOff, len);
    }

    /** The inverse of the multilevel {@link #forward(double[], int, int, int, double[], int, double[], int)}. */
    public void inverse(double[] coeffs, int cOff, int n, int levels, double[] x, int xOff, double[] scratch,
            int scratchOff) {
        checkLevels(n, levels);
        int len = n >> levels;
        System.arraycopy(coeffs, cOff, x, xOff, len);
        for (int lv = 0; lv < levels; lv++) {
            System.arraycopy(x, xOff, scratch, scratchOff, len);
            System.arraycopy(coeffs, cOff + len, scratch, scratchOff + len, len);
            inverse(scratch, scratchOff, 2 * len, x, xOff);
            len *= 2;
        }
    }

    private static void checkLevels(int n, int levels) {
        if (levels < 1 || n < 2 || (n & ((1 << levels) - 1)) != 0) {
            throw new IllegalArgumentException("n must be divisible by 2^levels: " + n + ", " + levels);
        }
    }

    private static void checkLevel(double[] a, int aOff, int n, double[] b, int bOff) {
        if (n < 2 || (n & 1) != 0 || a == null || b == null || aOff < 0 || aOff > a.length - n || bOff < 0
                || bOff > b.length - n) {
            throw new IllegalArgumentException("need an even n and arrays holding n values");
        }
    }

    /** Complex polynomial product. */
    private static double[][] mul(double[] ar, double[] ai, double[] br, double[] bi) {
        double[] r = new double[ar.length + br.length - 1];
        double[] i = new double[r.length];
        for (int a = 0; a < ar.length; a++) {
            for (int b = 0; b < br.length; b++) {
                r[a + b] += ar[a] * br[b] - ai[a] * bi[b];
                i[a + b] += ar[a] * bi[b] + ai[a] * br[b];
            }
        }
        return new double[][] {r, i};
    }

    /** The principal complex square root, algebraically (no transcendental functions: reproducible). */
    private static double[] csqrt(double re, double im) {
        double m = Math.sqrt(re * re + im * im);
        double r = Math.sqrt(0.5 * (m + re));
        double i = Math.copySign(Math.sqrt(0.5 * (m - re)), im);
        return new double[] {r, i};
    }

    /** All roots of {@code Σ coef[k]·y^k} (degree {@code deg}) by the Durand–Kerner iteration. */
    private static void rootsDurandKerner(double[] coef, int deg, double[] re, double[] im) {
        if (deg == 0) {
            return;
        }
        double lead = coef[deg];
        // the classic start (0.4 + 0.9i)^j, scaled by the Cauchy bound 1 + max|coef/lead|: rational, so reproducible
        double bound = 0;
        for (int k = 0; k < deg; k++) {
            bound = Math.max(bound, Math.abs(coef[k] / lead));
        }
        bound += 1;
        double wr = 1;
        double wi = 0;
        for (int j = 0; j < deg; j++) {
            re[j] = bound * wr;
            im[j] = bound * wi;
            double t = 0.4 * wr - 0.9 * wi;
            wi = 0.4 * wi + 0.9 * wr;
            wr = t;
        }
        for (int it = 0; it < 500; it++) {
            double change = 0;
            for (int j = 0; j < deg; j++) {
                // p(y_j) by Horner
                double pr = coef[deg];
                double pim = 0;
                for (int k = deg - 1; k >= 0; k--) {
                    double t = pr * re[j] - pim * im[j] + coef[k];
                    pim = pr * im[j] + pim * re[j];
                    pr = t;
                }
                // lead · Π (y_j - y_k)
                double qr = lead;
                double qi = 0;
                for (int k = 0; k < deg; k++) {
                    if (k != j) {
                        double dr = re[j] - re[k];
                        double di = im[j] - im[k];
                        double t = qr * dr - qi * di;
                        qi = qr * di + qi * dr;
                        qr = t;
                    }
                }
                double den = qr * qr + qi * qi;
                double sr = (pr * qr + pim * qi) / den;
                double si = (pim * qr - pr * qi) / den;
                re[j] -= sr;
                im[j] -= si;
                change = Math.max(change, Math.abs(sr) + Math.abs(si));
            }
            if (change < 1e-15) {
                break;
            }
        }
    }
}
