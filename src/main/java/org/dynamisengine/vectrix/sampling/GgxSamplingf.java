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

import org.dynamisengine.vectrix.brdf.Microfacetf;
import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.core.Vector3f;

/**
 * GGX visible-normal (VNDF) importance sampling (single precision): samples microfacet normals in proportion to
 * their projected area towards the viewer, {@code D_v(h) = G1(v) max(0, v·h) D(h) / (n·v)} (Heitz 2018), using the
 * spherical-cap construction of Dupuy and Benyoub (2023). Vectors are in the local shading frame with the normal on
 * {@code +z}; {@code (vx, vy, vz)} is the unit view direction with {@code vz > 0}.
 *
 * @since 2.1.0
 */
public final class GgxSamplingf {
    private GgxSamplingf() {
    }

    /**
     * Samples a visible microfacet normal for anisotropic GGX widths {@code alphaX}, {@code alphaY}
     * ({@code alphaX == alphaY} for isotropic GGX). The reflected direction is {@code 2 (v·h) h - v}.
     */
    public static Vector3f sampleVisibleNormal(float vx, float vy, float vz, float alphaX, float alphaY, float u1,
            float u2, Vector3f dest) {
        // stretch the view direction into the hemisphere configuration
        float sx = alphaX * vx;
        float sy = alphaY * vy;
        float sz = vz;
        float inv = 1.0f / (float) java.lang.Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx *= inv;
        sy *= inv;
        sz *= inv;
        // sample the spherical cap z in (-sz, 1]
        float phi = 2.0f * (float) java.lang.Math.PI * u1;
        float z = (1.0f - u2) * (1.0f + sz) - sz;
        float sinTheta = (float) java.lang.Math.sqrt(java.lang.Math.min(1.0f, java.lang.Math.max(0.0f, 1.0f - z * z)));
        float hx = sinTheta * (float) AccurateMath.cos(phi) + sx;
        float hy = sinTheta * (float) AccurateMath.sin(phi) + sy;
        float hz = z + sz;
        // unstretch back to the ellipsoid configuration
        hx *= alphaX;
        hy *= alphaY;
        hz = java.lang.Math.max(0.0f, hz);
        float invH = 1.0f / (float) java.lang.Math.sqrt(hx * hx + hy * hy + hz * hz);
        return dest.set(hx * invH, hy * invH, hz * invH);
    }

    /**
     * Density over half vectors of {@link #sampleVisibleNormal} for isotropic GGX:
     * {@code G1(v) max(0, v·h) D(h) / (n·v)}.
     */
    public static float pdfVisibleNormal(float nDotV, float vDotH, float nDotH, float alpha) {
        if (nDotV <= 0.0f || vDotH <= 0.0f) {
            return 0.0f;
        }
        return Microfacetf.g1Smith(nDotV, alpha) * vDotH * Microfacetf.dGgx(nDotH, alpha) / nDotV;
    }

    /**
     * Density over reflected directions {@code l = 2 (v·h) h - v} when {@code h} comes from
     * {@link #sampleVisibleNormal} (isotropic): {@code D_v(h) / (4 v·h) = G1(v) D(h) / (4 n·v)}.
     */
    public static float pdfReflected(float nDotV, float vDotH, float nDotH, float alpha) {
        if (vDotH <= 0.0f) {
            return 0.0f;
        }
        return pdfVisibleNormal(nDotV, vDotH, nDotH, alpha) / (4.0f * vDotH);
    }

    /**
     * Density over half vectors for anisotropic GGX, with {@code (hx, hy, hz)} the unit half vector and
     * {@code (vx, vy, vz)} the unit view direction in the tangent frame.
     */
    public static float pdfVisibleNormalAnisotropic(float vx, float vy, float vz, float hx, float hy, float hz,
            float alphaX, float alphaY) {
        float vDotH = vx * hx + vy * hy + vz * hz;
        if (vz <= 0.0f || vDotH <= 0.0f) {
            return 0.0f;
        }
        float lambda = 0.5f * ((float) java.lang.Math.sqrt(1.0f
                + (alphaX * alphaX * vx * vx + alphaY * alphaY * vy * vy) / (vz * vz)) - 1.0f);
        float g1 = 1.0f / (1.0f + lambda);
        return g1 * vDotH * Microfacetf.dGgxAnisotropic(hz, hx, hy, alphaX, alphaY) / vz;
    }
}
