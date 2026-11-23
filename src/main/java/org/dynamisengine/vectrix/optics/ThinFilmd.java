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

import org.dynamisengine.vectrix.core.Vector3d;

/**
 * Thin-film interference helpers (double-precision) using tri-band RGB wavelengths.
  * @since 1.0.0
  */
public final class ThinFilmd {
    private ThinFilmd() {
    }

    /**
     * Computes the reflectance of a single thin film on a substrate at the three wavelengths of
     * {@link SpectralRGBd} (red, green, blue), each by {@link #reflectance}. Under total internal reflection the
     * result is 1 in every channel (up to rounding when it happens at the substrate).
     *
     * @param etaI incident medium IOR, greater than 0
     * @param etaFilm film IOR, greater than 0
     * @param etaSub substrate IOR (real), greater than 0
     * @param thicknessNm film thickness in nanometers, at least 0
     * @param cosThetaI cosine of incident angle, clamped to {@code [0, 1]}
     * @param dest receives the reflectance per channel
     * @return {@code dest}
     * @throws IllegalArgumentException if an IOR is not greater than 0 or the thickness is negative
     */
    public static Vector3d reflectanceRgb(double etaI, double etaFilm, double etaSub, double thicknessNm, double cosThetaI, Vector3d dest) {
        if (etaI <= 0.0 || etaFilm <= 0.0 || etaSub <= 0.0 || thicknessNm < 0.0) {
            throw new IllegalArgumentException("params");
        }
        double c0 = Fresneld.clamp01(cosThetaI);

        double etaIOverFilm = etaI / etaFilm;
        double sin2Theta1 = etaIOverFilm * etaIOverFilm * (1.0 - c0 * c0);
        if (sin2Theta1 >= 1.0) {
            return dest.set(1.0, 1.0, 1.0);
        }
        double c1 = java.lang.Math.sqrt(1.0 - sin2Theta1);

        double etaFilmOverSub = etaFilm / etaSub;
        double sin2Theta2 = etaFilmOverSub * etaFilmOverSub * (1.0 - c1 * c1);
        double c2 = sin2Theta2 >= 1.0 ? 0.0 : java.lang.Math.sqrt(1.0 - sin2Theta2);

        double r01s = Fresneld.amplitudeS(c0, etaI, etaFilm, c1);
        double r12s = Fresneld.amplitudeS(c1, etaFilm, etaSub, c2);
        double r01p = Fresneld.amplitudeP(c0, etaI, etaFilm, c1);
        double r12p = Fresneld.amplitudeP(c1, etaFilm, etaSub, c2);

        double phaseScale = 4.0 * java.lang.Math.PI * etaFilm * thicknessNm * c1;
        double r = channelReflectance(phaseScale, SpectralRGBd.LAMBDA_R_NM, r01s, r12s, r01p, r12p);
        double g = channelReflectance(phaseScale, SpectralRGBd.LAMBDA_G_NM, r01s, r12s, r01p, r12p);
        double b = channelReflectance(phaseScale, SpectralRGBd.LAMBDA_B_NM, r01s, r12s, r01p, r12p);
        return dest.set(r, g, b);
    }

    /**
     * Returns the unpolarized reflectance of a single thin film on a substrate at one wavelength, by the Airy
     * summation of the multiple reflections inside the film (the mean of the s and p reflectances). Returns 1 under
     * total internal reflection at the top of the film, and 1 up to rounding under total internal reflection at the
     * substrate. With zero thickness the result is the reflectance of the bare substrate.
     *
     * @param etaI incident medium IOR, greater than 0
     * @param etaFilm film IOR, greater than 0
     * @param etaSub substrate IOR (real), greater than 0
     * @param thicknessNm film thickness in nanometers, at least 0
     * @param cosThetaI cosine of incident angle, clamped to {@code [0, 1]}
     * @param wavelengthNm the vacuum wavelength in nanometers, greater than 0
     * @return the reflectance, in {@code [0, 1]}
     * @throws IllegalArgumentException if an IOR or the wavelength is not greater than 0, or the thickness is
     *         negative
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
        double cd = org.dynamisengine.vectrix.core.AccurateMath.cos(delta);

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

    private static double channelReflectance(double phaseScale, double wavelengthNm, double r01s, double r12s, double r01p, double r12p) {
        double cd = org.dynamisengine.vectrix.core.AccurateMath.cos(phaseScale / wavelengthNm);
        double rs = combineAmplitude(r01s, r12s, cd);
        double rp = combineAmplitude(r01p, r12p, cd);
        return 0.5 * (rs + rp);
    }
}
