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
package org.dynamisengine.vectrix.color;

/**
 * RGB to reflectance-spectrum upsampling (Jakob and Hanika, "A Low-Dimensional Function Space for Efficient Spectral
 * Upsampling", 2019): the smooth, bounded spectrum {@code s(λ) = S(c₀λ² + c₁λ + c₂)} with
 * {@code S(x) = ½ + x / (2√(1 + x²))}, whose color under illuminant D65 (CIE 1931 observer, official 1 nm tables)
 * equals a given linear RGB.
 * <p>
 * {@link #fit} solves the three equations exactly (to about 1e-12) by damped Newton iteration with an analytic
 * Jacobian, continued from grey, instead of looking the coefficients up in the paper's precomputed 3D tables; it costs
 * a few tens of microseconds per color, so precompute once per material or build a table with it. Coefficients are
 * for {@code λ} in nanometres, as in the reference {@code rgb2spec} implementation. The RGB mapping is calibrated so
 * the constant spectrum 1 is exactly {@code (1, 1, 1)}. Supported gamuts are those with a D65 white point; the
 * ACES gamuts throw {@link IllegalArgumentException}.
 * <p>
 * The per-gamut weight tables are built on first use, under a lock. All methods are safe to call from any thread;
 * {@link #fit} allocates small working arrays, the other methods allocate nothing once the tables exist.
 *
 * @since 2.3.0
 */
public final class SpectralUpsampling {
    private static final int N = CieObserver.MAX_NM - CieObserver.MIN_NM + 1;
    private static final double SPAN = CieObserver.MAX_NM - CieObserver.MIN_NM;
    /** Per gamut: RGB weights of each 1 nm sample, {@code w[3 k + c]}. */
    private static final double[][] WEIGHTS = new double[ColorGamut.values().length][];

    private SpectralUpsampling() {
    }

    private static double[] weights(ColorGamut gamut) {
        if (gamut.acesWhite) {
            throw new IllegalArgumentException(gamut + " has a D60 white point; spectral upsampling here uses D65");
        }
        synchronized (WEIGHTS) {
            double[] w = WEIGHTS[gamut.ordinal()];
            if (w != null) {
                return w;
            }
            double[] toXyz = ColorSpacesd.toXyzMatrix(gamut, new double[9]);
            double[] toRgb = invert3(toXyz);
            w = new double[3 * N];
            double[] sum = new double[3];
            for (int k = 0; k < N; k++) {
                int nm = CieObserver.MIN_NM + k;
                double e = Spectra.d65(nm);
                double x = e * CieObserver.CIE_1931_2DEG.at(nm, 0);
                double y = e * CieObserver.CIE_1931_2DEG.at(nm, 1);
                double z = e * CieObserver.CIE_1931_2DEG.at(nm, 2);
                for (int c = 0; c < 3; c++) {
                    w[3 * k + c] = toRgb[3 * c] * x + toRgb[3 * c + 1] * y + toRgb[3 * c + 2] * z;
                    sum[c] += w[3 * k + c];
                }
            }
            for (int k = 0; k < N; k++) { // calibrate: the constant spectrum 1 maps to (1, 1, 1)
                for (int c = 0; c < 3; c++) {
                    w[3 * k + c] /= sum[c];
                }
            }
            WEIGHTS[gamut.ordinal()] = w;
            return w;
        }
    }

    private static double[] invert3(double[] m) {
        double a = m[0], b = m[1], c = m[2], d = m[3], e = m[4], f = m[5], g = m[6], h = m[7], i = m[8];
        double ai = e * i - f * h, bi = -(d * i - f * g), ci = d * h - e * g;
        double det = a * ai + b * bi + c * ci;
        return new double[] {ai / det, -(b * i - c * h) / det, (b * f - c * e) / det, bi / det, (a * i - c * g) / det,
            -(a * f - c * d) / det, ci / det, -(a * h - b * g) / det, (a * e - b * d) / det};
    }

