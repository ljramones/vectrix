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
 * Builds preintegrated SSS LUTs into caller-provided flat RGB arrays.
 */
public final class SssLutBuilder {
    private SssLutBuilder() {
    }

    /**
     * Build a 2D preintegrated SSS LUT with axes:
     * x = NdotL in [0,1], y = curvature in [0,1] mapped to [0,maxCurvature].
     *
     * @param width LUT width
     * @param height LUT height
     * @param profile sum-of-Gaussians diffusion profile
     * @param maxCurvature maximum curvature (1/radius) represented in LUT
     * @param maxArcLength integration half-range along surface arc
     * @param sampleCount integration samples along arc
     * @param outRgb output array length >= width*height*3, interleaved RGB
     */
    public static void build(int width, int height, SssProfile profile, float maxCurvature, float maxArcLength, int sampleCount, float[] outRgb) {
        if (width <= 0 || height <= 0 || sampleCount <= 0 || profile == null || maxCurvature <= 0.0f || maxArcLength <= 0.0f) {
            throw new IllegalArgumentException("params");
        }
        if (outRgb == null || outRgb.length < width * height * 3) {
            throw new IllegalArgumentException("outRgb");
        }
        float[] p = new float[3];
        for (int y = 0; y < height; y++) {
            float v = (y + 0.5f) / height;
            float curvature = v * maxCurvature;
            float radius = curvature > 1E-6f ? 1.0f / curvature : 1E6f;
            for (int x = 0; x < width; x++) {
                float u = (x + 0.5f) / width;
                float ndotl = u;
                float theta = (float) java.lang.Math.acos(clamp01(ndotl));

                float ir = 0.0f, ig = 0.0f, ib = 0.0f;
                float ds = (2.0f * maxArcLength) / sampleCount;
                for (int i = 0; i < sampleCount; i++) {
                    float s = -maxArcLength + (i + 0.5f) * ds;
                    float shifted = theta + s / radius;
                    float localNoL = (float) java.lang.Math.cos(shifted);
                    if (localNoL <= 0.0f) {
                        continue;
                    }
                    profile.evaluate(java.lang.Math.abs(s), p);
                    ir += p[0] * localNoL * ds;
                    ig += p[1] * localNoL * ds;
                    ib += p[2] * localNoL * ds;
                }

                int o = (y * width + x) * 3;
                outRgb[o] = java.lang.Math.max(0.0f, ir);
                outRgb[o + 1] = java.lang.Math.max(0.0f, ig);
                outRgb[o + 2] = java.lang.Math.max(0.0f, ib);
            }
        }
    }

    private static float clamp01(float v) {
        return java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, v));
    }
}
