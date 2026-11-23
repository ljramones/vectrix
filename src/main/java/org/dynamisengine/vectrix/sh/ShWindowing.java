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
package org.dynamisengine.vectrix.sh;

import org.dynamisengine.vectrix.core.AccurateMath;

/**
 * Windowing of SH coefficients to reduce ringing (Gibbs oscillation) in truncated reconstructions of sharp lighting,
 * as in Sloan, "Deringing Spherical Harmonics" (2017). Each band {@code l} is scaled by a window weight
 * {@code w(l / width)}; a larger {@code width} keeps more detail and removes less ringing. {@code width = maxBand + 1}
 * is the usual starting point; the DC band is never changed.
 *
 * @since 2.3.0
 */
public final class ShWindowing {
    /** Window shapes. */
    public enum Window {
        /** {@code (1 + cos(π t)) / 2}: strong ringing suppression, more blur. */
        HANN,
        /** {@code sin(π t) / (π t)} (Lanczos sigma factor): milder blur. */
        LANCZOS
    }

    private ShWindowing() {
    }

    /**
     * Returns the window weight of SH band {@code band}: 1 at band 0, {@code w(band / width)} below {@code width},
     * and 0 at and beyond {@code width}. A width of 0 keeps only band 0.
     *
     * @param window the window shape, not {@code null}
     * @param band the SH band {@code l}, at least 0
     * @param width the window width in bands, at least 0 and not NaN
     * @return the weight, in [0, 1]
     * @throws IllegalArgumentException if {@code window} is {@code null}, {@code band} is negative, or
     *         {@code width} is negative or NaN
     */
    public static double weight(Window window, int band, double width) {
        check(window, width);
        if (band < 0) {
            throw new IllegalArgumentException("band must be at least 0: " + band);
        }
        if (band == 0) {
            return 1.0;
        }
        double t = band / width;
        if (!(t < 1.0)) {
            return 0.0;
        }
        if (window == Window.HANN) {
            return 0.5 * (1.0 + AccurateMath.cos(java.lang.Math.PI * t));
        }
        double x = java.lang.Math.PI * t;
        return AccurateMath.sin(x) / x;
    }

    /**
     * Single-precision {@link #weight(Window, int, double)}: computed in double and rounded to float.
     *
     * @param window the window shape, not {@code null}
     * @param band the SH band {@code l}, at least 0
     * @param width the window width in bands, at least 0 and not NaN
     * @return the weight, in [0, 1]
     * @throws IllegalArgumentException if {@code window} is {@code null}, {@code band} is negative, or
     *         {@code width} is negative or NaN
     */
    public static float weight(Window window, int band, float width) {
        return (float) weight(window, band, (double) width);
    }

    private static void check(Window window, double width) {
        if (window == null) {
            throw new IllegalArgumentException("window must not be null");
        }
        if (!(width >= 0.0)) {
            throw new IllegalArgumentException("width must be at least 0: " + width);
        }
    }

    private static void apply(float[] in, float[] out, int bands, Window window, float width) {
        check(window, width);
        for (int l = 0; l <= bands; l++) {
            float w = weight(window, l, width);
            for (int i = 3 * l * l; i < 3 * (l + 1) * (l + 1); i++) {
                out[i] = in[i] * w;
            }
        }
    }

    private static void apply(double[] in, double[] out, int bands, Window window, double width) {
        check(window, width);
        for (int l = 0; l <= bands; l++) {
            double w = weight(window, l, width);
            for (int i = 3 * l * l; i < 3 * (l + 1) * (l + 1); i++) {
                out[i] = in[i] * w;
            }
        }
    }

    /**
     * Windows L2 RGB coefficients: every coefficient of band {@code l} (0..2) is multiplied by
     * {@link #weight(Window, int, double) weight(window, l, width)}.
     *
     * @param in the coefficients to window
     * @param window the window shape
     * @param width the window width in bands, at least 0; 3 is the usual starting point
     * @param dest receives the windowed coefficients; may be {@code in}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code window} is {@code null} or {@code width} is negative or NaN
     *         (checked before anything is written)
     */
    public static ShCoeffs9f windowL2(ShCoeffs9f in, Window window, float width, ShCoeffs9f dest) {
        apply(in.c, dest.c, 2, window, width);
        return dest;
    }

    /**
     * Windows L3 RGB coefficients: every coefficient of band {@code l} (0..3) is multiplied by
     * {@link #weight(Window, int, double) weight(window, l, width)}.
     *
     * @param in the coefficients to window
     * @param window the window shape
     * @param width the window width in bands, at least 0; 4 is the usual starting point
     * @param dest receives the windowed coefficients; may be {@code in}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code window} is {@code null} or {@code width} is negative or NaN
     *         (checked before anything is written)
     */
    public static ShCoeffs16f windowL3(ShCoeffs16f in, Window window, float width, ShCoeffs16f dest) {
        apply(in.c, dest.c, 3, window, width);
        return dest;
    }

    /**
     * Windows L2 RGB coefficients in double precision: every coefficient of band {@code l} (0..2) is multiplied by
     * {@link #weight(Window, int, double) weight(window, l, width)}.
     *
     * @param in the coefficients to window
     * @param window the window shape
     * @param width the window width in bands, at least 0; 3 is the usual starting point
     * @param dest receives the windowed coefficients; may be {@code in}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code window} is {@code null} or {@code width} is negative or NaN
     *         (checked before anything is written)
     */
    public static ShCoeffs9d windowL2(ShCoeffs9d in, Window window, double width, ShCoeffs9d dest) {
        apply(in.c, dest.c, 2, window, width);
        return dest;
    }

    /**
     * Windows L3 RGB coefficients in double precision: every coefficient of band {@code l} (0..3) is multiplied by
     * {@link #weight(Window, int, double) weight(window, l, width)}.
     *
     * @param in the coefficients to window
     * @param window the window shape
     * @param width the window width in bands, at least 0; 4 is the usual starting point
     * @param dest receives the windowed coefficients; may be {@code in}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code window} is {@code null} or {@code width} is negative or NaN
     *         (checked before anything is written)
     */
    public static ShCoeffs16d windowL3(ShCoeffs16d in, Window window, double width, ShCoeffs16d dest) {
        apply(in.c, dest.c, 3, window, width);
        return dest;
    }
}
