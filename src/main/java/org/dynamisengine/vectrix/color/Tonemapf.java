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
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Tonemapping operators (single precision): scene-referred linear sRGB (after exposure) to display-referred linear
 * values in {@code [0, 1]}, ready for {@link ColorMathf#linearToSrgb}. Every {@code dest} may alias the input.
 * Basic Reinhard is {@link ColorMathf#reinhardToneMap}.
 *
 * @since 2.1.0
 */
public final class Tonemapf {
    private Tonemapf() {
    }

    private static float clamp01(float x) {
        return java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, x));
    }

    /** Narkowicz's fit of the ACES RRT+ODT curve (2015), per channel: {@code x (2.51 x + 0.03) / (x (2.43 x + 0.59) + 0.14)}. */
    public static float acesNarkowicz(float x) {
        return clamp01(x * (2.51f * x + 0.03f) / (x * (2.43f * x + 0.59f) + 0.14f));
    }

    /** {@link #acesNarkowicz} per channel. */
    public static Vector3f acesNarkowicz(Vector3fc rgb, Vector3f dest) {
        return dest.set(acesNarkowicz(rgb.x()), acesNarkowicz(rgb.y()), acesNarkowicz(rgb.z()));
    }

    /**
     * Stephen Hill's fit of the ACES RRT+ODT (sRGB output): the ACES input matrix, a rational fit of the tone curve per
     * channel, and the output matrix. More faithful to ACES than {@link #acesNarkowicz}, including its desaturation of
     * bright colours.
     */
    public static Vector3f acesHill(Vector3fc rgb, Vector3f dest) {
        float r = rgb.x(), g = rgb.y(), b = rgb.z();
        float ar = 0.59719f * r + 0.35458f * g + 0.04823f * b;
        float ag = 0.07600f * r + 0.90834f * g + 0.01566f * b;
        float ab = 0.02840f * r + 0.13383f * g + 0.83777f * b;
        ar = rrtOdtFit(ar);
        ag = rrtOdtFit(ag);
        ab = rrtOdtFit(ab);
        return dest.set(clamp01(1.60475f * ar - 0.53108f * ag - 0.07367f * ab),
                clamp01(-0.10208f * ar + 1.10813f * ag - 0.00605f * ab),
                clamp01(-0.00327f * ar - 0.07276f * ag + 1.07602f * ab));
    }

    private static float rrtOdtFit(float v) {
        float a = v * (v + 0.0245786f) - 0.000090537f;
        float b = v * (0.983729f * v + 0.4329510f) + 0.238081f;
        return a / b;
    }

    /**
     * AgX (Sobotka), in the widely used approximation of Wrensch: the AgX inset matrix, a log2 encoding over
     * {@code [-12.47, 4.03]} EV, a sixth-order fit of the default contrast curve, the outset matrix, and the 2.2 power
     * back to linear. Hue-stable at high intensities, where per-channel curves skew towards the primaries. The published
     * inset matrix's rows sum to 1 only within 1.4e-4, so grey stays neutral to about 2e-4 and, where the log clamp
     * engages near 16, the output dips by about 3e-6.
     */
    public static Vector3f agx(Vector3fc rgb, Vector3f dest) {
        float r = rgb.x(), g = rgb.y(), b = rgb.z();
        float ir = 0.842479062253094f * r + 0.0784335999999992f * g + 0.0792237451477643f * b;
        float ig = 0.0423282422610123f * r + 0.878468636469772f * g + 0.0791661274605434f * b;
        float ib = 0.0423756549057051f * r + 0.0784336f * g + 0.879142973793104f * b;
        ir = agxCurve(agxLog(ir));
        ig = agxCurve(agxLog(ig));
        ib = agxCurve(agxLog(ib));
        float or = 1.19687900512017f * ir - 0.0980208811401368f * ig - 0.0990297440797205f * ib;
        float og = -0.0528968517574562f * ir + 1.15190312990417f * ig - 0.0989611768448433f * ib;
        float ob = -0.0529716355144438f * ir - 0.0980434501171241f * ig + 1.15107367264116f * ib;
        return dest.set(agxEotf(or), agxEotf(og), agxEotf(ob));
    }

    private static float agxLog(float x) {
        float minEv = -12.47393f;
        float maxEv = 4.026069f;
        float ev = x > 0.0f ? (float) (AccurateMath.log(x) * 1.4426950408889634) /* 1 / ln 2 */ : minEv;
        ev = java.lang.Math.max(minEv, java.lang.Math.min(maxEv, ev));
        return (ev - minEv) / (maxEv - minEv);
    }

    private static float agxCurve(float x) {
        float x2 = x * x;
        float x4 = x2 * x2;
        return 15.5f * x4 * x2 - 40.14f * x4 * x + 31.96f * x4 - 6.868f * x2 * x + 0.4298f * x2 + 0.1191f * x
                - 0.00232f;
    }

    private static float agxEotf(float x) {
        return (float) AccurateMath.pow(clamp01(x), 2.2f);
    }

    /**
     * Khronos PBR Neutral (2024): leaves colours below about 0.8 almost untouched (so base colours reproduce their
     * sRGB values), compresses highlights, and desaturates towards white as they clip.
     */
    public static Vector3f khronosPbrNeutral(Vector3fc rgb, Vector3f dest) {
        float startCompression = 0.8f - 0.04f;
        float desaturation = 0.15f;
        float r = rgb.x(), g = rgb.y(), b = rgb.z();
        float x = java.lang.Math.min(r, java.lang.Math.min(g, b));
        float offset = x < 0.08f ? x - 6.25f * x * x : 0.04f;
        r -= offset;
        g -= offset;
        b -= offset;
        float peak = java.lang.Math.max(r, java.lang.Math.max(g, b));
        if (peak < startCompression) {
            return dest.set(r, g, b);
        }
        float d = 1.0f - startCompression;
        float newPeak = 1.0f - d * d / (peak + d - startCompression);
        float scale = newPeak / peak;
        r *= scale;
        g *= scale;
        b *= scale;
        float w = 1.0f - 1.0f / (desaturation * (peak - newPeak) + 1.0f);
        return dest.set(r + (newPeak - r) * w, g + (newPeak - g) * w, b + (newPeak - b) * w);
    }

    /**
     * Uchimura's "GT" curve (Gran Turismo Sport, 2017) with its default parameters: a toe, a linear section that keeps
     * mid-tones unchanged (0.22 to 0.62), and a smooth shoulder to 1.
     */
    public static float uchimura(float x) {
        return uchimura(x, 1.0f, 1.0f, 0.22f, 0.4f, 1.33f, 0.0f);
    }

    /**
     * Uchimura's curve with explicit parameters: {@code maxBrightness} P, {@code contrast} a, linear-section start m and
     * length l, black tightness c and pedestal b.
     */
    public static float uchimura(float x, float maxBrightness, float contrast, float linearStart, float linearLength,
            float black, float pedestal) {
        float p = maxBrightness, a = contrast, m = linearStart, l = linearLength, c = black, b = pedestal;
        float l0 = ((p - m) * l) / a;
        float s0 = m + l0;
        float s1 = m + a * l0;
        float c2 = (a * p) / (p - s1);
        float cp = -c2 / p;
        float w0 = 1.0f - smoothstep(0.0f, m, x);
        float w2 = x < m + l0 ? 0.0f : 1.0f;
        float w1 = 1.0f - w0 - w2;
        float t = m * (float) AccurateMath.pow(java.lang.Math.max(x, 0.0f) / m, c) + b;
        float s = p - (p - s1) * (float) AccurateMath.exp(cp * (x - s0));
        float lin = m + a * (x - m);
        return t * w0 + lin * w1 + s * w2;
    }

    private static float smoothstep(float e0, float e1, float x) {
        float t = clamp01((x - e0) / (e1 - e0));
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * Lottes' curve (2016) with its published defaults: contrast 1.6, shoulder 0.977, white at 8, and middle grey 0.18
     * mapped to 0.267. The curve keeps rising past the white point, so the result is clamped to {@code [0, 1]}, as in
     * Lottes' presentation.
     */
    public static float lottes(float x) {
        float a = 1.6f, d = 0.977f, hdrMax = 8.0f, midIn = 0.18f, midOut = 0.267f;
        float pa = (float) AccurateMath.pow(hdrMax, a), pad = (float) AccurateMath.pow(hdrMax, a * d);
        float ma = (float) AccurateMath.pow(midIn, a), mad = (float) AccurateMath.pow(midIn, a * d);
        float b = (-ma + pa * midOut) / ((pad - mad) * midOut);
        float c = (pad * ma - pa * mad * midOut) / ((pad - mad) * midOut);
        float xa = (float) AccurateMath.pow(java.lang.Math.max(x, 0.0f), a);
        float xad = (float) AccurateMath.pow(java.lang.Math.max(x, 0.0f), a * d);
        return clamp01(xa / (xad * b + c));
    }

    /** Extended Reinhard with a white point: {@code x (1 + x / white²) / (1 + x)}, which maps {@code white} to 1. */
    public static float reinhardExtended(float x, float white) {
        return x * (1.0f + x / (white * white)) / (1.0f + x);
    }

    /**
     * DynamisLightEngine's current tonemap, reproduced exactly: {@code 1 - exp(-color * exposure)} per channel, with
     * {@code exposure} clamped to at least 1e-4 (post-processing shaders in {@code VulkanPostShaderSources} and
     * {@code GlShaderSources}). Its exposure is a plain multiplier; see {@link Exposuref} for physical exposure.
     */
    public static float lightEngineExponential(float x, float exposure) {
        return 1.0f - (float) AccurateMath.exp(-x * java.lang.Math.max(exposure, 0.0001f));
    }
}
