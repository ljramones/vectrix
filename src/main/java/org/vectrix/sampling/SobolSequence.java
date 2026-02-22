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
 * Deterministic Sobol low-discrepancy sequence utilities for dimensions 0..3.
 */
public final class SobolSequence {
    private static final double TO_UNIT_D = 1.0 / 4294967296.0;
    private static final float TO_UNIT_F = 1.0f / 4294967296.0f;
    private static final int MAX_BITS = 32;
    private static final int MAX_DIM = 4;
    private static final int[][] DIRECTIONS = new int[MAX_DIM][MAX_BITS];

    static {
        // dim 0: x^0
        initDirections(0, 0, 0, new int[0]);
        // dim 1: x + 1
        initDirections(1, 1, 0, new int[] {1});
        // dim 2: x^2 + x + 1
        initDirections(2, 2, 1, new int[] {1, 3});
        // dim 3: x^3 + x + 1
        initDirections(3, 3, 1, new int[] {1, 3, 1});
    }

    private SobolSequence() {
    }

    public static float sobol(int index, int dimension) {
        return (float) sobolDouble(index, dimension);
    }

    public static double sobolDouble(int index, int dimension) {
        checkIndexDimension(index, dimension);
        int bits = sobolBits(index, dimension);
        return (bits & 0xFFFFFFFFL) * TO_UNIT_D;
    }

    public static float sobolScrambled(int index, int dimension, int seed) {
        return (float) sobolScrambledDouble(index, dimension, seed);
    }

    public static double sobolScrambledDouble(int index, int dimension, int seed) {
        checkIndexDimension(index, dimension);
        int bits = sobolBits(index, dimension);
        int scramble = mixSeed(seed, dimension);
        bits ^= scramble;
        return (bits & 0xFFFFFFFFL) * TO_UNIT_D;
    }

    public static Vector2f sobol2D(int index, Vector2f dest) {
        return dest.set(sobol(index, 0), sobol(index, 1));
    }

    public static Vector2d sobol2D(int index, Vector2d dest) {
        return dest.set(sobolDouble(index, 0), sobolDouble(index, 1));
    }

    public static Vector3f sobol3D(int index, Vector3f dest) {
        return dest.set(sobol(index, 0), sobol(index, 1), sobol(index, 2));
    }

    public static Vector3d sobol3D(int index, Vector3d dest) {
        return dest.set(sobolDouble(index, 0), sobolDouble(index, 1), sobolDouble(index, 2));
    }

    public static Vector2f sobolScrambled2D(int index, int seed, Vector2f dest) {
        return dest.set(sobolScrambled(index, 0, seed), sobolScrambled(index, 1, seed));
    }

    public static Vector2d sobolScrambled2D(int index, int seed, Vector2d dest) {
        return dest.set(sobolScrambledDouble(index, 0, seed), sobolScrambledDouble(index, 1, seed));
    }

    public static Vector3f sobolScrambled3D(int index, int seed, Vector3f dest) {
        return dest.set(sobolScrambled(index, 0, seed), sobolScrambled(index, 1, seed), sobolScrambled(index, 2, seed));
    }

    public static Vector3d sobolScrambled3D(int index, int seed, Vector3d dest) {
        return dest.set(sobolScrambledDouble(index, 0, seed), sobolScrambledDouble(index, 1, seed), sobolScrambledDouble(index, 2, seed));
    }

    public static void sobolBatch2D(int startIndex, int count, float[] outX, int outXOffset, float[] outY, int outYOffset) {
        checkBatch(startIndex, count, outX.length, outXOffset, outY.length, outYOffset);
        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            outX[outXOffset + i] = sobol(idx, 0);
            outY[outYOffset + i] = sobol(idx, 1);
        }
    }

    public static void sobolBatch2D(int startIndex, int count, double[] outX, int outXOffset, double[] outY, int outYOffset) {
        checkBatch(startIndex, count, outX.length, outXOffset, outY.length, outYOffset);
        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            outX[outXOffset + i] = sobolDouble(idx, 0);
            outY[outYOffset + i] = sobolDouble(idx, 1);
        }
    }

    public static void sobolScrambledBatch2D(int startIndex, int count, int seed, float[] outX, int outXOffset, float[] outY, int outYOffset) {
        checkBatch(startIndex, count, outX.length, outXOffset, outY.length, outYOffset);
        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            outX[outXOffset + i] = sobolScrambled(idx, 0, seed);
            outY[outYOffset + i] = sobolScrambled(idx, 1, seed);
        }
    }

    public static void sobolScrambledBatch2D(int startIndex, int count, int seed, double[] outX, int outXOffset, double[] outY, int outYOffset) {
        checkBatch(startIndex, count, outX.length, outXOffset, outY.length, outYOffset);
        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            outX[outXOffset + i] = sobolScrambledDouble(idx, 0, seed);
            outY[outYOffset + i] = sobolScrambledDouble(idx, 1, seed);
        }
    }

    private static int sobolBits(int index, int dim) {
        int gray = index ^ (index >>> 1);
        int x = 0;
        int bit = 0;
        while (gray != 0 && bit < MAX_BITS) {
            if ((gray & 1) != 0) {
                x ^= DIRECTIONS[dim][bit];
            }
            gray >>>= 1;
            bit++;
        }
        return x;
    }

    private static void checkIndexDimension(int index, int dimension) {
        if (index < 0) {
            throw new IllegalArgumentException("index");
        }
        if (dimension < 0 || dimension >= MAX_DIM) {
            throw new IllegalArgumentException("dimension");
        }
    }

    private static int mixSeed(int seed, int dimension) {
        int x = seed + 0x9E3779B9 + (dimension + 1) * 0x7F4A7C15;
        x ^= x >>> 16;
        x *= 0x85EBCA6B;
        x ^= x >>> 13;
        x *= 0xC2B2AE35;
        x ^= x >>> 16;
        return x;
    }

    private static void initDirections(int dim, int s, int a, int[] m) {
        int[] v = DIRECTIONS[dim];
        if (s == 0) {
            for (int i = 0; i < MAX_BITS; i++) {
                v[i] = 1 << (31 - i);
            }
            return;
        }
        for (int i = 1; i <= s; i++) {
            v[i - 1] = m[i - 1] << (32 - i);
        }
        for (int i = s + 1; i <= MAX_BITS; i++) {
            int value = v[i - s - 1] ^ (v[i - s - 1] >>> s);
            for (int k = 1; k <= s - 1; k++) {
                if (((a >>> (s - 1 - k)) & 1) != 0) {
                    value ^= v[i - k - 1];
                }
            }
            v[i - 1] = value;
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
