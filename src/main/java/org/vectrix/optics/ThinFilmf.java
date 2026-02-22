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

import org.vectrix.core.Vector3f;

/**
 * Thin-film interference helpers (single-precision) using tri-band RGB wavelengths.
 */
public final class ThinFilmf {
    private ThinFilmf() {
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
    public static Vector3f reflectanceRgb(float etaI, float etaFilm, float etaSub, float thicknessNm, float cosThetaI, Vector3f dest) {
        return dest.set(
            reflectance(etaI, etaFilm, etaSub, thicknessNm, cosThetaI, SpectralRGBf.LAMBDA_R_NM),
            reflectance(etaI, etaFilm, etaSub, thicknessNm, cosThetaI, SpectralRGBf.LAMBDA_G_NM),
            reflectance(etaI, etaFilm, etaSub, thicknessNm, cosThetaI, SpectralRGBf.LAMBDA_B_NM));
    }

    /**
     * Evaluate thin-film reflectance at a single wavelength in nanometers.
     */
    public static float reflectance(float etaI, float etaFilm, float etaSub, float thicknessNm, float cosThetaI, float wavelengthNm) {
        if (etaI <= 0.0f || etaFilm <= 0.0f || etaSub <= 0.0f || thicknessNm < 0.0f || wavelengthNm <= 0.0f) {
            throw new IllegalArgumentException("params");
        }
        float c0 = Fresnelf.clamp01(cosThetaI);

        float sin2Theta1 = (etaI / etaFilm) * (etaI / etaFilm) * (1.0f - c0 * c0);
        if (sin2Theta1 >= 1.0f) {
            return 1.0f;
        }
        float c1 = (float) java.lang.Math.sqrt(1.0f - sin2Theta1);

        float sin2Theta2 = (etaFilm / etaSub) * (etaFilm / etaSub) * (1.0f - c1 * c1);
        float c2 = sin2Theta2 >= 1.0f ? 0.0f : (float) java.lang.Math.sqrt(1.0f - sin2Theta2);

        float r01s = Fresnelf.amplitudeS(c0, etaI, etaFilm, c1);
        float r12s = Fresnelf.amplitudeS(c1, etaFilm, etaSub, c2);
        float r01p = Fresnelf.amplitudeP(c0, etaI, etaFilm, c1);
        float r12p = Fresnelf.amplitudeP(c1, etaFilm, etaSub, c2);

        float delta = (float) (4.0 * java.lang.Math.PI * etaFilm * thicknessNm * c1 / wavelengthNm);
        float cd = (float) java.lang.Math.cos(delta);

        float rs = combineAmplitude(r01s, r12s, cd);
        float rp = combineAmplitude(r01p, r12p, cd);
        return 0.5f * (rs + rp);
    }

    private static float combineAmplitude(float r01, float r12, float cosDelta) {
        float r01r12 = r01 * r12;
        float num = r01 * r01 + r12 * r12 + 2.0f * r01r12 * cosDelta;
        float den = 1.0f + r01r12 * r01r12 + 2.0f * r01r12 * cosDelta;
        return num / den;
    }
}
