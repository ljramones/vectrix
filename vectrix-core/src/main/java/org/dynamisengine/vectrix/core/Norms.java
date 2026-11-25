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
package org.dynamisengine.vectrix.core;

/**
 * Overflow- and underflow-safe Euclidean norms and angles for the vector classes. The fast path is the plain sum of
 * squares (bit-identical to the classic formula) whenever that sum is safely inside the double range; otherwise the
 * components are scaled by a power of two (exact) first, so lengths of vectors like {@code (1e200, 1e200, 0)} or
 * {@code (1e-170, 0, 0)} are correct instead of infinite or zero. Angles use {@code atan2} forms, accurate at every
 * size of angle, where {@code acos} of a dot product loses everything below about 1e-8 rad.
 */
final class Norms {
    private static final double LO = 0x1p-1000;
    private static final double HI = 0x1p+1000;

    private Norms() {
    }

    private static int scaleExponent(double m) {
        return -java.lang.Math.getExponent(m);
    }

    static double norm2(double x, double y) {
        double s = x * x + y * y;
        if (s >= LO && s <= HI) {
            return java.lang.Math.sqrt(s);
        }
        double m = java.lang.Math.max(java.lang.Math.abs(x), java.lang.Math.abs(y));
        if (m == 0.0 || !Double.isFinite(m)) {
            return Double.isNaN(s) ? s : m;
        }
        int e = scaleExponent(m);
        double a = java.lang.Math.scalb(x, e), b = java.lang.Math.scalb(y, e);
        return java.lang.Math.scalb(java.lang.Math.sqrt(a * a + b * b), -e);
    }

    static double norm3(double x, double y, double z) {
        double s = java.lang.Math.fma(x, x, java.lang.Math.fma(y, y, z * z));
        if (s >= LO && s <= HI) {
            return java.lang.Math.sqrt(s);
        }
        double m = java.lang.Math.max(java.lang.Math.abs(x), java.lang.Math.max(java.lang.Math.abs(y), java.lang.Math.abs(z)));
        if (m == 0.0 || !Double.isFinite(m)) {
            return Double.isNaN(s) ? s : m;
        }
        int e = scaleExponent(m);
        double a = java.lang.Math.scalb(x, e), b = java.lang.Math.scalb(y, e), c = java.lang.Math.scalb(z, e);
        return java.lang.Math.scalb(java.lang.Math.sqrt(a * a + b * b + c * c), -e);
    }

    static double norm4(double x, double y, double z, double w) {
        double s = java.lang.Math.fma(x, x, java.lang.Math.fma(y, y, java.lang.Math.fma(z, z, w * w)));
        if (s >= LO && s <= HI) {
            return java.lang.Math.sqrt(s);
        }
        double m = java.lang.Math.max(java.lang.Math.max(java.lang.Math.abs(x), java.lang.Math.abs(y)),
                java.lang.Math.max(java.lang.Math.abs(z), java.lang.Math.abs(w)));
        if (m == 0.0 || !Double.isFinite(m)) {
            return Double.isNaN(s) ? s : m;
        }
        int e = scaleExponent(m);
        double a = java.lang.Math.scalb(x, e), b = java.lang.Math.scalb(y, e), c = java.lang.Math.scalb(z, e);
        double d = java.lang.Math.scalb(w, e);
        return java.lang.Math.scalb(java.lang.Math.sqrt(a * a + b * b + c * c + d * d), -e);
    }

    /** Exponent that brings the largest component of a vector near 1 (0 for zero or non-finite vectors). */
    private static int exp3(double x, double y, double z) {
        double m = java.lang.Math.max(java.lang.Math.abs(x), java.lang.Math.max(java.lang.Math.abs(y), java.lang.Math.abs(z)));
        return m == 0.0 || !Double.isFinite(m) ? 0 : scaleExponent(m);
    }

    private static int exp4(double x, double y, double z, double w) {
        double m = java.lang.Math.max(java.lang.Math.max(java.lang.Math.abs(x), java.lang.Math.abs(y)),
                java.lang.Math.max(java.lang.Math.abs(z), java.lang.Math.abs(w)));
        return m == 0.0 || !Double.isFinite(m) ? 0 : scaleExponent(m);
    }

