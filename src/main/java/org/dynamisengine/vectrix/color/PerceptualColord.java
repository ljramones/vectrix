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

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;

/**
 * Perceptual color spaces and color differences (double precision): OKLab / OKLCH (Ottosson 2020), CIELAB / LCh
 * (D65, the white of {@link ColorMathd#linearSrgbToXyz}), HSV / HSL, CIEDE2000 and ΔE-OK. Hue angles are in degrees,
 * {@code [0, 360)}. Every {@code dest} may alias the input.
 *
 * @since 2.1.0
 */
public final class PerceptualColord {
    private static final double XN = 0.95047;
    private static final double ZN = 1.08883;
    private static final double LAB_E = 216.0 / 24389.0;
    private static final double LAB_K = 24389.0 / 27.0;
    private static final double RAD_TO_DEG = (180.0 / java.lang.Math.PI);
    private static final double DEG_TO_RAD = (java.lang.Math.PI / 180.0);

    private PerceptualColord() {
    }

    private static double cbrt(double x) {
        return AccurateMath.cbrt(x);
    }

    /**
     * Converts linear sRGB to OKLab (L, a, b), with Ottosson's linear-sRGB-to-LMS matrix. White (1, 1, 1) gives
     * L = 1, a = b = 0 up to rounding.
     *
     * @param rgb the linear sRGB color
     * @param dest receives (L, a, b); may be {@code rgb}
     * @return {@code dest}
     */
    public static Vector3d oklabFromLinearSrgb(Vector3dc rgb, Vector3d dest) {
        double r = rgb.x(), g = rgb.y(), b = rgb.z();
        double l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return lmsToOklab(l, m, s, dest);
    }

    /**
     * Converts OKLab to linear sRGB; the inverse of {@link #oklabFromLinearSrgb}. Colors outside the sRGB gamut give
     * channels outside [0, 1]; nothing is clamped.
     *
     * @param lab the OKLab color (L, a, b)
     * @param dest receives the linear sRGB color; may be {@code lab}
     * @return {@code dest}
     */
    public static Vector3d linearSrgbFromOklab(Vector3dc lab, Vector3d dest) {
        double ll = lab.x(), a = lab.y(), bb = lab.z();
        double l = ll + 0.3963377774 * a + 0.2158037573 * bb;
        double m = ll - 0.1055613458 * a - 0.0638541728 * bb;
        double s = ll - 0.0894841775 * a - 1.2914855480 * bb;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        return dest.set(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
                -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
                -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s);
    }

    /**
     * Converts CIE XYZ (D65, Y = 1 for white) to OKLab, with Ottosson's XYZ-to-LMS matrix.
     *
     * @param xyz the XYZ color
     * @param dest receives (L, a, b); may be {@code xyz}
     * @return {@code dest}
     */
    public static Vector3d oklabFromXyz(Vector3dc xyz, Vector3d dest) {
        double x = xyz.x(), y = xyz.y(), z = xyz.z();
        double l = cbrt(0.8189330101 * x + 0.3618667424 * y - 0.1288597137 * z);
        double m = cbrt(0.0329845436 * x + 0.9293118715 * y + 0.0361456387 * z);
        double s = cbrt(0.0482003018 * x + 0.2643662691 * y + 0.6338517070 * z);
        return lmsToOklab(l, m, s, dest);
    }

    private static Vector3d lmsToOklab(double l, double m, double s, Vector3d dest) {
        return dest.set(0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s);
    }

    /**
     * Converts a Lab-like triple to its cylindrical form (L, C, h): OKLab to OKLCH, or CIELAB to LCh. The hue h is in
     * degrees, in [0, 360); a gray (a = b = 0) gets hue 0.
     *
     * @param lab the color (L, a, b)
     * @param dest receives (L, chroma, hue in degrees); may be {@code lab}
     * @return {@code dest}
     */
    public static Vector3d labToLch(Vector3dc lab, Vector3d dest) {
        double l = lab.x(), a = lab.y(), b = lab.z();
        double h = AccurateMath.atan2(b, a) * RAD_TO_DEG;
        return dest.set(l, java.lang.Math.sqrt(a * a + b * b), wrapHue(h));
    }

    /**
     * Converts a cylindrical (L, C, h) triple back to Lab; the inverse of {@link #labToLch}.
     *
     * @param lch the color (L, chroma, hue in degrees); any hue angle is accepted
     * @param dest receives (L, a, b); may be {@code lch}
     * @return {@code dest}
     */
    public static Vector3d lchToLab(Vector3dc lch, Vector3d dest) {
        double l = lch.x(), c = lch.y(), h = lch.z() * DEG_TO_RAD;
        return dest.set(l, c * AccurateMath.cos(h), c * AccurateMath.sin(h));
    }

