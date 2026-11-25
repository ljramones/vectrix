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

import org.dynamisengine.vectrix.core.AccurateMath;

/**
 * Physical camera exposure (single precision), in the conventions of Lagarde and de Rousiers (2014) and Filament:
 * exposure value at ISO 100 (EV100), the saturation-based scale that turns scene luminance into a normalized value,
 * and photometric/radiometric conversion.
 *
 * @since 2.1.0
 */
public final class Exposuref {
    /** Maximum luminous efficacy, lm/W at 555 nm. */
    public static final float LUMINOUS_EFFICACY = 683.0f;
    private static final float INV_LN2 = (float) 1.4426950408889634; // 1 / ln 2; a literal for reproducibility

    private Exposuref() {
    }

    /**
     * Returns the exposure value at ISO 100 for camera settings: {@code log2(N² / t · 100 / S)}.
     *
     * @param aperture the f-number N, greater than 0
     * @param shutterSeconds the shutter time t in seconds, greater than 0
     * @param iso the sensitivity S, greater than 0
     * @return the EV100
     */
    public static float ev100(float aperture, float shutterSeconds, float iso) {
        return (float) AccurateMath.log(aperture * aperture / shutterSeconds * 100.0f / iso) * INV_LN2;
    }

    /**
     * Returns the EV100 that exposes an average scene luminance to middle grey: {@code log2(L · 100 / 12.5)}, with
     * the reflected-light meter constant K = 12.5.
     *
     * @param nits the average scene luminance L in cd/m², greater than 0
     * @return the EV100
     */
    public static float ev100FromAverageLuminance(float nits) {
        return (float) AccurateMath.log(nits * 100.0f / 12.5f) * INV_LN2;
    }

    /**
     * Returns the multiplier that maps luminance (cd/m²) to a normalized value for tonemapping, from the
     * saturation-based sensitivity: {@code 1 / (1.2 · 2^EV100)}.
     *
     * @param ev100 the exposure value at ISO 100
     * @return the exposure multiplier
     */
    public static float exposureFromEv100(float ev100) {
        return 1.0f / (1.2f * (float) AccurateMath.pow(2.0f, ev100));
    }

    /**
     * Converts a radiometric quantity to the photometric one at 555 nm: multiplies by {@link #LUMINOUS_EFFICACY}
     * (watts to lumens, W/sr to candela, W/m² to lux, W/(sr·m²) to nits).
     *
     * @param watts the radiometric quantity
     * @return the photometric quantity
     */
    public static float photometricFromRadiometric(float watts) {
        return watts * LUMINOUS_EFFICACY;
    }

    /**
     * Converts a photometric quantity to the radiometric one at 555 nm: divides by {@link #LUMINOUS_EFFICACY}.
     *
     * @param lumens the photometric quantity (lumens, candela, lux or nits)
     * @return the radiometric quantity
     */
    public static float radiometricFromPhotometric(float lumens) {
        return lumens / LUMINOUS_EFFICACY;
    }
}
