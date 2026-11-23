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

import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Conversions between linear RGB gamuts (single precision): the matrices of {@link ColorSpacesd}, rounded to float.
 * <p>
 * All matrices are computed once, when the class loads, so {@link #convert(Vector3fc, ColorGamut, ColorGamut,
 * Vector3f)} and {@link #conversionMatrix} are table lookups. The methods are safe to call from any thread.
 *
 * @since 2.1.0
 */
public final class ColorSpacesf {

    /** The number of gamuts, cached: {@code values()} copies its array on every call. */
    private static final int GAMUTS = ColorGamut.values().length;
    private static final float[][][] CONVERT;

    static {
        ColorGamut[] g = ColorGamut.values();
        ColorSpacesd.Adaptation[] ad = ColorSpacesd.Adaptation.values();
        CONVERT = new float[g.length * g.length][ad.length][];
        double[] m = new double[9];
        for (ColorGamut from : g) {
            for (ColorGamut to : g) {
                for (ColorSpacesd.Adaptation a : ad) {
                    ColorSpacesd.conversionMatrix(from, to, a, m);
                    float[] f = new float[9];
                    for (int i = 0; i < 9; i++) {
                        f[i] = (float) m[i];
                    }
                    CONVERT[from.ordinal() * g.length + to.ordinal()][a.ordinal()] = f;
                }
            }
        }
    }

    private ColorSpacesf() {
    }

    /**
     * Converts linear RGB between gamuts with Bradford adaptation.
     *
     * @param rgb the linear color in {@code from}
     * @param from the source gamut
     * @param to the target gamut
     * @param dest receives the linear color in {@code to}; may be {@code rgb}
     * @return {@code dest}
     */
    public static Vector3f convert(Vector3fc rgb, ColorGamut from, ColorGamut to, Vector3f dest) {
        return convert(rgb, from, to, ColorSpacesd.Adaptation.BRADFORD, dest);
    }

    /**
     * Converts linear RGB between gamuts with the given white-point adaptation.
     *
     * @param rgb the linear color in {@code from}
     * @param from the source gamut
     * @param to the target gamut
     * @param adaptation the white-point adaptation to use
     * @param dest receives the linear color in {@code to}; may be {@code rgb}
     * @return {@code dest}
     */
    public static Vector3f convert(Vector3fc rgb, ColorGamut from, ColorGamut to, ColorSpacesd.Adaptation adaptation,
            Vector3f dest) {
        float[] m = CONVERT[from.ordinal() * GAMUTS + to.ordinal()][adaptation.ordinal()];
        float r = rgb.x(), g = rgb.y(), b = rgb.z();
        return dest.set(m[0] * r + m[1] * g + m[2] * b, m[3] * r + m[4] * g + m[5] * b, m[6] * r + m[7] * g + m[8] * b);
    }

    /**
     * Writes the row-major 3×3 conversion matrix, rounded to float; see
     * {@link ColorSpacesd#conversionMatrix(ColorGamut, ColorGamut, ColorSpacesd.Adaptation, double[])}.
     *
     * @param from the source gamut
     * @param to the target gamut
     * @param adaptation the white-point adaptation to use
     * @param out9 receives the nine matrix elements at indices 0 to 8
     * @return {@code out9}
     */
    public static float[] conversionMatrix(ColorGamut from, ColorGamut to, ColorSpacesd.Adaptation adaptation,
            float[] out9) {
        System.arraycopy(CONVERT[from.ordinal() * GAMUTS + to.ordinal()][adaptation.ordinal()], 0,
                out9, 0, 9);
        return out9;
    }
}