    /**
     * Converts CIE XYZ to CIELAB (L*, a*, b*) with the D65 white (0.95047, 1, 1.08883).
     *
     * @param xyz the XYZ color, Y = 1 for white
     * @param dest receives (L*, a*, b*), L* in [0, 100] for Y in [0, 1]; may be {@code xyz}
     * @return {@code dest}
     */
    public static Vector3d cielabFromXyz(Vector3dc xyz, Vector3d dest) {
        double fx = labF(xyz.x() / XN), fy = labF(xyz.y()), fz = labF(xyz.z() / ZN);
        return dest.set(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz));
    }

    /**
     * Converts CIELAB to CIE XYZ with the D65 white; the inverse of {@link #cielabFromXyz}.
     *
     * @param lab the CIELAB color (L*, a*, b*)
     * @param dest receives X, Y and Z, Y = 1 for white; may be {@code lab}
     * @return {@code dest}
     */
    public static Vector3d xyzFromCielab(Vector3dc lab, Vector3d dest) {
        double fy = (lab.x() + 16.0) / 116.0;
        double fx = fy + lab.y() / 500.0;
        double fz = fy - lab.z() / 200.0;
        return dest.set(XN * labFInv(fx), labFInv(fy), ZN * labFInv(fz));
    }

    private static double labF(double t) {
        return t > LAB_E ? cbrt(t) : (LAB_K * t + 16.0) / 116.0;
    }

    private static double labFInv(double f) {
        double f3 = f * f * f;
        return f3 > LAB_E ? f3 : (116.0 * f - 16.0) / LAB_K;
    }

    /**
     * Converts RGB to HSV. A gray gets hue 0 and black gets saturation 0.
     *
     * @param rgb the RGB color, channels in [0, 1], usually gamma-encoded
     * @param dest receives (hue in degrees in [0, 360), saturation, value); may be {@code rgb}
     * @return {@code dest}
     */
    public static Vector3d rgbToHsv(Vector3dc rgb, Vector3d dest) {
        double r = rgb.x(), g = rgb.y(), b = rgb.z();
        double max = java.lang.Math.max(r, java.lang.Math.max(g, b));
        double min = java.lang.Math.min(r, java.lang.Math.min(g, b));
        double d = max - min;
        return dest.set(hue(r, g, b, max, d), max > 0.0 ? d / max : 0.0, max);
    }

    /**
     * Converts HSV to RGB; the inverse of {@link #rgbToHsv}.
     *
     * @param hsv the color (hue in degrees, saturation, value); any hue, wrapped to [0, 360)
     * @param dest receives the RGB color; may be {@code hsv}
     * @return {@code dest}
     */
    public static Vector3d hsvToRgb(Vector3dc hsv, Vector3d dest) {
        double h = hsv.x(), s = hsv.y(), v = hsv.z();
        return dest.set(hsvChannel(5.0, h, s, v), hsvChannel(3.0, h, s, v), hsvChannel(1.0, h, s, v));
    }

    private static double hsvChannel(double n, double h, double s, double v) {
        double k = (n + h / 60.0) % 6.0;
        if (k < 0) {
            k += 6.0;
        }
        return v - v * s * java.lang.Math.max(0.0, java.lang.Math.min(k, java.lang.Math.min(4.0 - k, 1.0)));
    }

    /**
     * Converts RGB to HSL. A gray gets hue 0 and saturation 0.
     *
     * @param rgb the RGB color, channels in [0, 1], usually gamma-encoded
     * @param dest receives (hue in degrees in [0, 360), saturation, lightness); may be {@code rgb}
     * @return {@code dest}
     */
    public static Vector3d rgbToHsl(Vector3dc rgb, Vector3d dest) {
        double r = rgb.x(), g = rgb.y(), b = rgb.z();
        double max = java.lang.Math.max(r, java.lang.Math.max(g, b));
        double min = java.lang.Math.min(r, java.lang.Math.min(g, b));
        double d = max - min;
        double l = 0.5 * (max + min);
        double s = d == 0.0 ? 0.0 : d / (1.0 - java.lang.Math.abs(2.0 * l - 1.0));
        return dest.set(hue(r, g, b, max, d), s, l);
    }

    /**
     * Converts HSL to RGB; the inverse of {@link #rgbToHsl}.
     *
     * @param hsl the color (hue in degrees, saturation, lightness); any hue, wrapped to [0, 360)
     * @param dest receives the RGB color; may be {@code hsl}
     * @return {@code dest}
     */
    public static Vector3d hslToRgb(Vector3dc hsl, Vector3d dest) {
        double h = hsl.x(), s = hsl.y(), l = hsl.z();
        double a = s * java.lang.Math.min(l, 1.0 - l);
        return dest.set(hslChannel(0.0, h, a, l), hslChannel(8.0, h, a, l), hslChannel(4.0, h, a, l));
    }

    private static double hslChannel(double n, double h, double a, double l) {
        double k = (n + h / 30.0) % 12.0;
        if (k < 0) {
            k += 12.0;
        }
        return l - a * java.lang.Math.max(-1.0, java.lang.Math.min(k - 3.0, java.lang.Math.min(9.0 - k, 1.0)));
    }

    private static double hue(double r, double g, double b, double max, double d) {
        if (d == 0.0) {
            return 0.0;
        }
        double h;
        if (max == r) {
            h = 60.0 * (((g - b) / d) % 6.0);
        } else if (max == g) {
            h = 60.0 * ((b - r) / d + 2.0);
        } else {
            h = 60.0 * ((r - g) / d + 4.0);
        }
        return wrapHue(h);
    }

    /**
     * Returns the color difference ΔE-OK: the Euclidean distance between two OKLab colors.
     *
     * @param lab1 the first OKLab color
     * @param lab2 the second OKLab color
     * @return the distance, at least 0
     */
    public static double deltaEOk(Vector3dc lab1, Vector3dc lab2) {
        double dl = lab1.x() - lab2.x(), da = lab1.y() - lab2.y(), db = lab1.z() - lab2.z();
        return java.lang.Math.sqrt(dl * dl + da * da + db * db);
    }

    /**
     * Returns the CIEDE2000 color difference between two CIELAB colors, with {@code kL = kC = kH = 1}, following
     * Sharma, Wu and Dalal (2005), including their rules for hue differences and means when a chroma is 0.
     *
     * @param lab1 the first CIELAB color
     * @param lab2 the second CIELAB color
     * @return ΔE00, at least 0
     */
    public static double deltaE2000(Vector3dc lab1, Vector3dc lab2) {
        double l1 = lab1.x(), a1 = lab1.y(), b1 = lab1.z();
        double l2 = lab2.x(), a2 = lab2.y(), b2 = lab2.z();
        double c1 = java.lang.Math.sqrt(a1 * a1 + b1 * b1), c2 = java.lang.Math.sqrt(a2 * a2 + b2 * b2);
        double cb = 0.5 * (c1 + c2), cb7 = pow7(cb);
        double g = 0.5 * (1.0 - java.lang.Math.sqrt(cb7 / (cb7 + 6103515625.0)));
        double a1p = (1.0 + g) * a1, a2p = (1.0 + g) * a2;
        double c1p = java.lang.Math.sqrt(a1p * a1p + b1 * b1), c2p = java.lang.Math.sqrt(a2p * a2p + b2 * b2);
        double h1p = hueDeg(b1, a1p), h2p = hueDeg(b2, a2p);
        double dlp = l2 - l1, dcp = c2p - c1p;
        double dhp;
        if (c1p * c2p == 0.0) {
            dhp = 0.0;
        } else if (java.lang.Math.abs(h2p - h1p) <= 180.0) {
            dhp = h2p - h1p;
        } else {
            dhp = h2p - h1p > 180.0 ? h2p - h1p - 360.0 : h2p - h1p + 360.0;
        }
        double dHp = 2.0 * java.lang.Math.sqrt(c1p * c2p) * AccurateMath.sin(java.lang.Math.toRadians(dhp / 2.0));
        double lbp = 0.5 * (l1 + l2), cbp = 0.5 * (c1p + c2p);
        double hbp;
        if (c1p * c2p == 0.0) {
            hbp = h1p + h2p;
        } else if (java.lang.Math.abs(h1p - h2p) <= 180.0) {
            hbp = 0.5 * (h1p + h2p);
        } else {
            hbp = h1p + h2p < 360.0 ? 0.5 * (h1p + h2p + 360.0) : 0.5 * (h1p + h2p - 360.0);
        }
        double t = 1.0 - 0.17 * AccurateMath.cos(java.lang.Math.toRadians(hbp - 30.0))
                + 0.24 * AccurateMath.cos(java.lang.Math.toRadians(2.0 * hbp))
                + 0.32 * AccurateMath.cos(java.lang.Math.toRadians(3.0 * hbp + 6.0))
                - 0.20 * AccurateMath.cos(java.lang.Math.toRadians(4.0 * hbp - 63.0));
        double dTheta = 30.0 * AccurateMath.exp(-((hbp - 275.0) / 25.0) * ((hbp - 275.0) / 25.0));
        double cbp7 = pow7(cbp);
        double rc = 2.0 * java.lang.Math.sqrt(cbp7 / (cbp7 + 6103515625.0));
        double lb50 = (lbp - 50.0) * (lbp - 50.0);
        double sl = 1.0 + 0.015 * lb50 / java.lang.Math.sqrt(20.0 + lb50);
        double sc = 1.0 + 0.045 * cbp;
        double sh = 1.0 + 0.015 * cbp * t;
        double rt = -AccurateMath.sin(java.lang.Math.toRadians(2.0 * dTheta)) * rc;
        double x = dlp / sl, y = dcp / sc, z = dHp / sh;
        return java.lang.Math.sqrt(x * x + y * y + z * z + rt * y * z);
    }

    private static double pow7(double x) {
        double x2 = x * x;
        return x2 * x2 * x2 * x;
    }

    private static double hueDeg(double b, double a) {
        if (a == 0.0 && b == 0.0) {
            return 0.0;
        }
        double h = java.lang.Math.toDegrees(AccurateMath.atan2(b, a));
        return wrapHue(h);
    }

    /** {@code h} wrapped to [0, 360), with -0 as +0 (rounding of a tiny negative hue can otherwise give 360). */
    private static double wrapHue(double h) {
        double r = h % 360.0;
        if (r < 0) {
            r += 360.0;
        }
        if (r >= 360.0) {
            r -= 360.0;
        }
        return r + 0.0;
    }
}
