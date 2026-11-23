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
 * Transfer functions (single precision) between linear light and encoded signal values: SMPTE ST 2084 (PQ) and
 * ARIB STD-B67 (HLG) for HDR, the Rec.709 camera OETF, and pure gamma 2.2. The sRGB curve is
 * {@link ColorMathf#linearToSrgb} / {@link ColorMathf#srgbToLinear}.
 *
 * @since 2.1.0
 */
public final class TransferFunctionsf {
    private static final float PQ_M1 = 2610.0f / 16384.0f;
    private static final float PQ_M2 = 2523.0f / 4096.0f * 128.0f;
    private static final float PQ_C1 = 3424.0f / 4096.0f;
    private static final float PQ_C2 = 2413.0f / 4096.0f * 32.0f;
    private static final float PQ_C3 = 2392.0f / 4096.0f * 32.0f;
    private static final float HLG_A = 0.17883277f;
    private static final float HLG_B = 1.0f - 4.0f * HLG_A;
    private static final float HLG_C = 0.5f - HLG_A * (float) AccurateMath.log(4.0f * HLG_A);

    private TransferFunctionsf() {
    }

    /**
     * Applies the SMPTE ST 2084 (PQ) inverse EOTF: normalized luminance to signal. Negative inputs count as 0.
     *
     * @param y the luminance divided by 10000 cd/m², in [0, 1]
     * @return the PQ signal, in [0, 1] for inputs in range
     */
    public static float pqEncode(float y) {
        float yp = (float) AccurateMath.pow(java.lang.Math.max(y, 0.0f), PQ_M1);
        return (float) AccurateMath.pow((PQ_C1 + PQ_C2 * yp) / (1.0f + PQ_C3 * yp), PQ_M2);
    }

    /**
     * Applies the SMPTE ST 2084 (PQ) EOTF: signal to normalized luminance; the inverse of {@link #pqEncode}. Negative
     * inputs count as 0.
     *
     * @param signal the PQ signal, in [0, 1]
     * @return the luminance divided by 10000 cd/m²
     */
    public static float pqDecode(float signal) {
        float ep = (float) AccurateMath.pow(java.lang.Math.max(signal, 0.0f), 1.0f / PQ_M2);
        return (float) AccurateMath.pow(java.lang.Math.max(ep - PQ_C1, 0.0f) / (PQ_C2 - PQ_C3 * ep), 1.0f / PQ_M1);
    }

    /**
     * Applies {@link #pqEncode} to an absolute luminance.
     *
     * @param nits the luminance in cd/m², in [0, 10000]
     * @return the PQ signal
     */
    public static float pqEncodeNits(float nits) {
        return pqEncode(nits / 10000.0f);
    }

    /**
     * Applies the ARIB STD-B67 (HLG) OETF: {@code √(3E)} up to 1/12, then {@code a ln(12E − b) + c}. Negative inputs
     * count as 0.
     *
     * @param e the normalized scene light, in [0, 1]
     * @return the HLG signal, in [0, 1] for inputs in range
     */
    public static float hlgOetf(float e) {
        e = java.lang.Math.max(e, 0.0f);
        return e <= 1.0f / 12.0f ? (float) java.lang.Math.sqrt(3.0f * e)
                : HLG_A * (float) AccurateMath.log(12.0f * e - HLG_B) + HLG_C;
    }

    /**
     * Applies the inverse of {@link #hlgOetf}: HLG signal to normalized scene light. Negative inputs count as 0.
     *
     * @param signal the HLG signal, in [0, 1]
     * @return the normalized scene light
     */
    public static float hlgInverseOetf(float signal) {
        signal = java.lang.Math.max(signal, 0.0f);
        return signal <= 0.5f ? signal * signal / 3.0f
                : ((float) AccurateMath.exp((signal - HLG_C) / HLG_A) + HLG_B) / 12.0f;
    }

    /**
     * Applies the ITU-R BT.709 camera OETF: {@code 4.5 L} below 0.018, else {@code 1.099 L^0.45 - 0.099}. Negative
     * inputs follow the linear segment.
     *
     * @param linear the linear scene value L, usually in [0, 1]
     * @return the encoded signal
     */
    public static float rec709Oetf(float linear) {
        return linear < 0.018f ? 4.5f * linear : 1.099f * (float) AccurateMath.pow(linear, 0.45f) - 0.099f;
    }

    /**
     * Applies the inverse of {@link #rec709Oetf}: {@code V / 4.5} below 0.081, else
     * {@code ((V + 0.099) / 1.099)^(1/0.45)}.
     *
     * @param signal the encoded signal V, usually in [0, 1]
     * @return the linear scene value
     */
    public static float rec709InverseOetf(float signal) {
        return signal < 0.081f ? signal / 4.5f : (float) AccurateMath.pow((signal + 0.099f) / 1.099f, 1.0f / 0.45f);
    }

    /**
     * Encodes with a pure power law, {@code linear^(1/2.2)}. Negative inputs count as 0.
     *
     * @param linear the linear value
     * @return the encoded value
     */
    public static float gamma22Encode(float linear) {
        return (float) AccurateMath.pow(java.lang.Math.max(linear, 0.0f), 1.0f / 2.2f);
    }

    /**
     * Decodes with a pure power law, {@code signal^2.2}. Negative inputs count as 0.
     *
     * @param signal the encoded value
     * @return the linear value
     */
    public static float gamma22Decode(float signal) {
        return (float) AccurateMath.pow(java.lang.Math.max(signal, 0.0f), 2.2f);
    }
}
