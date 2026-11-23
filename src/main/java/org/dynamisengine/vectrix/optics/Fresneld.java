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

import org.dynamisengine.vectrix.core.Complexd;

/**
 * Fresnel reflectance helpers (double-precision).
  * @since 1.0.0
  */
public final class Fresneld {
    private Fresneld() {
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
     * @throws IllegalArgumentException if {@code etaI} or {@code etaT} is not greater than 0 (NaN included)
     */
    public static double dielectric(double cosThetaI, double etaI, double etaT) {
        if (!(etaI > 0.0) || !(etaT > 0.0)) {
            throw new IllegalArgumentException("eta");
        }
        if (cosThetaI < 0.0) {
            // Leaving the medium: swap the indices, as in pbrt (it used to clamp to grazing incidence and return ~1).
            double tmp = etaI;
            etaI = etaT;
            etaT = tmp;
            cosThetaI = -cosThetaI;
        }
        double c = clamp01(cosThetaI);
        double eta = etaI / etaT;
        double sin2T = eta * eta * (1.0 - c * c);
        if (sin2T >= 1.0) {
            return 1.0;
        }
        double cosThetaT = java.lang.Math.sqrt(1.0 - sin2T);
        double rs = (etaI * c - etaT * cosThetaT) / (etaI * c + etaT * cosThetaT);
        double rp = (etaT * c - etaI * cosThetaT) / (etaT * c + etaI * cosThetaT);
        return 0.5 * (rs * rs + rp * rp);
    }

    /**
     * Returns the exact unpolarized Fresnel reflectance of a conductor with complex relative IOR {@code n + i k}.
     * The incident medium is assumed to have IOR 1. Same as {@link #conductor(double, double, double)}.
     *
     * @param cosThetaI cosine of the incident angle, clamped to {@code [0, 1]}
     * @param eta the complex IOR: {@code n} in the real part and the extinction {@code k} in the imaginary part
     * @return the reflectance, in {@code [0, 1]}
     * @throws IllegalArgumentException if {@code n} or {@code k} is negative or NaN
     */
    public static double conductor(double cosThetaI, Complexd eta) {
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
     * @throws IllegalArgumentException if {@code n} or {@code k} is negative or NaN
     */
    public static double conductor(double cosThetaI, double n, double k) {
        if (!(n >= 0.0) || !(k >= 0.0)) {
            throw new IllegalArgumentException("n/k");
        }
        // Exact unpolarized conductor Fresnel (pbrt-v3 FrConductor, incident IOR 1). The previous pbrt-v2
        // approximation was off by up to 0.099 (n = 1.5, k = 0, 79 degrees); for k = 0 this equals dielectric().
        double c = clamp01(cosThetaI);
        double c2 = c * c;
        double s2 = 1.0 - c2;
        double t0 = n * n - k * k - s2;
        double a2b2 = java.lang.Math.sqrt(t0 * t0 + 4.0 * n * n * k * k);
        double a = java.lang.Math.sqrt(java.lang.Math.max(0.0, 0.5 * (a2b2 + t0)));
        double t1 = a2b2 + c2;
        double t2 = 2.0 * a * c;
        double rs = (t1 - t2) / (t1 + t2);
        double t3 = c2 * a2b2 + s2 * s2;
        double t4 = t2 * s2;
        double rp = rs * (t3 - t4) / (t3 + t4);
        return 0.5 * (rs + rp);
    }

    static double amplitudeS(double cosThetaI, double etaI, double etaT, double cosThetaT) {
        return (etaI * cosThetaI - etaT * cosThetaT) / (etaI * cosThetaI + etaT * cosThetaT);
    }

    static double amplitudeP(double cosThetaI, double etaI, double etaT, double cosThetaT) {
        return (etaT * cosThetaI - etaI * cosThetaT) / (etaT * cosThetaI + etaI * cosThetaT);
    }

    static double clamp01(double v) {
        return java.lang.Math.max(0.0, java.lang.Math.min(1.0, v));
    }

}
