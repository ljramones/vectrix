/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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
package org.vectrix.optics;

import org.vectrix.fft.Complexf;

/**
 * Fresnel reflectance helpers (single-precision).
  * @since 1.0.0
  */
public final class Fresnelf {
    private Fresnelf() {
    }

    /**
     * Unpolarized dielectric Fresnel reflectance.
     *
     * @param cosThetaI cosine of incident angle in [0,1]
     */
    public static float dielectric(float cosThetaI, float etaI, float etaT) {
        if (etaI <= 0.0f || etaT <= 0.0f) {
            throw new IllegalArgumentException("eta");
        }
        float c = clamp01(cosThetaI);
        float eta = etaI / etaT;
        float sin2T = eta * eta * (1.0f - c * c);
        if (sin2T >= 1.0f) {
            return 1.0f;
        }
        float cosThetaT = (float) java.lang.Math.sqrt(1.0f - sin2T);
        float rs = (etaI * c - etaT * cosThetaT) / (etaI * c + etaT * cosThetaT);
        float rp = (etaT * c - etaI * cosThetaT) / (etaT * c + etaI * cosThetaT);
        return 0.5f * (rs * rs + rp * rp);
    }

    /**
     * Unpolarized conductor Fresnel reflectance with complex relative IOR {@code n + i k}.
     * The incident medium is assumed to have IOR 1.
     */
    public static float conductor(float cosThetaI, Complexf eta) {
        return conductor(cosThetaI, eta.real, eta.imag);
    }

    /**
     * Unpolarized conductor Fresnel reflectance with complex relative IOR {@code n + i k}.
     * The incident medium is assumed to have IOR 1.
     */
    public static float conductor(float cosThetaI, float n, float k) {
        if (n < 0.0f || k < 0.0f) {
            throw new IllegalArgumentException("n/k");
        }
        float c = clamp01(cosThetaI);
        float c2 = c * c;
        float n2k2 = n * n + k * k;

        float rsNum = n2k2 - 2.0f * n * c + c2;
        float rsDen = n2k2 + 2.0f * n * c + c2;
        float rs = rsNum / rsDen;

        float rpNum = n2k2 * c2 - 2.0f * n * c + 1.0f;
        float rpDen = n2k2 * c2 + 2.0f * n * c + 1.0f;
        float rp = rpNum / rpDen;
        return 0.5f * (rs + rp);
    }

    static float amplitudeS(float cosThetaI, float etaI, float etaT, float cosThetaT) {
        return (etaI * cosThetaI - etaT * cosThetaT) / (etaI * cosThetaI + etaT * cosThetaT);
    }

    static float amplitudeP(float cosThetaI, float etaI, float etaT, float cosThetaT) {
        return (etaT * cosThetaI - etaI * cosThetaT) / (etaT * cosThetaI + etaI * cosThetaT);
    }

    static float clamp01(float v) {
        return java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, v));
    }
}
