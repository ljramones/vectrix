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
package org.dynamisengine.vectrix.renderingmath;

/**
 * Builds Bruneton-style transmittance LUTs (height x zenith cosine) into flat RGB arrays.
  * @since 1.0.0
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
        float invWidth = 1.0f / width;
        float invHeight = 1.0f / height;
        float groundRadius = params.groundRadius;
        float atmosphereRadius = params.atmosphereRadius;
        float rayleighScaleHeight = params.rayleighScaleHeight;
        float mieScaleHeight = params.mieScaleHeight;
        float betaRayleighR = params.betaRayleighR;
        float betaRayleighG = params.betaRayleighG;
        float betaRayleighB = params.betaRayleighB;
        float betaMieR = params.betaMieR;
        float betaMieG = params.betaMieG;
        float betaMieB = params.betaMieB;
        for (int y = 0; y < height; y++) {
            float v = (y + 0.5f) * invHeight;
            float r = groundRadius + v * (atmosphereRadius - groundRadius);
            float r2 = r * r;
            for (int x = 0; x < width; x++) {
                float u = (x + 0.5f) * invWidth;
                float mu = -1.0f + 2.0f * u;
                float d = distanceToAtmosphereBoundary(r, mu, groundRadius, atmosphereRadius);
                int o = (y * width + x) * 3;
                if (d <= 0.0f) {
                    outRgb[o] = 1.0f;
                    outRgb[o + 1] = 1.0f;
                    outRgb[o + 2] = 1.0f;
                    continue;
                }
                float ds = d / sampleCount;
                float tauR = 0.0f;
                float tauM = 0.0f;
                for (int i = 0; i < sampleCount; i++) {
                    float s = (i + 0.5f) * ds;
                    float rr = (float) java.lang.Math.sqrt(r2 + s * s + 2.0f * r * mu * s);
                    float h = java.lang.Math.max(0.0f, rr - groundRadius);
                    tauR += (float) java.lang.Math.exp(-h / rayleighScaleHeight) * ds;
                    tauM += (float) java.lang.Math.exp(-h / mieScaleHeight) * ds;
                }

                outRgb[o] = clamp01((float) java.lang.Math.exp(-(betaRayleighR * tauR + betaMieR * tauM)));
                outRgb[o + 1] = clamp01((float) java.lang.Math.exp(-(betaRayleighG * tauR + betaMieG * tauM)));
                outRgb[o + 2] = clamp01((float) java.lang.Math.exp(-(betaRayleighB * tauR + betaMieB * tauM)));
            }
        }
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