    /**
     * Returns the sigmoid {@code S(x) = ½ + x / (2√(1 + x²))}, which maps the real line onto (0, 1).
     *
     * @param x the argument
     * @return {@code S(x)}
     */
    public static double sigmoid(double x) {
        return 0.5 + x / (2.0 * java.lang.Math.sqrt(1.0 + x * x));
    }

    /**
     * Evaluates the spectrum of fitted coefficients at a wavelength: {@code S((c₀ λ + c₁) λ + c₂)}.
     *
     * @param coeffs the coefficients {@code (c₀, c₁, c₂)} in the nanometre domain, as written by {@link #fit}
     * @param nm the wavelength λ in nanometres
     * @return the reflectance, in (0, 1)
     */
    public static double evaluate(double[] coeffs, double nm) {
        return sigmoid((coeffs[0] * nm + coeffs[1]) * nm + coeffs[2]);
    }

    /**
     * Single-precision {@link #evaluate(double[], double)} for renderers; the polynomial is evaluated in double.
     *
     * @param coeffs the coefficients {@code (c₀, c₁, c₂)} in the nanometre domain
     * @param nm the wavelength λ in nanometres
     * @return the reflectance, in [0, 1]
     */
    public static float evaluate(float[] coeffs, float nm) {
        double x = ((double) coeffs[0] * nm + coeffs[1]) * nm + coeffs[2];
        return (float) sigmoid(x);
    }

    /**
     * RGB of normalized-domain coefficients ({@code t = (λ − 360) / 470}) into {@code rgb}; Jacobian into {@code j}.
     */
    private static void rgbOf(double[] w, double a, double b, double c, double[] rgb, double[] j) {
        double r0 = 0, r1 = 0, r2 = 0;
        double[] jj = j;
        if (jj != null) {
            java.util.Arrays.fill(jj, 0.0);
        }
        for (int k = 0; k < N; k++) {
            double t = k / SPAN;
            double x = (a * t + b) * t + c;
            double q = 1.0 + x * x;
            double s = 0.5 + x / (2.0 * java.lang.Math.sqrt(q));
            double w0 = w[3 * k], w1 = w[3 * k + 1], w2 = w[3 * k + 2];
            r0 += s * w0;
            r1 += s * w1;
            r2 += s * w2;
            if (jj != null) {
                double ds = 0.5 / (q * java.lang.Math.sqrt(q)); // S'(x)
                double da = ds * t * t, db = ds * t;
                jj[0] += w0 * da;
                jj[1] += w0 * db;
                jj[2] += w0 * ds;
                jj[3] += w1 * da;
                jj[4] += w1 * db;
                jj[5] += w1 * ds;
                jj[6] += w2 * da;
                jj[7] += w2 * db;
                jj[8] += w2 * ds;
            }
        }
        rgb[0] = r0;
        rgb[1] = r1;
        rgb[2] = r2;
    }

    /**
     * Fits the coefficients of a smooth spectrum whose linear RGB in {@code gamut} under D65 is {@code (r, g, b)}.
     * <p>
     * Runs a damped Newton iteration from grey, and if that fails, again with continuation along the line from grey to
     * the target. Allocates small working arrays; the first call for a gamut also builds its weight table.
     *
     * @param r the linear red channel
     * @param g the linear green channel
     * @param b the linear blue channel
     * @param gamut the RGB gamut; must have a D65 white point
     * @param coeffsOut receives {@code (c₀, c₁, c₂)} in the nanometre domain at indices 0 to 2
     * @return {@code true} when the RGB residual is below 1e-12; {@code false} when the color is outside what a
     *         reflectance strictly between 0 and 1 can produce (for example a channel at or beyond 0 or 1), in which
     *         case {@code coeffsOut} holds the best coefficients found
     * @throws IllegalArgumentException if {@code gamut} has the ACES white point
     */
    public static boolean fit(double r, double g, double b, ColorGamut gamut, double[] coeffsOut) {
        double[] w = weights(gamut);
        // plain Newton from grey first; continuation along the grey-to-target line only if that fails
        if (solve(w, r, g, b, 1, coeffsOut)) {
            return true;
        }
        return solve(w, r, g, b, 16, coeffsOut);
    }

