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
 * Transfer functions (double precision) between linear light and encoded signal values: SMPTE ST 2084 (PQ) and
 * ARIB STD-B67 (HLG) for HDR, the Rec.709 camera OETF, and pure gamma 2.2. The sRGB curve is
 * {@link ColorMathd#linearToSrgb} / {@link ColorMathd#srgbToLinear}.
 *
 * @since 2.1.0
 */
public final class TransferFunctionsd {
    private static final double PQ_M1 = 2610.0 / 16384.0;
    private static final double PQ_M2 = 2523.0 / 4096.0 * 128.0;
    private static final double PQ_C1 = 3424.0 / 4096.0;
    private static final double PQ_C2 = 2413.0 / 4096.0 * 32.0;
    private static final double PQ_C3 = 2392.0 / 4096.0 * 32.0;
    private static final double HLG_A = 0.17883277;
    private static final double HLG_B = 1.0 - 4.0 * HLG_A;
    private static final double HLG_C = 0.5 - HLG_A * AccurateMath.log(4.0 * HLG_A);

    private TransferFunctionsd() {
    }

    /**
     * Applies the SMPTE ST 2084 (PQ) inverse EOTF: normalized luminance to signal. Negative inputs count as 0.
     *
     * @param y the luminance divided by 10000 cd/m², in [0, 1]
     * @return the PQ signal, in [0, 1] for inputs in range
     */
    public static double pqEncode(double y) {
        double yp = AccurateMath.pow(java.lang.Math.max(y, 0.0), PQ_M1);
        return AccurateMath.pow((PQ_C1 + PQ_C2 * yp) / (1.0 + PQ_C3 * yp), PQ_M2);
    }

    /**
     * Applies the SMPTE ST 2084 (PQ) EOTF: signal to normalized luminance; the inverse of {@link #pqEncode}. Negative
     * inputs count as 0.
     *
     * @param signal the PQ signal, in [0, 1]
     * @return the luminance divided by 10000 cd/m²
     */
    public static double pqDecode(double signal) {
        double ep = AccurateMath.pow(java.lang.Math.max(signal, 0.0), 1.0 / PQ_M2);
        return AccurateMath.pow(java.lang.Math.max(ep - PQ_C1, 0.0) / (PQ_C2 - PQ_C3 * ep), 1.0 / PQ_M1);
    }

    /**
     * Applies {@link #pqEncode} to an absolute luminance.
     *
     * @param nits the luminance in cd/m², in [0, 10000]
     * @return the PQ signal
     */
    public static double pqEncodeNits(double nits) {
        return pqEncode(nits / 10000.0);
    }

    /**
     * Applies the ARIB STD-B67 (HLG) OETF: {@code √(3E)} up to 1/12, then {@code a ln(12E − b) + c}. Negative inputs
     * count as 0.
     *
     * @param e the normalized scene light, in [0, 1]
     * @return the HLG signal, in [0, 1] for inputs in range
     */
    public static double hlgOetf(double e) {
        e = java.lang.Math.max(e, 0.0);
        return e <= 1.0 / 12.0 ? java.lang.Math.sqrt(3.0 * e)
                : HLG_A * AccurateMath.log(12.0 * e - HLG_B) + HLG_C;
    }

    /**
     * Applies the inverse of {@link #hlgOetf}: HLG signal to normalized scene light. Negative inputs count as 0.
     *
     * @param signal the HLG signal, in [0, 1]
     * @return the normalized scene light
     */
    public static double hlgInverseOetf(double signal) {
        signal = java.lang.Math.max(signal, 0.0);
        return signal <= 0.5 ? signal * signal / 3.0
                : (AccurateMath.exp((signal - HLG_C) / HLG_A) + HLG_B) / 12.0;
    }

    /**
     * Applies the ITU-R BT.709 camera OETF: {@code 4.5 L} below 0.018, else {@code 1.099 L^0.45 - 0.099}. Negative
     * inputs follow the linear segment.
     *
     * @param linear the linear scene value L, usually in [0, 1]
     * @return the encoded signal
     */
    public static double rec709Oetf(double linear) {
        return linear < 0.018 ? 4.5 * linear : 1.099 * AccurateMath.pow(linear, 0.45) - 0.099;
    }

    /**
     * Applies the inverse of {@link #rec709Oetf}: {@code V / 4.5} below 0.081, else
     * {@code ((V + 0.099) / 1.099)^(1/0.45)}.
     *
     * @param signal the encoded signal V, usually in [0, 1]
     * @return the linear scene value
     */
    public static double rec709InverseOetf(double signal) {
        return signal < 0.081 ? signal / 4.5 : AccurateMath.pow((signal + 0.099) / 1.099, 1.0 / 0.45);
    }

    /**
     * Encodes with a pure power law, {@code linear^(1/2.2)}. Negative inputs count as 0.
     *
     * @param linear the linear value
     * @return the encoded value
     */
    public static double gamma22Encode(double linear) {
        return AccurateMath.pow(java.lang.Math.max(linear, 0.0), 1.0 / 2.2);
    }

    /**
     * Decodes with a pure power law, {@code signal^2.2}. Negative inputs count as 0.
     *
     * @param signal the encoded value
     * @return the linear value
     */
    public static double gamma22Decode(double signal) {
        return AccurateMath.pow(java.lang.Math.max(signal, 0.0), 2.2);
    }
}
