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
package org.dynamisengine.vectrix.sampling;

import org.dynamisengine.vectrix.brdf.Microfacetd;
import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.core.Vector3d;

/**
 * GGX visible-normal (VNDF) importance sampling (double precision): samples microfacet normals in proportion to
 * their projected area towards the viewer, {@code D_v(h) = G1(v) max(0, v·h) D(h) / (n·v)} (Heitz 2018), using the
 * spherical-cap construction of Dupuy and Benyoub (2023). Vectors are in the local shading frame with the normal on
 * {@code +z}; {@code (vx, vy, vz)} is the unit view direction with {@code vz > 0}.
 *
 * @since 2.1.0
 */
public final class GgxSamplingd {
    private GgxSamplingd() {
    }

    /**
     * Samples a visible microfacet normal for anisotropic GGX widths {@code alphaX}, {@code alphaY}
     * ({@code alphaX == alphaY} for isotropic GGX). The reflected direction is {@code 2 (v·h) h - v}.
     */
    public static Vector3d sampleVisibleNormal(double vx, double vy, double vz, double alphaX, double alphaY, double u1,
            double u2, Vector3d dest) {
        // stretch the view direction into the hemisphere configuration
        double sx = alphaX * vx;
        double sy = alphaY * vy;
        double sz = vz;
        double inv = 1.0 / java.lang.Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx *= inv;
        sy *= inv;
        sz *= inv;
        // sample the spherical cap z in (-sz, 1]
        double phi = 2.0 * java.lang.Math.PI * u1;
        double z = (1.0 - u2) * (1.0 + sz) - sz;
        double sinTheta = java.lang.Math.sqrt(java.lang.Math.min(1.0, java.lang.Math.max(0.0, 1.0 - z * z)));
        double hx = sinTheta * AccurateMath.cos(phi) + sx;
        double hy = sinTheta * AccurateMath.sin(phi) + sy;
        double hz = z + sz;
        // unstretch back to the ellipsoid configuration
        hx *= alphaX;
        hy *= alphaY;
        hz = java.lang.Math.max(0.0, hz);
        double invH = 1.0 / java.lang.Math.sqrt(hx * hx + hy * hy + hz * hz);
        return dest.set(hx * invH, hy * invH, hz * invH);
    }

    /**
     * Density over half vectors of {@link #sampleVisibleNormal} for isotropic GGX:
     * {@code G1(v) max(0, v·h) D(h) / (n·v)}.
     */
    public static double pdfVisibleNormal(double nDotV, double vDotH, double nDotH, double alpha) {
        if (nDotV <= 0.0 || vDotH <= 0.0) {
            return 0.0;
        }
        return Microfacetd.g1Smith(nDotV, alpha) * vDotH * Microfacetd.dGgx(nDotH, alpha) / nDotV;
    }

    /**
     * Density over reflected directions {@code l = 2 (v·h) h - v} when {@code h} comes from
     * {@link #sampleVisibleNormal} (isotropic): {@code D_v(h) / (4 v·h) = G1(v) D(h) / (4 n·v)}.
     */
    public static double pdfReflected(double nDotV, double vDotH, double nDotH, double alpha) {
        if (vDotH <= 0.0) {
            return 0.0;
        }
        return pdfVisibleNormal(nDotV, vDotH, nDotH, alpha) / (4.0 * vDotH);
    }

    /**
     * Density over half vectors for anisotropic GGX, with {@code (hx, hy, hz)} the unit half vector and
     * {@code (vx, vy, vz)} the unit view direction in the tangent frame.
     */
    public static double pdfVisibleNormalAnisotropic(double vx, double vy, double vz, double hx, double hy, double hz,
            double alphaX, double alphaY) {
        double vDotH = vx * hx + vy * hy + vz * hz;
        if (vz <= 0.0 || vDotH <= 0.0) {
            return 0.0;
        }
        double lambda = 0.5 * (java.lang.Math.sqrt(1.0
                + (alphaX * alphaX * vx * vx + alphaY * alphaY * vy * vy) / (vz * vz)) - 1.0);
        double g1 = 1.0 / (1.0 + lambda);
        return g1 * vDotH * Microfacetd.dGgxAnisotropic(hz, hx, hy, alphaX, alphaY) / vz;
    }
}