    /** Cosine of the angle between 3-vectors, safe for any magnitudes. */
    static double cos3(double ax, double ay, double az, double bx, double by, double bz) {
        int ea = exp3(ax, ay, az), eb = exp3(bx, by, bz);
        ax = java.lang.Math.scalb(ax, ea);
        ay = java.lang.Math.scalb(ay, ea);
        az = java.lang.Math.scalb(az, ea);
        bx = java.lang.Math.scalb(bx, eb);
        by = java.lang.Math.scalb(by, eb);
        bz = java.lang.Math.scalb(bz, eb);
        double dot = ax * bx + ay * by + az * bz;
        return dot / (java.lang.Math.sqrt(ax * ax + ay * ay + az * az) * java.lang.Math.sqrt(bx * bx + by * by + bz * bz));
    }

    /** Angle between 3-vectors, {@code atan2(|a × b|, a · b)}: accurate at every angle and magnitude. */
    static double angle3(double ax, double ay, double az, double bx, double by, double bz) {
        int ea = exp3(ax, ay, az), eb = exp3(bx, by, bz);
        ax = java.lang.Math.scalb(ax, ea);
        ay = java.lang.Math.scalb(ay, ea);
        az = java.lang.Math.scalb(az, ea);
        bx = java.lang.Math.scalb(bx, eb);
        by = java.lang.Math.scalb(by, eb);
        bz = java.lang.Math.scalb(bz, eb);
        double cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
        return AccurateMath.atan2(java.lang.Math.sqrt(cx * cx + cy * cy + cz * cz), ax * bx + ay * by + az * bz);
    }

    /** Angle between 2-vectors (signed, as {@code atan2(a × b, a · b)}), safe for any magnitudes. */
    static double angle2(double ax, double ay, double bx, double by) {
        int ea = exp3(ax, ay, 0.0), eb = exp3(bx, by, 0.0);
        ax = java.lang.Math.scalb(ax, ea);
        ay = java.lang.Math.scalb(ay, ea);
        bx = java.lang.Math.scalb(bx, eb);
        by = java.lang.Math.scalb(by, eb);
        return AccurateMath.atan2(ax * by - ay * bx, ax * bx + ay * by);
    }

    /** Cosine of the angle between 4-vectors, safe for any magnitudes. */
    static double cos4(double ax, double ay, double az, double aw, double bx, double by, double bz, double bw) {
        int ea = exp4(ax, ay, az, aw), eb = exp4(bx, by, bz, bw);
        ax = java.lang.Math.scalb(ax, ea);
        ay = java.lang.Math.scalb(ay, ea);
        az = java.lang.Math.scalb(az, ea);
        aw = java.lang.Math.scalb(aw, ea);
        bx = java.lang.Math.scalb(bx, eb);
        by = java.lang.Math.scalb(by, eb);
        bz = java.lang.Math.scalb(bz, eb);
        bw = java.lang.Math.scalb(bw, eb);
        double dot = ax * bx + ay * by + az * bz + aw * bw;
        return dot / (java.lang.Math.sqrt(ax * ax + ay * ay + az * az + aw * aw)
                * java.lang.Math.sqrt(bx * bx + by * by + bz * bz + bw * bw));
    }

    /** Angle between 4-vectors, {@code 2 atan2(|â − b̂|, |â + b̂|)}: accurate at every angle and magnitude. */
    static double angle4(double ax, double ay, double az, double aw, double bx, double by, double bz, double bw) {
        double la = norm4(ax, ay, az, aw), lb = norm4(bx, by, bz, bw);
        ax /= la;
        ay /= la;
        az /= la;
        aw /= la;
        bx /= lb;
        by /= lb;
        bz /= lb;
        bw /= lb;
        double d = norm4(ax - bx, ay - by, az - bz, aw - bw), s = norm4(ax + bx, ay + by, az + bz, aw + bw);
        return 2.0 * AccurateMath.atan2(d, s);
    }
}
