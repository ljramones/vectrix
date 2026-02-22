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
package org.vectrix.renderingmath;

/**
 * Builds Bruneton-style transmittance LUTs (height x zenith cosine) into flat RGB arrays.
 */
public final class TransmittanceLutBuilder {
    private TransmittanceLutBuilder() {
    }

    /**
     * Build a transmittance LUT with axes:
     * x = mu = cos(zenith) in [-1,1], y = height in [0,1].
     *
     * @param width LUT width
     * @param height LUT height
     * @param params atmosphere parameters
     * @param sampleCount integration samples along ray
     * @param outRgb output array length >= width*height*3, interleaved RGB
     */
    public static void build(int width, int height, AtmosphereParams params, int sampleCount, float[] outRgb) {
        if (width <= 0 || height <= 0 || sampleCount <= 0 || params == null) {
            throw new IllegalArgumentException("params");
        }
        if (outRgb == null || outRgb.length < width * height * 3) {
            throw new IllegalArgumentException("outRgb");
        }
        float[] tmp = new float[3];
        for (int y = 0; y < height; y++) {
            float v = (y + 0.5f) / height;
            float r = params.groundRadius + v * (params.atmosphereRadius - params.groundRadius);
            for (int x = 0; x < width; x++) {
                float u = (x + 0.5f) / width;
                float mu = -1.0f + 2.0f * u;
                float d = distanceToAtmosphereBoundary(r, mu, params.groundRadius, params.atmosphereRadius);
                integrateOpticalDepth(r, mu, d, params, sampleCount, tmp);

                int o = (y * width + x) * 3;
                outRgb[o] = clamp01(tmp[0]);
                outRgb[o + 1] = clamp01(tmp[1]);
                outRgb[o + 2] = clamp01(tmp[2]);
            }
        }
    }

    private static void integrateOpticalDepth(float r0, float mu, float distance, AtmosphereParams p, int samples, float[] outRgb) {
        if (distance <= 0.0f) {
            outRgb[0] = 1.0f;
            outRgb[1] = 1.0f;
            outRgb[2] = 1.0f;
            return;
        }
        float ds = distance / samples;
        float tauR = 0.0f;
        float tauM = 0.0f;
        for (int i = 0; i < samples; i++) {
            float s = (i + 0.5f) * ds;
            float r = (float) java.lang.Math.sqrt(r0 * r0 + s * s + 2.0f * r0 * mu * s);
            float h = java.lang.Math.max(0.0f, r - p.groundRadius);
            tauR += (float) java.lang.Math.exp(-h / p.rayleighScaleHeight) * ds;
            tauM += (float) java.lang.Math.exp(-h / p.mieScaleHeight) * ds;
        }
        outRgb[0] = (float) java.lang.Math.exp(-(p.betaRayleighR * tauR + p.betaMieR * tauM));
        outRgb[1] = (float) java.lang.Math.exp(-(p.betaRayleighG * tauR + p.betaMieG * tauM));
        outRgb[2] = (float) java.lang.Math.exp(-(p.betaRayleighB * tauR + p.betaMieB * tauM));
    }

    private static float distanceToAtmosphereBoundary(float r, float mu, float groundRadius, float atmosphereRadius) {
        float dTop = intersectSphere(r, mu, atmosphereRadius);
        float dGround = intersectSphere(r, mu, groundRadius);
        if (dGround > 0.0f && (dTop <= 0.0f || dGround < dTop)) {
            return dGround;
        }
        return java.lang.Math.max(0.0f, dTop);
    }

    private static float intersectSphere(float r, float mu, float radius) {
        float b = 2.0f * r * mu;
        float c = r * r - radius * radius;
        float disc = b * b - 4.0f * c;
        if (disc < 0.0f) {
            return -1.0f;
        }
        float sqrt = (float) java.lang.Math.sqrt(disc);
        float t0 = (-b - sqrt) * 0.5f;
        float t1 = (-b + sqrt) * 0.5f;
        if (t0 > 0.0f) {
            return t0;
        }
        if (t1 > 0.0f) {
            return t1;
        }
        return -1.0f;
    }

    private static float clamp01(float v) {
        return java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, v));
    }
}
