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
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Perceptual colour spaces and colour differences (single precision): OKLab / OKLCH (Ottosson 2020), CIELAB / LCh
 * (D65, the white of {@link ColorMathf#linearSrgbToXyz}), HSV / HSL, CIEDE2000 and ΔE-OK. Hue angles are in degrees,
 * {@code [0, 360)}. Every {@code dest} may alias the input.
 *
 * @since 2.1.0
 */
public final class PerceptualColorf {
    private static final float XN = 0.95047f;
    private static final float ZN = 1.08883f;
    private static final float LAB_E = 216.0f / 24389.0f;
    private static final float LAB_K = 24389.0f / 27.0f;
    private static final float RAD_TO_DEG = (float) (180.0 / java.lang.Math.PI);
    private static final float DEG_TO_RAD = (float) (java.lang.Math.PI / 180.0);

    private PerceptualColorf() {
    }

    private static float cbrt(float x) {
        return (float) AccurateMath.cbrt(x);
    }

    /** OKLab from linear sRGB. */
    public static Vector3f oklabFromLinearSrgb(Vector3fc rgb, Vector3f dest) {
        float r = rgb.x(), g = rgb.y(), b = rgb.z();
        float l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b);
        float m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b);
        float s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b);
        return lmsToOklab(l, m, s, dest);
    }

    /** Linear sRGB from OKLab. */
    public static Vector3f linearSrgbFromOklab(Vector3fc lab, Vector3f dest) {
        float ll = lab.x(), a = lab.y(), bb = lab.z();
        float l = ll + 0.3963377774f * a + 0.2158037573f * bb;
        float m = ll - 0.1055613458f * a - 0.0638541728f * bb;
        float s = ll - 0.0894841775f * a - 1.2914855480f * bb;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        return dest.set(4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
                -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
                -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s);
    }

    /** OKLab from CIE XYZ (D65), with Ottosson's XYZ-to-LMS matrix. */
    public static Vector3f oklabFromXyz(Vector3fc xyz, Vector3f dest) {
        float x = xyz.x(), y = xyz.y(), z = xyz.z();
        float l = cbrt(0.8189330101f * x + 0.3618667424f * y - 0.1288597137f * z);
        float m = cbrt(0.0329845436f * x + 0.9293118715f * y + 0.0361456387f * z);
        float s = cbrt(0.0482003018f * x + 0.2643662691f * y + 0.6338517070f * z);
        return lmsToOklab(l, m, s, dest);
    }

    private static Vector3f lmsToOklab(float l, float m, float s, Vector3f dest) {
        return dest.set(0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
                1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
                0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s);
    }

    /** Cylindrical form (L, C, h°) of a Lab-like triple (OKLab → OKLCH, CIELAB → LCh). */
    public static Vector3f labToLch(Vector3fc lab, Vector3f dest) {
        float l = lab.x(), a = lab.y(), b = lab.z();
        float h = (float) AccurateMath.atan2(b, a) * RAD_TO_DEG;
        return dest.set(l, (float) java.lang.Math.sqrt(a * a + b * b), h < 0.0f ? h + 360.0f : h);
    }

    /** Inverse of {@link #labToLch}. */
    public static Vector3f lchToLab(Vector3fc lch, Vector3f dest) {
        float l = lch.x(), c = lch.y(), h = lch.z() * DEG_TO_RAD;
        return dest.set(l, c * (float) AccurateMath.cos(h), c * (float) AccurateMath.sin(h));
    }

    /** CIELAB (L* in [0, 100]) from CIE XYZ, D65 white. */
    public static Vector3f cielabFromXyz(Vector3fc xyz, Vector3f dest) {
        float fx = labF(xyz.x() / XN), fy = labF(xyz.y()), fz = labF(xyz.z() / ZN);
        return dest.set(116.0f * fy - 16.0f, 500.0f * (fx - fy), 200.0f * (fy - fz));
    }

    /** CIE XYZ from CIELAB, D65 white. */
    public static Vector3f xyzFromCielab(Vector3fc lab, Vector3f dest) {
        float fy = (lab.x() + 16.0f) / 116.0f;
        float fx = fy + lab.y() / 500.0f;
        float fz = fy - lab.z() / 200.0f;
        return dest.set(XN * labFInv(fx), labFInv(fy), ZN * labFInv(fz));
    }

    private static float labF(float t) {
        return t > LAB_E ? cbrt(t) : (LAB_K * t + 16.0f) / 116.0f;
    }

    private static float labFInv(float f) {
        float f3 = f * f * f;
        return f3 > LAB_E ? f3 : (116.0f * f - 16.0f) / LAB_K;
    }

    /** HSV (h°, s, v) from RGB in [0, 1] (usually gamma-encoded). */
    public static Vector3f rgbToHsv(Vector3fc rgb, Vector3f dest) {
        float r = rgb.x(), g = rgb.y(), b = rgb.z();
        float max = java.lang.Math.max(r, java.lang.Math.max(g, b));
        float min = java.lang.Math.min(r, java.lang.Math.min(g, b));
        float d = max - min;
        return dest.set(hue(r, g, b, max, d), max > 0.0f ? d / max : 0.0f, max);
    }

    /** RGB from HSV. */
    public static Vector3f hsvToRgb(Vector3fc hsv, Vector3f dest) {
        float h = hsv.x(), s = hsv.y(), v = hsv.z();
        return dest.set(hsvChannel(5.0f, h, s, v), hsvChannel(3.0f, h, s, v), hsvChannel(1.0f, h, s, v));
    }

    private static float hsvChannel(float n, float h, float s, float v) {
        float k = (n + h / 60.0f) % 6.0f;
        return v - v * s * java.lang.Math.max(0.0f, java.lang.Math.min(k, java.lang.Math.min(4.0f - k, 1.0f)));
    }

    /** HSL (h°, s, l) from RGB in [0, 1]. */
    public static Vector3f rgbToHsl(Vector3fc rgb, Vector3f dest) {
        float r = rgb.x(), g = rgb.y(), b = rgb.z();
        float max = java.lang.Math.max(r, java.lang.Math.max(g, b));
        float min = java.lang.Math.min(r, java.lang.Math.min(g, b));
        float d = max - min;
        float l = 0.5f * (max + min);
        float s = d == 0.0f ? 0.0f : d / (1.0f - java.lang.Math.abs(2.0f * l - 1.0f));
        return dest.set(hue(r, g, b, max, d), s, l);
    }

    /** RGB from HSL. */
    public static Vector3f hslToRgb(Vector3fc hsl, Vector3f dest) {
        float h = hsl.x(), s = hsl.y(), l = hsl.z();
        float a = s * java.lang.Math.min(l, 1.0f - l);
        return dest.set(hslChannel(0.0f, h, a, l), hslChannel(8.0f, h, a, l), hslChannel(4.0f, h, a, l));
    }

    private static float hslChannel(float n, float h, float a, float l) {
        float k = (n + h / 30.0f) % 12.0f;
        return l - a * java.lang.Math.max(-1.0f, java.lang.Math.min(k - 3.0f, java.lang.Math.min(9.0f - k, 1.0f)));
    }

    private static float hue(float r, float g, float b, float max, float d) {
        if (d == 0.0f) {
            return 0.0f;
        }
        float h;
        if (max == r) {
            h = 60.0f * (((g - b) / d) % 6.0f);
        } else if (max == g) {
            h = 60.0f * ((b - r) / d + 2.0f);
        } else {
            h = 60.0f * ((r - g) / d + 4.0f);
        }
        return h < 0.0f ? h + 360.0f : h;
    }

    /** Colour difference ΔE-OK: the Euclidean distance between two OKLab colours. */
    public static float deltaEOk(Vector3fc lab1, Vector3fc lab2) {
        float dl = lab1.x() - lab2.x(), da = lab1.y() - lab2.y(), db = lab1.z() - lab2.z();
        return (float) java.lang.Math.sqrt(dl * dl + da * da + db * db);
    }

    /** CIEDE2000 colour difference between two CIELAB colours ({@code kL = kC = kH = 1}; Sharma et al. 2005). */
    public static float deltaE2000(Vector3fc lab1, Vector3fc lab2) {
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
        return (float) java.lang.Math.sqrt(x * x + y * y + z * z + rt * y * z);
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
        return h < 0.0 ? h + 360.0 : h;
    }
}
