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
package org.dynamisengine.vectrix.optics;

import org.dynamisengine.vectrix.core.Complexf;

/**
 * Fresnel reflectance helpers (single-precision).
  * @since 1.0.0
  */
public final class Fresnelf {
    private Fresnelf() {
    }

    /**
     * Returns the unpolarized Fresnel reflectance of a smooth dielectric interface, the mean of the s and p
     * reflectances. Returns 1 under total internal reflection.
     *
     * @param cosThetaI cosine of the incident angle; a negative value means the ray arrives from the {@code etaT}
     *                  side, and the indices are swapped. Values beyond 1 in magnitude are clamped.
     * @param etaI the refractive index of the incident medium, greater than 0
     * @param etaT the refractive index of the transmitting medium, greater than 0
     * @return the reflectance, in {@code [0, 1]}
     * @throws IllegalArgumentException if {@code etaI} or {@code etaT} is not greater than 0
     */
    public static float dielectric(float cosThetaI, float etaI, float etaT) {
        if (etaI <= 0.0f || etaT <= 0.0f) {
            throw new IllegalArgumentException("eta");
        }
        if (cosThetaI < 0.0f) {
            // Leaving the medium: swap the indices, as in pbrt (it used to clamp to grazing incidence and return ~1).
            float tmp = etaI;
            etaI = etaT;
            etaT = tmp;
            cosThetaI = -cosThetaI;
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
     * Returns the exact unpolarized Fresnel reflectance of a conductor with complex relative IOR {@code n + i k}.
     * The incident medium is assumed to have IOR 1. Same as {@link #conductor(float, float, float)}.
     *
     * @param cosThetaI cosine of the incident angle, clamped to {@code [0, 1]}
     * @param eta the complex IOR: {@code n} in the real part and the extinction {@code k} in the imaginary part
     * @return the reflectance, in {@code [0, 1]}
     * @throws IllegalArgumentException if {@code n} or {@code k} is negative
     */
    public static float conductor(float cosThetaI, Complexf eta) {
        return conductor(cosThetaI, eta.real, eta.imag);
    }

    /**
     * Returns the exact unpolarized Fresnel reflectance of a conductor with complex relative IOR {@code n + i k},
     * the mean of the s and p reflectances. The incident medium is assumed to have IOR 1. For {@code k = 0} this
     * equals {@link #dielectric} with {@code etaI = 1} and {@code etaT = n}.
     *
     * @param cosThetaI cosine of the incident angle, clamped to {@code [0, 1]}
     * @param n the real part of the IOR, at least 0
     * @param k the extinction coefficient, at least 0
     * @return the reflectance, in {@code [0, 1]}
     * @throws IllegalArgumentException if {@code n} or {@code k} is negative
     */
    public static float conductor(float cosThetaI, float n, float k) {
        if (n < 0.0f || k < 0.0f) {
            throw new IllegalArgumentException("n/k");
        }
        // Exact unpolarized conductor Fresnel (pbrt-v3 FrConductor, incident IOR 1). The previous pbrt-v2
        // approximation was off by up to 0.099 (n = 1.5, k = 0, 79 degrees); for k = 0 this equals dielectric().
        float c = clamp01(cosThetaI);
        float c2 = c * c;
        float s2 = 1.0f - c2;
        float t0 = n * n - k * k - s2;
        float a2b2 = (float) java.lang.Math.sqrt(t0 * t0 + 4.0f * n * n * k * k);
        float a = (float) java.lang.Math.sqrt(java.lang.Math.max(0.0f, 0.5f * (a2b2 + t0)));
        float t1 = a2b2 + c2;
        float t2 = 2.0f * a * c;
        float rs = (t1 - t2) / (t1 + t2);
        float t3 = c2 * a2b2 + s2 * s2;
        float t4 = t2 * s2;
        float rp = rs * (t3 - t4) / (t3 + t4);
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

    /**
     * Returns the conductor reflectance for an IOR given as an {@code fft.Complexf}. Allocates a copy of the IOR.
     *
     * @param cosThetaI cosine of the incident angle, clamped to {@code [0, 1]}
     * @param eta the complex IOR: {@code n} in the real part and {@code k} in the imaginary part
     * @return the reflectance, in {@code [0, 1]}
     * @throws IllegalArgumentException if {@code n} or {@code k} is negative
     * @deprecated use the overload taking {@link Complexf} from {@code core}
     */
    @Deprecated(since = "2.6.0", forRemoval = true)
    public static float conductor(float cosThetaI, org.dynamisengine.vectrix.fft.Complexf eta) {
        return conductor(cosThetaI, new Complexf(eta.real, eta.imag));
    }
}
