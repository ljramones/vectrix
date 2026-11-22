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
package org.dynamisengine.vectrix.bench;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

/**
 * Benchmark-only Vector API versions of {@code SkinningKernels.skinLbs4MatrixPalette} (rigid 4-joint LBS with a 3x4
 * row-major matrix palette), gathering the palette entries per lane. They perform the scalar kernel's operations in
 * the same order, so results are bitwise identical (checked in the benchmark setup). Two palette layouts:
 * <ul>
 * <li>{@link #skinAos}: gathers from the 12-float AoS palette with joint offsets pre-multiplied by 12 (SoA).</li>
 * <li>{@link #skinSoa}: transposes the palette into 12 SoA arrays first (part of the timed call) and gathers with
 * the raw joint indices.</li>
 * </ul>
 */
final class GatherPaletteKernel {
    private static final VectorSpecies<Float> S = FloatVector.SPECIES_PREFERRED;

    private GatherPaletteKernel() {
    }

    private static FloatVector row(FloatVector a, FloatVector b, FloatVector c, FloatVector t, FloatVector x,
            FloatVector y, FloatVector z, FloatVector w) {
        // scalar: (m0 * x + m1 * y + m2 * z + m3) * w, left to right
        return a.mul(x).add(b.mul(y)).add(c.mul(z)).add(t).mul(w);
    }

    static void skinAos(float[] palette, int[] o0, int[] o1, int[] o2, int[] o3, float[] w0, float[] w1, float[] w2,
            float[] w3, float[] inX, float[] inY, float[] inZ, float[] outX, float[] outY, float[] outZ, int count) {
        int lanes = S.length();
        int limit = count - count % lanes;
        int i = 0;
        for (; i < limit; i += lanes) {
            FloatVector x = FloatVector.fromArray(S, inX, i);
            FloatVector y = FloatVector.fromArray(S, inY, i);
            FloatVector z = FloatVector.fromArray(S, inZ, i);
            FloatVector ox = FloatVector.zero(S);
            FloatVector oy = FloatVector.zero(S);
            FloatVector oz = FloatVector.zero(S);
            for (int k = 0; k < 4; k++) {
                int[] o = k == 0 ? o0 : k == 1 ? o1 : k == 2 ? o2 : o3;
                float[] wk = k == 0 ? w0 : k == 1 ? w1 : k == 2 ? w2 : w3;
                FloatVector w = FloatVector.fromArray(S, wk, i);
                ox = ox.add(row(FloatVector.fromArray(S, palette, 0, o, i), FloatVector.fromArray(S, palette, 1, o, i),
                        FloatVector.fromArray(S, palette, 2, o, i), FloatVector.fromArray(S, palette, 3, o, i), x, y, z,
                        w));
                oy = oy.add(row(FloatVector.fromArray(S, palette, 4, o, i), FloatVector.fromArray(S, palette, 5, o, i),
                        FloatVector.fromArray(S, palette, 6, o, i), FloatVector.fromArray(S, palette, 7, o, i), x, y, z,
                        w));
                oz = oz.add(row(FloatVector.fromArray(S, palette, 8, o, i), FloatVector.fromArray(S, palette, 9, o, i),
                        FloatVector.fromArray(S, palette, 10, o, i), FloatVector.fromArray(S, palette, 11, o, i), x, y,
                        z, w));
            }
            ox.intoArray(outX, i);
            oy.intoArray(outY, i);
            oz.intoArray(outZ, i);
        }
        for (; i < count; i++) {
            float x = inX[i], y = inY[i], z = inZ[i];
            float ox = 0.0f, oy = 0.0f, oz = 0.0f;
            for (int k = 0; k < 4; k++) {
                int b = (k == 0 ? o0 : k == 1 ? o1 : k == 2 ? o2 : o3)[i];
                float w = (k == 0 ? w0 : k == 1 ? w1 : k == 2 ? w2 : w3)[i];
                ox += (palette[b] * x + palette[b + 1] * y + palette[b + 2] * z + palette[b + 3]) * w;
                oy += (palette[b + 4] * x + palette[b + 5] * y + palette[b + 6] * z + palette[b + 7]) * w;
                oz += (palette[b + 8] * x + palette[b + 9] * y + palette[b + 10] * z + palette[b + 11]) * w;
            }
            outX[i] = ox;
            outY[i] = oy;
            outZ[i] = oz;
        }
    }

