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

import org.vectrix.fft.Complexd;

/**
 * Fresnel reflectance helpers (double-precision).
  * @since 1.0.0
  */
public final class Fresneld {
    private Fresneld() {
    }

    /**
     * Unpolarized dielectric Fresnel reflectance.
     *
     * @param cosThetaI cosine of incident angle in [0,1]
     */
    public static double dielectric(double cosThetaI, double etaI, double etaT) {
        if (etaI <= 0.0 || etaT <= 0.0) {
            throw new IllegalArgumentException("eta");
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
     * Unpolarized conductor Fresnel reflectance with complex relative IOR {@code n + i k}.
     * The incident medium is assumed to have IOR 1.
     */
    public static double conductor(double cosThetaI, Complexd eta) {
        return conductor(cosThetaI, eta.real, eta.imag);
    }

    /**
     * Unpolarized conductor Fresnel reflectance with complex relative IOR {@code n + i k}.
     * The incident medium is assumed to have IOR 1.
     */
    public static double conductor(double cosThetaI, double n, double k) {
        if (n < 0.0 || k < 0.0) {
            throw new IllegalArgumentException("n/k");
        }
        double c = clamp01(cosThetaI);
        double c2 = c * c;
        double n2k2 = n * n + k * k;

        double rsNum = n2k2 - 2.0 * n * c + c2;
        double rsDen = n2k2 + 2.0 * n * c + c2;
        double rs = rsNum / rsDen;

        double rpNum = n2k2 * c2 - 2.0 * n * c + 1.0;
        double rpDen = n2k2 * c2 + 2.0 * n * c + 1.0;
        double rp = rpNum / rpDen;
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
