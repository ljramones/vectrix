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
 * Conversions between linear RGB gamuts (single precision): the matrices of {@link ColorSpacesd}, rounded to float,
 * with the same methods.
 * <p>
 * The methods are safe to call from any thread. Every gamut matrix is computed once, when the class loads, so
 * {@link #convert(Vector3fc, ColorGamut, ColorGamut, Vector3f)} and {@link #conversionMatrix} are table lookups.
 * {@link #adaptationMatrix}, for arbitrary white points, computes its matrix in double on each call and rounds it.
 * No method allocates.
 *
 * @since 2.1.0
 */
public final class ColorSpacesf {

    /** The number of gamuts, cached: {@code values()} copies its array on every call. */
    private static final int GAMUTS = ColorGamut.values().length;
    private static final float[][][] CONVERT;
    private static final float[][] TO_XYZ = new float[GAMUTS][];
    private static final float[][] FROM_XYZ = new float[GAMUTS][];

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
            TO_XYZ[from.ordinal()] = round(ColorSpacesd.toXyzMatrix(from, m));
            FROM_XYZ[from.ordinal()] = round(ColorSpacesd.fromXyzMatrix(from, m));
        }
    }

    private static float[] round(double[] m) {
        float[] f = new float[9];
        for (int i = 0; i < 9; i++) {
            f[i] = (float) m[i];
        }
        return f;
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

    /**
     * Converts linear RGB in {@code gamut} to CIE XYZ relative to that gamut's own white point (D65 or the ACES white),
     * without adaptation; RGB white (1, 1, 1) gives Y = 1.
     *
     * @param rgb the linear color
     * @param gamut the gamut of {@code rgb}
     * @param dest receives X, Y and Z; may be {@code rgb}
     * @return {@code dest}
     * @since 3.0.0
     */
    public static Vector3f toXyz(Vector3fc rgb, ColorGamut gamut, Vector3f dest) {
        float[] m = TO_XYZ[gamut.ordinal()];
        float r = rgb.x(), g = rgb.y(), b = rgb.z();
        return dest.set(m[0] * r + m[1] * g + m[2] * b, m[3] * r + m[4] * g + m[5] * b, m[6] * r + m[7] * g + m[8] * b);
    }

    /**
     * Converts CIE XYZ, relative to the gamut's own white point, to linear RGB in {@code gamut}; the inverse of
     * {@link #toXyz}. Out-of-gamut colors give channels outside [0, 1]; nothing is clamped.
     *
     * @param xyz the XYZ color
     * @param gamut the target gamut
     * @param dest receives the linear color; may be {@code xyz}
     * @return {@code dest}
     * @since 3.0.0
     */
    public static Vector3f fromXyz(Vector3fc xyz, ColorGamut gamut, Vector3f dest) {
        float[] m = FROM_XYZ[gamut.ordinal()];
        float x = xyz.x(), y = xyz.y(), z = xyz.z();
        return dest.set(m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z);
    }

    /**
     * Writes the row-major RGB to XYZ matrix of {@code gamut} (the matrix {@link #toXyz} uses), rounded to float.
     *
     * @param gamut the gamut
     * @param out9 receives the nine matrix elements at indices 0 to 8
     * @return {@code out9}
     * @since 3.0.0
     */
    public static float[] toXyzMatrix(ColorGamut gamut, float[] out9) {
        System.arraycopy(TO_XYZ[gamut.ordinal()], 0, out9, 0, 9);
        return out9;
    }

    /**
     * Writes the row-major XYZ to RGB matrix of {@code gamut} (the matrix {@link #fromXyz} uses), rounded to float.
     *
     * @param gamut the gamut
     * @param out9 receives the nine matrix elements at indices 0 to 8
     * @return {@code out9}
     * @since 3.0.0
     */
    public static float[] fromXyzMatrix(ColorGamut gamut, float[] out9) {
        System.arraycopy(FROM_XYZ[gamut.ordinal()], 0, out9, 0, 9);
        return out9;
    }

    /**
     * Writes the chromatic adaptation matrix (row-major) from white {@code srcWhite} to {@code dstWhite}, with the
     * given transform: {@code M⁻¹ diag(M dst / M src) M}, where {@code M} is the transform's cone-response matrix.
     * Computed in double, as {@link ColorSpacesd#adaptationMatrix}, and rounded. Does not allocate.
     *
     * @param srcWhite the source white point in XYZ
     * @param dstWhite the target white point in XYZ
     * @param adaptation the transform; {@link ColorSpacesd.Adaptation#NONE} gives the identity
     * @param out9 receives the nine matrix elements at indices 0 to 8
     * @return {@code out9}
     * @since 3.0.0
     */
    public static float[] adaptationMatrix(Vector3fc srcWhite, Vector3fc dstWhite, ColorSpacesd.Adaptation adaptation,
            float[] out9) {
        ColorSpacesd.adaptation(srcWhite.x(), srcWhite.y(), srcWhite.z(), dstWhite.x(), dstWhite.y(), dstWhite.z(),
                adaptation, null, out9);
        return out9;
    }
}
