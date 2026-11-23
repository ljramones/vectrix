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

import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Conversions between linear RGB gamuts (double precision), through CIE XYZ, with chromatic adaptation between
 * white points (Bradford by default, as in the ACES reference implementation, or CAT16).
 * <p>
 * The matrices are derived from each gamut's primaries. The D65 white is the one implied by
 * {@link ColorMathd#linearSrgbToXyz(double, double, double, Vector3d)} (XYZ 0.95047, 1, 1.08883), so sRGB white
 * stays white in every D65 gamut and sRGB conversions match the rest of the library exactly. The ACES white point is
 * x 0.32168, y 0.33767. All matrices are 3×3 row-major: {@code out[i] = m[3i] r + m[3i+1] g + m[3i+2] b}.
 * <p>
 * The methods are safe to call from any thread. Every gamut-to-gamut matrix is computed once, on first use, so
 * {@link #convert(Vector3dc, ColorGamut, ColorGamut, Vector3d)} and {@link #conversionMatrix} do not allocate.
 * {@link #adaptationMatrix}, for arbitrary white points, computes its matrix on each call and allocates small
 * temporary arrays.
 *
 * @since 2.1.0
 */
public final class ColorSpacesd {

    /** Chromatic adaptation transforms (von Kries scaling in a cone-response space). */
    public enum Adaptation {
        /** Bradford (Lam 1985), used by the ACES reference transforms. */
        BRADFORD,
        /** CAT16 (Li et al. 2017), the CIECAM16 adaptation matrix. */
        CAT16,
        /** No adaptation: XYZ is passed through unchanged, so white points are not mapped onto each other. */
        NONE
    }

    private static final double[] D65 = {0.95047, 1.0, 1.08883};
    private static final double[] ACES_WHITE = xyToXyz(0.32168, 0.33767);
    private static final double[] BRADFORD_M = {0.8951, 0.2664, -0.1614, -0.7502, 1.7135, 0.0367, 0.0389, -0.0685,
        1.0296};
    private static final double[] CAT16_M = {0.401288, 0.650173, -0.051461, -0.250268, 1.204414, 0.045854,
        -0.002079, 0.048952, 0.953127};

    private static final double[][] TO_XYZ = new double[ColorGamut.values().length][];
    private static final double[][] FROM_XYZ = new double[ColorGamut.values().length][];

    static {
        for (ColorGamut g : ColorGamut.values()) {
            double[] m = g == ColorGamut.SRGB
                    ? new double[] {0.4124564, 0.3575761, 0.1804375, 0.2126729, 0.7151522, 0.0721750, 0.0193339,
                        0.1191920, 0.9503041}
                    : rgbToXyz(g, g.acesWhite ? ACES_WHITE : D65);
            TO_XYZ[g.ordinal()] = m;
            FROM_XYZ[g.ordinal()] = g == ColorGamut.SRGB
                    ? new double[] {3.2404542, -1.5371385, -0.4985314, -0.9692660, 1.8760108, 0.0415560, 0.0556434,
                        -0.2040259, 1.0572252}
                    : invert(m);
        }
    }

    private ColorSpacesd() {
    }

    /** XYZ (Y = 1) of chromaticity {@code (x, y)}. */
    private static double[] xyToXyz(double x, double y) {
        return new double[] {x / y, 1.0, (1.0 - x - y) / y};
    }

    /** RGB to XYZ: columns are the primaries' XYZ, scaled so that RGB (1, 1, 1) maps to {@code white}. */
    private static double[] rgbToXyz(ColorGamut g, double[] white) {
        double[] r = xyToXyz(g.rx, g.ry);
        double[] gr = xyToXyz(g.gx, g.gy);
        double[] b = xyToXyz(g.bx, g.by);
        double[] p = {r[0], gr[0], b[0], r[1], gr[1], b[1], r[2], gr[2], b[2]};
        double[] s = mul(invert(p), white);
        return new double[] {p[0] * s[0], p[1] * s[1], p[2] * s[2], p[3] * s[0], p[4] * s[1], p[5] * s[2],
            p[6] * s[0], p[7] * s[1], p[8] * s[2]};
    }

    private static double[] white(ColorGamut g) {
        return g.acesWhite ? ACES_WHITE : D65;
    }

    /**
     * Writes the 3×3 matrix (row-major) converting linear RGB in {@code from} to linear RGB in {@code to}, for baking
     * into shaders: {@code fromXyz(to) · adapt(white(from) → white(to)) · toXyz(from)}. The adaptation step is skipped
     * when both gamuts share a white point or {@code adaptation} is {@link Adaptation#NONE}; equal gamuts give the
     * exact identity. Transpose the result before uploading it as a column-major GLSL {@code mat3}.
     *
     * @param from the source gamut
     * @param to the target gamut
     * @param adaptation the white-point adaptation to use
     * @param out9 receives the nine matrix elements at indices 0 to 8
     * @return {@code out9}
     */
    public static double[] conversionMatrix(ColorGamut from, ColorGamut to, Adaptation adaptation, double[] out9) {
        System.arraycopy(Table.CONVERT[Table.index(from, to, adaptation)], 0, out9, 0, 9);
        return out9;
    }

    /** Every conversion matrix, computed once (the computation allocates); read by {@link #convert}. */
    private static final class Table {
        static final int GAMUTS = ColorGamut.values().length;
        static final int ADAPTATIONS = Adaptation.values().length;
        static final double[][] CONVERT = new double[GAMUTS * GAMUTS * ADAPTATIONS][];

        static {
            for (ColorGamut from : ColorGamut.values()) {
                for (ColorGamut to : ColorGamut.values()) {
                    for (Adaptation a : Adaptation.values()) {
                        CONVERT[index(from, to, a)] = compute(from, to, a, new double[9]);
                    }
                }
            }
        }

        static int index(ColorGamut from, ColorGamut to, Adaptation a) {
            return (from.ordinal() * GAMUTS + to.ordinal()) * ADAPTATIONS + a.ordinal();
        }
    }

    private static double[] compute(ColorGamut from, ColorGamut to, Adaptation adaptation, double[] out9) {
        if (from == to) {
            // exact: the published sRGB pair (ColorMath) is only inverse to about 1e-7
            System.arraycopy(new double[] {1, 0, 0, 0, 1, 0, 0, 0, 1}, 0, out9, 0, 9);
            return out9;
        }
        double[] m = TO_XYZ[from.ordinal()];
        double[] w0 = white(from);
        double[] w1 = white(to);
        if (adaptation != Adaptation.NONE && w0 != w1) {
            m = mul3(adaptationMatrix(w0, w1, adaptation == Adaptation.CAT16 ? CAT16_M : BRADFORD_M), m);
        }
        m = mul3(FROM_XYZ[to.ordinal()], m);
        System.arraycopy(m, 0, out9, 0, 9);
        return out9;
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
    public static Vector3d convert(Vector3dc rgb, ColorGamut from, ColorGamut to, Vector3d dest) {
        return convert(rgb, from, to, Adaptation.BRADFORD, dest);
    }

    /**
     * Converts linear RGB between gamuts with the given white-point adaptation, using
     * {@link #conversionMatrix(ColorGamut, ColorGamut, Adaptation, double[])}. Does not allocate.
     *
     * @param rgb the linear color in {@code from}
     * @param from the source gamut
     * @param to the target gamut
     * @param adaptation the white-point adaptation to use
     * @param dest receives the linear color in {@code to}; may be {@code rgb}
     * @return {@code dest}
     */
    public static Vector3d convert(Vector3dc rgb, ColorGamut from, ColorGamut to, Adaptation adaptation, Vector3d dest) {
        double[] m = Table.CONVERT[Table.index(from, to, adaptation)];
        double r = rgb.x(), g = rgb.y(), b = rgb.z();
        return dest.set(m[0] * r + m[1] * g + m[2] * b, m[3] * r + m[4] * g + m[5] * b, m[6] * r + m[7] * g + m[8] * b);
    }

    /**
     * Converts linear RGB in {@code gamut} to CIE XYZ relative to that gamut's own white point (D65 or the ACES white),
     * without adaptation; RGB white (1, 1, 1) gives Y = 1.
     *
     * @param rgb the linear color
     * @param gamut the gamut of {@code rgb}
     * @param dest receives X, Y and Z; may be {@code rgb}
     * @return {@code dest}
     */
    public static Vector3d toXyz(Vector3dc rgb, ColorGamut gamut, Vector3d dest) {
        double[] m = TO_XYZ[gamut.ordinal()];
        double r = rgb.x(), g = rgb.y(), b = rgb.z();
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
     */
    public static Vector3d fromXyz(Vector3dc xyz, ColorGamut gamut, Vector3d dest) {
        double[] m = FROM_XYZ[gamut.ordinal()];
        double x = xyz.x(), y = xyz.y(), z = xyz.z();
        return dest.set(m[0] * x + m[1] * y + m[2] * z, m[3] * x + m[4] * y + m[5] * z, m[6] * x + m[7] * y + m[8] * z);
    }

    /**
     * Writes the row-major RGB to XYZ matrix of {@code gamut} (the matrix {@link #toXyz} uses).
     *
     * @param gamut the gamut
     * @param out9 receives the nine matrix elements at indices 0 to 8
     * @return {@code out9}
     */
    public static double[] toXyzMatrix(ColorGamut gamut, double[] out9) {
        System.arraycopy(TO_XYZ[gamut.ordinal()], 0, out9, 0, 9);
        return out9;
    }

    /**
     * Writes the chromatic adaptation matrix (row-major) from white {@code srcWhite} to {@code dstWhite}, with the
     * given transform: {@code M⁻¹ diag(M dst / M src) M}, where {@code M} is the transform's cone-response matrix.
     *
     * @param srcWhite the source white point in XYZ
     * @param dstWhite the target white point in XYZ
     * @param adaptation the transform; {@link Adaptation#NONE} gives the identity
     * @param out9 receives the nine matrix elements at indices 0 to 8
     * @return {@code out9}
     */
    public static double[] adaptationMatrix(Vector3dc srcWhite, Vector3dc dstWhite, Adaptation adaptation,
            double[] out9) {
        double[] m = adaptation == Adaptation.NONE ? new double[] {1, 0, 0, 0, 1, 0, 0, 0, 1}
                : adaptationMatrix(new double[] {srcWhite.x(), srcWhite.y(), srcWhite.z()},
                        new double[] {dstWhite.x(), dstWhite.y(), dstWhite.z()},
                        adaptation == Adaptation.CAT16 ? CAT16_M : BRADFORD_M);
        System.arraycopy(m, 0, out9, 0, 9);
        return out9;
    }

    private static double[] adaptationMatrix(double[] src, double[] dst, double[] cone) {
        double[] s = mul(cone, src);
        double[] d = mul(cone, dst);
        double[] scale = {d[0] / s[0], 0, 0, 0, d[1] / s[1], 0, 0, 0, d[2] / s[2]};
        return mul3(invert(cone), mul3(scale, cone));
    }

    private static double[] mul(double[] m, double[] v) {
        return new double[] {m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2],
            m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    private static double[] mul3(double[] a, double[] b) {
        double[] r = new double[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[3 * i + j] = a[3 * i] * b[j] + a[3 * i + 1] * b[3 + j] + a[3 * i + 2] * b[6 + j];
            }
        }
        return r;
    }

    private static double[] invert(double[] m) {
        Matrix3d x = new Matrix3d(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]).invert();
        return new double[] {x.m00(), x.m10(), x.m20(), x.m01(), x.m11(), x.m21(), x.m02(), x.m12(), x.m22()};
    }
}