    /** Transposes the AoS palette ({@code joints} × 12) into {@code soa} (12 × joints). */
    static void transpose(float[] palette, int joints, float[] soa) {
        for (int j = 0; j < joints; j++) {
            for (int e = 0; e < 12; e++) {
                soa[e * joints + j] = palette[12 * j + e];
            }
        }
    }

    static void skinSoa(float[] palette, int joints, float[] soa, int[] j0, int[] j1, int[] j2, int[] j3, float[] w0,
            float[] w1, float[] w2, float[] w3, float[] inX, float[] inY, float[] inZ, float[] outX, float[] outY,
            float[] outZ, int count) {
        transpose(palette, joints, soa);
        int lanes = S.length();
        int limit = count - count % lanes;
        int i = 0;
        for (; i < limit; i += lanes) {
            FloatVector x = FloatVector.fromArray(S, inX, i);
            FloatVector y = FloatVector.fromArray(S, inY, i);
            FloatVector z = FloatVector.fromArray(S, inZ, i);
            FloatVector ox = FloatVector.zero(S);
            FloatVector oy = FloatVector.zero(S);
            FloatVector oz = FloatVector.zero(S);
            for (int k = 0; k < 4; k++) {
                int[] j = k == 0 ? j0 : k == 1 ? j1 : k == 2 ? j2 : j3;
                float[] wk = k == 0 ? w0 : k == 1 ? w1 : k == 2 ? w2 : w3;
                FloatVector w = FloatVector.fromArray(S, wk, i);
                ox = ox.add(row(FloatVector.fromArray(S, soa, 0, j, i), FloatVector.fromArray(S, soa, joints, j, i),
                        FloatVector.fromArray(S, soa, 2 * joints, j, i), FloatVector.fromArray(S, soa, 3 * joints, j, i),
                        x, y, z, w));
                oy = oy.add(row(FloatVector.fromArray(S, soa, 4 * joints, j, i),
                        FloatVector.fromArray(S, soa, 5 * joints, j, i), FloatVector.fromArray(S, soa, 6 * joints, j, i),
                        FloatVector.fromArray(S, soa, 7 * joints, j, i), x, y, z, w));
                oz = oz.add(row(FloatVector.fromArray(S, soa, 8 * joints, j, i),
                        FloatVector.fromArray(S, soa, 9 * joints, j, i), FloatVector.fromArray(S, soa, 10 * joints, j, i),
                        FloatVector.fromArray(S, soa, 11 * joints, j, i), x, y, z, w));
            }
            ox.intoArray(outX, i);
            oy.intoArray(outY, i);
            oz.intoArray(outZ, i);
        }
        for (; i < count; i++) {
            float x = inX[i], y = inY[i], z = inZ[i];
            float ox = 0.0f, oy = 0.0f, oz = 0.0f;
            for (int k = 0; k < 4; k++) {
                int b = 12 * (k == 0 ? j0 : k == 1 ? j1 : k == 2 ? j2 : j3)[i];
                float w = (k == 0 ? w0 : k == 1 ? w1 : k == 2 ? w2 : w3)[i];
                ox += (palette[b] * x + palette[b + 1] * y + palette[b + 2] * z + palette[b + 3]) * w;
                oy += (palette[b + 4] * x + palette[b + 5] * y + palette[b + 6] * z + palette[b + 7]) * w;
                oz += (palette[b + 8] * x + palette[b + 9] * y + palette[b + 10] * z + palette[b + 11]) * w;
            }
            outX[i] = ox;
            outY[i] = oy;
            outZ[i] = oz;
        }
    }
}
