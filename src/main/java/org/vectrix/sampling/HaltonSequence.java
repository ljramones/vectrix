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
package org.vectrix.sampling;

import org.vectrix.core.Vector2d;
import org.vectrix.core.Vector2f;
import org.vectrix.core.Vector3d;
import org.vectrix.core.Vector3f;

/**
 * Deterministic Halton low-discrepancy sequence utilities.
  * @since 1.0.0
  */
public final class HaltonSequence {
    private HaltonSequence() {
    }

    public static float halton(int index, int base) {
        return (float) haltonDouble(index, base);
    }

    public static double haltonDouble(int index, int base) {
        if (index < 0) {
            throw new IllegalArgumentException("index");
        }
        if (base < 2) {
            throw new IllegalArgumentException("base");
        }
        double f = 1.0;
        double r = 0.0;
        int i = index;
        while (i > 0) {
            f /= base;
            r += f * (i % base);
            i /= base;
        }
        return r;
    }

    public static Vector2f halton2D(int index, Vector2f dest) {
        return dest.set(halton(index, 2), halton(index, 3));
    }

    public static Vector2d halton2D(int index, Vector2d dest) {
        return dest.set(haltonDouble(index, 2), haltonDouble(index, 3));
    }

    public static Vector3f halton3D(int index, Vector3f dest) {
        return dest.set(halton(index, 2), halton(index, 3), halton(index, 5));
    }

    public static Vector3d halton3D(int index, Vector3d dest) {
        return dest.set(haltonDouble(index, 2), haltonDouble(index, 3), haltonDouble(index, 5));
    }

    public static void haltonBatch2D(int startIndex, int count, float[] outX, int outXOffset, float[] outY, int outYOffset) {
        checkBatch(startIndex, count, outX.length, outXOffset, outY.length, outYOffset);
        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            outX[outXOffset + i] = halton(idx, 2);
            outY[outYOffset + i] = halton(idx, 3);
        }
    }

    public static void haltonBatch2D(int startIndex, int count, double[] outX, int outXOffset, double[] outY, int outYOffset) {
        checkBatch(startIndex, count, outX.length, outXOffset, outY.length, outYOffset);
        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            outX[outXOffset + i] = haltonDouble(idx, 2);
            outY[outYOffset + i] = haltonDouble(idx, 3);
        }
    }

    private static void checkBatch(int startIndex, int count, int xLen, int outXOffset, int yLen, int outYOffset) {
        if (startIndex < 0) {
            throw new IllegalArgumentException("startIndex");
        }
        if (count < 0) {
            throw new IllegalArgumentException("count");
        }
        if (outXOffset < 0 || outYOffset < 0 || outXOffset + count > xLen || outYOffset + count > yLen) {
            throw new IllegalArgumentException("output bounds");
        }
    }
}