    private static boolean solve(double[] w, double r, double g, double b, int steps, double[] coeffsOut) {
        double[] rgb = new double[3], jac = new double[9], trial = new double[3];
        double ca = 0.0, cb = 0.0, cc = 0.0; // constant 0.5: RGB (0.5, 0.5, 0.5)
        for (int stage = 1; stage <= steps; stage++) {
            double u = (double) stage / steps;
            double tr = 0.5 + u * (r - 0.5), tg = 0.5 + u * (g - 0.5), tb = 0.5 + u * (b - 0.5);
            for (int it = 0; it < 50; it++) {
                rgbOf(w, ca, cb, cc, rgb, jac);
                double f0 = rgb[0] - tr, f1 = rgb[1] - tg, f2 = rgb[2] - tb;
                double residual = java.lang.Math.sqrt(f0 * f0 + f1 * f1 + f2 * f2);
                if (residual < 1e-13) {
                    break;
                }
                double[] inv = invert3(jac);
                double da = -(inv[0] * f0 + inv[1] * f1 + inv[2] * f2);
                double db = -(inv[3] * f0 + inv[4] * f1 + inv[5] * f2);
                double dc = -(inv[6] * f0 + inv[7] * f1 + inv[8] * f2);
                if (!Double.isFinite(da + db + dc)) {
                    break;
                }
                double lambda = 1.0;
                boolean improved = false;
                for (int half = 0; half < 30; half++) {
                    rgbOf(w, ca + lambda * da, cb + lambda * db, cc + lambda * dc, trial, null);
                    double g0 = trial[0] - tr, g1 = trial[1] - tg, g2 = trial[2] - tb;
                    if (java.lang.Math.sqrt(g0 * g0 + g1 * g1 + g2 * g2) < residual) {
                        improved = true;
                        break;
                    }
                    lambda *= 0.5;
                }
                if (!improved) {
                    break;
                }
                ca += lambda * da;
                cb += lambda * db;
                cc += lambda * dc;
            }
        }
        rgbOf(w, ca, cb, cc, rgb, null);
        double f0 = rgb[0] - r, f1 = rgb[1] - g, f2 = rgb[2] - b;
        double residual = java.lang.Math.sqrt(f0 * f0 + f1 * f1 + f2 * f2);
        // back to nanometres: x = a t² + b t + c with t = (λ − λ0) / span
        double l0 = CieObserver.MIN_NM;
        double a = ca / (SPAN * SPAN);
        coeffsOut[0] = a;
        coeffsOut[1] = cb / SPAN - 2.0 * a * l0;
        coeffsOut[2] = cc - cb * l0 / SPAN + a * l0 * l0;
        return residual < 1e-12;
    }

    /**
     * Returns the linear RGB in {@code gamut} of the spectrum with nanometre-domain {@code coeffs}, under D65 (1 nm
     * summation over 360 to 830 nm). Use it to check a {@link #fit}.
     *
     * @param coeffs the coefficients {@code (c₀, c₁, c₂)} in the nanometre domain
     * @param gamut the RGB gamut; must have a D65 white point
     * @param rgbOut receives (r, g, b) at indices 0 to 2
     * @return {@code rgbOut}
     * @throws IllegalArgumentException if {@code gamut} has the ACES white point
     */
    public static double[] rgbOf(double[] coeffs, ColorGamut gamut, double[] rgbOut) {
        double[] w = weights(gamut);
        double r0 = 0, r1 = 0, r2 = 0;
        for (int k = 0; k < N; k++) {
            double s = evaluate(coeffs, CieObserver.MIN_NM + k);
            r0 += s * w[3 * k];
            r1 += s * w[3 * k + 1];
            r2 += s * w[3 * k + 2];
        }
        rgbOut[0] = r0;
        rgbOut[1] = r1;
        rgbOut[2] = r2;
        return rgbOut;
    }
}
