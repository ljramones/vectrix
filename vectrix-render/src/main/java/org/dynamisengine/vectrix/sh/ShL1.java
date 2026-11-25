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
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;

/**
 * L1 fast paths: diffuse irradiance from the first four coefficients (bands 0 and 1) of radiance coefficients in
 * the {@link ShBasis} convention, read from an L2 set (its band-2 terms are ignored).
 * <ul>
 * <li>{@link #irradianceLinear}: the Lambert-convolved L1 reconstruction {@code E(n) = R0 + G · n}, cheap but
 * negative opposite strong lights.</li>
 * <li>{@link #irradianceNonLinear}: Hazel's non-linear reconstruction (Geomerics, "Reconstructing Diffuse Lighting
 * from Spherical Harmonic Data", 2017), {@code E = R0 (a + (1 - a)(p + 1) q^p)} with {@code q = (1 + n · r̂) / 2},
 * {@code r = |G| / (2 R0)}, {@code p = 1 + 2 r}, {@code a = (1 - r) / (1 + r)}. It keeps the mean irradiance
 * {@code R0} exactly, agrees with the linear form to first order in {@code r}, and is never negative ({@code r} is
 * clamped to 1).</li>
 * </ul>
 * Here {@code R0 = π Y0 c0} and {@code G = (2π / 3) Y1 (c3, c1, c2)} per channel.
 *
 * @since 2.3.0
 */
public final class ShL1 {
    private static final double K0 = java.lang.Math.PI * ShBasis.D0;
    private static final double K1 = 2.0 * java.lang.Math.PI / 3.0 * ShBasis.D1;

    private ShL1() {
    }

    private static double linear(double c0, double cy, double cz, double cx, double nx, double ny, double nz) {
        return K0 * c0 + K1 * (cx * nx + cy * ny + cz * nz);
    }

    private static double nonLinear(double c0, double cy, double cz, double cx, double nx, double ny, double nz) {
        double r0 = K0 * c0;
        if (!(r0 > 0.0)) {
            return 0.0;
        }
        double gx = K1 * cx, gy = K1 * cy, gz = K1 * cz;
        double len = java.lang.Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (len == 0.0) {
            return r0;
        }
        double r = java.lang.Math.min(0.5 * len / r0, 1.0);
        double q = 0.5 * (1.0 + (gx * nx + gy * ny + gz * nz) / len);
        double p = 1.0 + 2.0 * r;
        double a = (1.0 - r) / (1.0 + r);
        return r0 * (a + (1.0 - a) * (p + 1.0) * AccurateMath.pow(java.lang.Math.max(q, 0.0), p));
    }

    /**
     * Returns the linear L1 irradiance {@code R0 + G · n} at a normal (see the class comment); it can be
     * negative.
     *
     * @param radiance radiance coefficients; only coefficients 0..3 are read
     * @param nx the x component of the unit normal; it is not normalized
     * @param ny the y component of the unit normal
     * @param nz the z component of the unit normal
     * @param dest receives the RGB irradiance
     * @return {@code dest}
     */
    public static Vector3f irradianceLinear(ShCoeffs9f radiance, float nx, float ny, float nz, Vector3f dest) {
        float[] c = radiance.c;
        return dest.set((float) linear(c[0], c[3], c[6], c[9], nx, ny, nz),
                (float) linear(c[1], c[4], c[7], c[10], nx, ny, nz),
                (float) linear(c[2], c[5], c[8], c[11], nx, ny, nz));
    }

    /**
     * Returns the non-linear (Hazel) L1 irradiance at a normal (see the class comment); never negative.
     * Zero for a channel whose {@code R0} is not positive.
     *
     * @param radiance radiance coefficients; only coefficients 0..3 are read
     * @param nx the x component of the unit normal; it is not normalized
     * @param ny the y component of the unit normal
     * @param nz the z component of the unit normal
     * @param dest receives the RGB irradiance
     * @return {@code dest}
     */
    public static Vector3f irradianceNonLinear(ShCoeffs9f radiance, float nx, float ny, float nz, Vector3f dest) {
        float[] c = radiance.c;
        return dest.set((float) nonLinear(c[0], c[3], c[6], c[9], nx, ny, nz),
                (float) nonLinear(c[1], c[4], c[7], c[10], nx, ny, nz),
                (float) nonLinear(c[2], c[5], c[8], c[11], nx, ny, nz));
    }

    /**
     * Double-precision version: returns the linear L1 irradiance {@code R0 + G · n} at a normal (see the class
     * comment); it can be negative.
     *
     * @param radiance radiance coefficients; only coefficients 0..3 are read
     * @param nx the x component of the unit normal; it is not normalized
     * @param ny the y component of the unit normal
     * @param nz the z component of the unit normal
     * @param dest receives the RGB irradiance
     * @return {@code dest}
     */
    public static Vector3d irradianceLinear(ShCoeffs9d radiance, double nx, double ny, double nz, Vector3d dest) {
        double[] c = radiance.c;
        return dest.set(linear(c[0], c[3], c[6], c[9], nx, ny, nz), linear(c[1], c[4], c[7], c[10], nx, ny, nz),
                linear(c[2], c[5], c[8], c[11], nx, ny, nz));
    }

    /**
     * Double-precision version: returns the non-linear (Hazel) L1 irradiance at a normal (see the class comment);
     * never negative. Zero for a channel whose {@code R0} is not positive.
     *
     * @param radiance radiance coefficients; only coefficients 0..3 are read
     * @param nx the x component of the unit normal; it is not normalized
     * @param ny the y component of the unit normal
     * @param nz the z component of the unit normal
     * @param dest receives the RGB irradiance
     * @return {@code dest}
     */
    public static Vector3d irradianceNonLinear(ShCoeffs9d radiance, double nx, double ny, double nz, Vector3d dest) {
        double[] c = radiance.c;
        return dest.set(nonLinear(c[0], c[3], c[6], c[9], nx, ny, nz),
                nonLinear(c[1], c[4], c[7], c[10], nx, ny, nz), nonLinear(c[2], c[5], c[8], c[11], nx, ny, nz));
    }
}
