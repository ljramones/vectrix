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

import org.vectrix.core.Vector3d;

/**
 * Thin-film interference helpers (double-precision) using tri-band RGB wavelengths.
 */
public final class ThinFilmd {
    private ThinFilmd() {
    }

    /**
     * Evaluate thin-film reflectance for RGB tri-band wavelengths.
     *
     * @param etaI incident medium IOR
     * @param etaFilm film IOR
     * @param etaSub substrate IOR
     * @param thicknessNm film thickness in nanometers
     * @param cosThetaI cosine of incident angle in [0,1]
     */
    public static Vector3d reflectanceRgb(double etaI, double etaFilm, double etaSub, double thicknessNm, double cosThetaI, Vector3d dest) {
        return dest.set(
            reflectance(etaI, etaFilm, etaSub, thicknessNm, cosThetaI, SpectralRGBd.LAMBDA_R_NM),
            reflectance(etaI, etaFilm, etaSub, thicknessNm, cosThetaI, SpectralRGBd.LAMBDA_G_NM),
            reflectance(etaI, etaFilm, etaSub, thicknessNm, cosThetaI, SpectralRGBd.LAMBDA_B_NM));
    }

    /**
     * Evaluate thin-film reflectance at a single wavelength in nanometers.
     */
    public static double reflectance(double etaI, double etaFilm, double etaSub, double thicknessNm, double cosThetaI, double wavelengthNm) {
        if (etaI <= 0.0 || etaFilm <= 0.0 || etaSub <= 0.0 || thicknessNm < 0.0 || wavelengthNm <= 0.0) {
            throw new IllegalArgumentException("params");
        }
        double c0 = Fresneld.clamp01(cosThetaI);

        double sin2Theta1 = (etaI / etaFilm) * (etaI / etaFilm) * (1.0 - c0 * c0);
        if (sin2Theta1 >= 1.0) {
            return 1.0;
        }
        double c1 = java.lang.Math.sqrt(1.0 - sin2Theta1);

        double sin2Theta2 = (etaFilm / etaSub) * (etaFilm / etaSub) * (1.0 - c1 * c1);
        double c2 = sin2Theta2 >= 1.0 ? 0.0 : java.lang.Math.sqrt(1.0 - sin2Theta2);

        double r01s = Fresneld.amplitudeS(c0, etaI, etaFilm, c1);
        double r12s = Fresneld.amplitudeS(c1, etaFilm, etaSub, c2);
        double r01p = Fresneld.amplitudeP(c0, etaI, etaFilm, c1);
        double r12p = Fresneld.amplitudeP(c1, etaFilm, etaSub, c2);

        double delta = 4.0 * java.lang.Math.PI * etaFilm * thicknessNm * c1 / wavelengthNm;
        double cd = java.lang.Math.cos(delta);

        double rs = combineAmplitude(r01s, r12s, cd);
        double rp = combineAmplitude(r01p, r12p, cd);
        return 0.5 * (rs + rp);
    }

    private static double combineAmplitude(double r01, double r12, double cosDelta) {
        double r01r12 = r01 * r12;
        double num = r01 * r01 + r12 * r12 + 2.0 * r01r12 * cosDelta;
        double den = 1.0 + r01r12 * r01r12 + 2.0 * r01r12 * cosDelta;
        return num / den;
    }
}
