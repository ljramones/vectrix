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
package org.dynamisengine.vectrix.color;

import org.dynamisengine.vectrix.core.Vector3d;

/**
 * Spectral colorimetry on 1 nm data, by the CIE summation method ({@code X = k Σ φ(λ) x̄(λ) Δλ} with Δλ = 1 nm, CIE
 * 15): the D65 illuminant, tristimulus values of emission spectra, and of reflectance spectra under D65 normalized so
 * the perfect reflecting diffuser has {@code Y = 1}. Spectra are arrays of 1 nm samples starting at {@code firstNm};
 * wavelengths outside the observer's 360-830 nm contribute nothing.
 *
 * @since 2.3.0
 */
public final class Spectra {
    /** First and last D65 wavelengths (nm). */
    public static final int D65_MIN_NM = 300;
    /** Last D65 wavelength (nm). */
    public static final int D65_MAX_NM = 830;

    private Spectra() {
    }

    /** Relative spectral power of CIE illuminant D65 at {@code nm} (official 1 nm table, interpolated). */
    public static double d65(double nm) {
        if (!(nm >= D65_MIN_NM && nm <= D65_MAX_NM)) {
            return 0.0;
        }
        double f = nm - D65_MIN_NM;
        int i = (int) f;
        if (i == D65_MAX_NM - D65_MIN_NM) {
            return CieData.D65[i];
        }
        double t = f - i;
        return CieData.D65[i] + t * (CieData.D65[i + 1] - CieData.D65[i]);
    }

    private static double d65At(int nm) {
        return nm < D65_MIN_NM || nm > D65_MAX_NM ? 0.0 : CieData.D65[nm - D65_MIN_NM];
    }

    /** Raw tristimulus sums {@code Σ φ x̄, Σ φ ȳ, Σ φ z̄} (Δλ = 1 nm, k = 1) of the 1 nm spectrum {@code spd}. */
    public static Vector3d tristimulus(double[] spd, int firstNm, CieObserver observer, Vector3d dest) {
        double x = 0.0, y = 0.0, z = 0.0;
        int lo = java.lang.Math.max(firstNm, CieObserver.MIN_NM);
        int hi = java.lang.Math.min(firstNm + spd.length - 1, CieObserver.MAX_NM);
        for (int nm = lo; nm <= hi; nm++) {
            double v = spd[nm - firstNm];
            x += v * observer.at(nm, 0);
            y += v * observer.at(nm, 1);
            z += v * observer.at(nm, 2);
        }
        return dest.set(x, y, z);
    }

    /**
     * XYZ of a reflectance spectrum (1 nm samples) under D65, with {@code k = 1 / Σ D65 ȳ} so a perfect diffuser has
     * {@code Y = 1}.
     */
    public static Vector3d reflectanceToXyz(double[] reflectance, int firstNm, CieObserver observer, Vector3d dest) {
        double x = 0.0, y = 0.0, z = 0.0, n = 0.0;
        for (int nm = CieObserver.MIN_NM; nm <= CieObserver.MAX_NM; nm++) {
            double e = d65At(nm);
            int i = nm - firstNm;
            double r = i >= 0 && i < reflectance.length ? reflectance[i] : 0.0;
            x += r * e * observer.at(nm, 0);
            y += r * e * observer.at(nm, 1);
            z += r * e * observer.at(nm, 2);
            n += e * observer.at(nm, 1);
        }
        return dest.set(x / n, y / n, z / n);
    }

    /** XYZ white point of D65 for {@code observer}, normalized to {@code Y = 1}. */
    public static Vector3d whitePointD65(CieObserver observer, Vector3d dest) {
        double x = 0.0, y = 0.0, z = 0.0;
        for (int nm = CieObserver.MIN_NM; nm <= CieObserver.MAX_NM; nm++) {
            double e = d65At(nm);
            x += e * observer.at(nm, 0);
            y += e * observer.at(nm, 1);
            z += e * observer.at(nm, 2);
        }
        return dest.set(x / y, 1.0, z / y);
    }
}
