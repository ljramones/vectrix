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
 * Physical camera exposure (double precision), in the conventions of Lagarde and de Rousiers (2014) and Filament:
 * exposure value at ISO 100 (EV100), the saturation-based scale that turns scene luminance into a normalised value,
 * and photometric/radiometric conversion.
 *
 * @since 2.1.0
 */
public final class Exposured {
    /** Maximum luminous efficacy, lm/W at 555 nm. */
    public static final double LUMINOUS_EFFICACY = 683.0;
    private static final double INV_LN2 = 1.4426950408889634; // 1 / ln 2, a literal: Math.log is not bit-reproducible

    private Exposured() {
    }

    /** EV100 from camera settings: {@code log2(N² / t · 100 / S)} for aperture N, shutter time t (s) and ISO S. */
    public static double ev100(double aperture, double shutterSeconds, double iso) {
        return AccurateMath.log(aperture * aperture / shutterSeconds * 100.0 / iso) * INV_LN2;
    }

    /** EV100 that exposes average scene luminance {@code nits} (cd/m²) to middle grey: {@code log2(L · 100 / 12.5)}. */
    public static double ev100FromAverageLuminance(double nits) {
        return AccurateMath.log(nits * 100.0 / 12.5) * INV_LN2;
    }

    /**
     * The multiplier that maps luminance (cd/m²) to a normalised value for tonemapping, saturation-based:
     * {@code 1 / (1.2 · 2^EV100)}.
     */
    public static double exposureFromEv100(double ev100) {
        return 1.0 / (1.2 * AccurateMath.pow(2.0, ev100));
    }

    /** Luminous quantity (lm, cd, lux, nits) from the radiometric one at 555 nm: {@code 683 ×}. */
    public static double photometricFromRadiometric(double watts) {
        return watts * LUMINOUS_EFFICACY;
    }

    /** Radiometric quantity from the photometric one at 555 nm: {@code ÷ 683}. */
    public static double radiometricFromPhotometric(double lumens) {
        return lumens / LUMINOUS_EFFICACY;
    }
}
