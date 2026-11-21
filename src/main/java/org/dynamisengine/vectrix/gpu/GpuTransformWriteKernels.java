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
package org.dynamisengine.vectrix.gpu;

import org.dynamisengine.vectrix.affine.PackedAffineArray;
import org.dynamisengine.vectrix.affine.Transformf;

import java.nio.ByteBuffer;

/**
 * Tight write kernels for runtime transform upload staging.
 */
public final class GpuTransformWriteKernels {
    private GpuTransformWriteKernels() {
    }

    /**
     * Expand packed affines into column-major 4x4 matrices (GLSL {@code mat4} / JOML {@code Matrix4f} order).
     * Each packed transform holds rows {@code [r00 r01 r02 tx, r10 r11 r12 ty, r20 r21 r22 tz]} (see
     * {@link PackedAffineArray}), so the output is
     * {@code [r00 r10 r20 0, r01 r11 r21 0, r02 r12 r22 0, tx ty tz 1]}.
     */
    public static void writeMatrix4fFromPackedAffine(PackedAffineArray src, int[] order, float[] out, int count) {
        float[] data = src.raw();
        for (int i = 0; i < count; i++) {
            int srcBase = src.offsetOf(order[i]);
            int dst = i << 4;
            out[dst] = data[srcBase];
            out[dst + 1] = data[srcBase + 4];
            out[dst + 2] = data[srcBase + 8];
            out[dst + 3] = 0.0f;
            out[dst + 4] = data[srcBase + 1];
            out[dst + 5] = data[srcBase + 5];
            out[dst + 6] = data[srcBase + 9];
            out[dst + 7] = 0.0f;
            out[dst + 8] = data[srcBase + 2];
            out[dst + 9] = data[srcBase + 6];
            out[dst + 10] = data[srcBase + 10];
            out[dst + 11] = 0.0f;
            out[dst + 12] = data[srcBase + 3];
            out[dst + 13] = data[srcBase + 7];
            out[dst + 14] = data[srcBase + 11];
            out[dst + 15] = 1.0f;
        }
    }

    /**
     * Copy packed affines in {@code order}, keeping the row-major {@code AFFINE_3X4_ROW_MAJOR} layout of
     * {@link PackedAffineArray}.
     */
    public static void writePackedAffine(PackedAffineArray src, int[] order, float[] out, int count) {
        float[] data = src.raw();
        for (int i = 0; i < count; i++) {
            int srcBase = src.offsetOf(order[i]);
            int dst = i * 12;
            out[dst] = data[srcBase];
            out[dst + 1] = data[srcBase + 1];
            out[dst + 2] = data[srcBase + 2];
            out[dst + 3] = data[srcBase + 3];
            out[dst + 4] = data[srcBase + 4];
            out[dst + 5] = data[srcBase + 5];
            out[dst + 6] = data[srcBase + 6];
            out[dst + 7] = data[srcBase + 7];
            out[dst + 8] = data[srcBase + 8];
            out[dst + 9] = data[srcBase + 9];
            out[dst + 10] = data[srcBase + 10];
            out[dst + 11] = data[srcBase + 11];
        }
    }

    public static void writeCompactTrs(GpuTransformLayout layout, Transformf[] transforms, int[] order, ByteBuffer out, int count) {
        for (int i = 0; i < count; i++) {
            layout.writeCompactTrs(out, i, transforms[order[i]]);
        }
    }

    public static void writeFloatTrs(GpuTransformLayout layout, Transformf[] transforms, int[] order, ByteBuffer out, int count) {
        for (int i = 0; i < count; i++) {
            layout.writeFloatTrs(out, i, transforms[order[i]]);
        }
    }
}
