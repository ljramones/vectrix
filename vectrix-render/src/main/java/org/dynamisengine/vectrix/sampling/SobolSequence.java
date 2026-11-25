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
package org.dynamisengine.vectrix.sampling;

import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;

/**
 * Sobol low-discrepancy sequence for dimensions 0 to 3, with optional XOR scrambling or Owen scrambling by a seed.
 * All functions are pure and stateless: the same index, dimension and seed always give the same value, and no
 * method allocates. Values are in {@code [0, 1)} with 32 bits of resolution; the float variants are clamped to the
 * largest float below 1.
 *
 * @since 1.0.0
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

    /**
     * Returns one coordinate of the unscrambled Sobol point at {@code index}, in single precision.
     *
     * @param index the sample index, non-negative
     * @param dimension the dimension, 0 to 3
     * @return the coordinate, in {@code [0, 1)}
     * @throws IllegalArgumentException if {@code index} is negative or {@code dimension} is outside 0 to 3
     */
    public static float sobol(int index, int dimension) {
        return toUnitFloat(sobolDouble(index, dimension));
    }

    /**
     * Returns one coordinate of the unscrambled Sobol point at {@code index}, in double precision. The value has 32
     * bits of resolution.
     *
     * @param index the sample index, non-negative
     * @param dimension the dimension, 0 to 3
     * @return the coordinate, in {@code [0, 1)}
     * @throws IllegalArgumentException if {@code index} is negative or {@code dimension} is outside 0 to 3
     */
    public static double sobolDouble(int index, int dimension) {
        checkIndexDimension(index, dimension);
        int bits = sobolBits(index, dimension);
        return (bits & 0xFFFFFFFFL) * TO_UNIT_D;
    }

    /**
     * Returns one coordinate of the XOR-scrambled Sobol point at {@code index}, in single precision. See
     * {@link #sobolScrambledDouble}.
     *
     * @param index the sample index, non-negative
     * @param dimension the dimension, 0 to 3
     * @param seed the scrambling seed; any value
     * @return the coordinate, in {@code [0, 1)}
     * @throws IllegalArgumentException if {@code index} is negative or {@code dimension} is outside 0 to 3
     */
    public static float sobolScrambled(int index, int dimension, int seed) {
        return toUnitFloat(sobolScrambledDouble(index, dimension, seed));
    }

    /**
     * Returns one coordinate of the XOR-scrambled Sobol point at {@code index}, in double precision: the Sobol bits are
     * XORed with a hash of {@code seed} and {@code dimension} (a random digital shift). Each seed gives a different,
     * equally well distributed point set. For decorrelated seeds without structured artefacts, prefer
     * {@link #sobolOwenDouble}.
     *
     * @param index the sample index, non-negative
     * @param dimension the dimension, 0 to 3
     * @param seed the scrambling seed; any value
     * @return the coordinate, in {@code [0, 1)}
     * @throws IllegalArgumentException if {@code index} is negative or {@code dimension} is outside 0 to 3
     */
    public static double sobolScrambledDouble(int index, int dimension, int seed) {
        checkIndexDimension(index, dimension);
        int bits = sobolBits(index, dimension);
        int scramble = mixSeed(seed, dimension);
        bits ^= scramble;
        return (bits & 0xFFFFFFFFL) * TO_UNIT_D;
    }

    /**
     * Writes dimensions 0 and 1 of the unscrambled Sobol point at {@code index} into {@code dest}, in single precision.
     *
     * @param index the sample index, non-negative
     * @param dest receives the point, in {@code [0, 1)²}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector2f sobol2D(int index, Vector2f dest) {
        return dest.set(sobol(index, 0), sobol(index, 1));
    }

    /**
     * Writes dimensions 0 and 1 of the unscrambled Sobol point at {@code index} into {@code dest}, in double precision.
     *
     * @param index the sample index, non-negative
     * @param dest receives the point, in {@code [0, 1)²}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector2d sobol2D(int index, Vector2d dest) {
        return dest.set(sobolDouble(index, 0), sobolDouble(index, 1));
    }

    /**
     * Writes dimensions 0, 1 and 2 of the unscrambled Sobol point at {@code index} into {@code dest}, in single
     * precision.
     *
     * @param index the sample index, non-negative
     * @param dest receives the point, in {@code [0, 1)³}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector3f sobol3D(int index, Vector3f dest) {
        return dest.set(sobol(index, 0), sobol(index, 1), sobol(index, 2));
    }

    /**
     * Writes dimensions 0, 1 and 2 of the unscrambled Sobol point at {@code index} into {@code dest}, in double
     * precision.
     *
     * @param index the sample index, non-negative
     * @param dest receives the point, in {@code [0, 1)³}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector3d sobol3D(int index, Vector3d dest) {
        return dest.set(sobolDouble(index, 0), sobolDouble(index, 1), sobolDouble(index, 2));
    }

    /**
     * Writes dimensions 0 and 1 of the XOR-scrambled Sobol point at {@code index} into {@code dest}, in single
     * precision. See {@link #sobolScrambledDouble}.
     *
     * @param index the sample index, non-negative
     * @param seed the scrambling seed; any value
     * @param dest receives the point, in {@code [0, 1)²}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector2f sobolScrambled2D(int index, int seed, Vector2f dest) {
        return dest.set(sobolScrambled(index, 0, seed), sobolScrambled(index, 1, seed));
    }

    /**
     * Writes dimensions 0 and 1 of the XOR-scrambled Sobol point at {@code index} into {@code dest}, in double
     * precision. See {@link #sobolScrambledDouble}.
     *
     * @param index the sample index, non-negative
     * @param seed the scrambling seed; any value
     * @param dest receives the point, in {@code [0, 1)²}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector2d sobolScrambled2D(int index, int seed, Vector2d dest) {
        return dest.set(sobolScrambledDouble(index, 0, seed), sobolScrambledDouble(index, 1, seed));
    }

    /**
     * Writes dimensions 0, 1 and 2 of the XOR-scrambled Sobol point at {@code index} into {@code dest}, in single
     * precision. See {@link #sobolScrambledDouble}.
     *
     * @param index the sample index, non-negative
     * @param seed the scrambling seed; any value
     * @param dest receives the point, in {@code [0, 1)³}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector3f sobolScrambled3D(int index, int seed, Vector3f dest) {
        return dest.set(sobolScrambled(index, 0, seed), sobolScrambled(index, 1, seed), sobolScrambled(index, 2, seed));
    }

    /**
     * Writes dimensions 0, 1 and 2 of the XOR-scrambled Sobol point at {@code index} into {@code dest}, in double
     * precision. See {@link #sobolScrambledDouble}.
     *
     * @param index the sample index, non-negative
     * @param seed the scrambling seed; any value
     * @param dest receives the point, in {@code [0, 1)³}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector3d sobolScrambled3D(int index, int seed, Vector3d dest) {
        return dest.set(sobolScrambledDouble(index, 0, seed), sobolScrambledDouble(index, 1, seed), sobolScrambledDouble(index, 2, seed));
    }

    /**
     * Writes dimensions 0 and 1 of the unscrambled Sobol points {@code startIndex} to {@code startIndex + count - 1}
     * into two float arrays: point {@code i} goes to {@code outX[outXOffset + i]} and {@code outY[outYOffset + i]}.
     * The arguments are checked before any element is written.
     *
     * @param startIndex the index of the first point, non-negative
     * @param count the number of points, non-negative
     * @param outX receives the x coordinates
     * @param outXOffset the first index written in {@code outX}
     * @param outY receives the y coordinates
     * @param outYOffset the first index written in {@code outY}
     * @throws IllegalArgumentException if {@code startIndex} or {@code count} is negative, the last index
     *         {@code startIndex + count - 1} exceeds {@code Integer.MAX_VALUE}, or an output range is outside its array
     */
    public static void sobolBatch2D(int startIndex, int count, float[] outX, int outXOffset, float[] outY, int outYOffset) {
        checkBatch(startIndex, count, outX.length, outXOffset, outY.length, outYOffset);
        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            outX[outXOffset + i] = sobol(idx, 0);
            outY[outYOffset + i] = sobol(idx, 1);
        }
    }

    /**
     * Writes dimensions 0 and 1 of the unscrambled Sobol points {@code startIndex} to {@code startIndex + count - 1}
     * into two double arrays: point {@code i} goes to {@code outX[outXOffset + i]} and {@code outY[outYOffset + i]}.
     * The arguments are checked before any element is written.
     *
     * @param startIndex the index of the first point, non-negative
     * @param count the number of points, non-negative
     * @param outX receives the x coordinates
     * @param outXOffset the first index written in {@code outX}
     * @param outY receives the y coordinates
     * @param outYOffset the first index written in {@code outY}
     * @throws IllegalArgumentException if {@code startIndex} or {@code count} is negative, the last index
     *         {@code startIndex + count - 1} exceeds {@code Integer.MAX_VALUE}, or an output range is outside its array
     */
    public static void sobolBatch2D(int startIndex, int count, double[] outX, int outXOffset, double[] outY, int outYOffset) {
        checkBatch(startIndex, count, outX.length, outXOffset, outY.length, outYOffset);
        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            outX[outXOffset + i] = sobolDouble(idx, 0);
            outY[outYOffset + i] = sobolDouble(idx, 1);
        }
    }

    /**
     * Writes dimensions 0 and 1 of the XOR-scrambled Sobol points {@code startIndex} to
     * {@code startIndex + count - 1} into two float arrays: point {@code i} goes to {@code outX[outXOffset + i]} and
     * {@code outY[outYOffset + i]}. See {@link #sobolScrambledDouble}. The arguments are checked before any element is
     * written.
     *
     * @param startIndex the index of the first point, non-negative
     * @param count the number of points, non-negative
     * @param seed the scrambling seed; any value
     * @param outX receives the x coordinates
     * @param outXOffset the first index written in {@code outX}
     * @param outY receives the y coordinates
     * @param outYOffset the first index written in {@code outY}
     * @throws IllegalArgumentException if {@code startIndex} or {@code count} is negative, the last index
     *         {@code startIndex + count - 1} exceeds {@code Integer.MAX_VALUE}, or an output range is outside its array
     */
    public static void sobolScrambledBatch2D(int startIndex, int count, int seed, float[] outX, int outXOffset, float[] outY, int outYOffset) {
        checkBatch(startIndex, count, outX.length, outXOffset, outY.length, outYOffset);
        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            outX[outXOffset + i] = sobolScrambled(idx, 0, seed);
            outY[outYOffset + i] = sobolScrambled(idx, 1, seed);
        }
    }

    /**
     * Writes dimensions 0 and 1 of the XOR-scrambled Sobol points {@code startIndex} to
     * {@code startIndex + count - 1} into two double arrays: point {@code i} goes to {@code outX[outXOffset + i]} and
     * {@code outY[outYOffset + i]}. See {@link #sobolScrambledDouble}. The arguments are checked before any element is
     * written.
     *
     * @param startIndex the index of the first point, non-negative
     * @param count the number of points, non-negative
     * @param seed the scrambling seed; any value
     * @param outX receives the x coordinates
     * @param outXOffset the first index written in {@code outX}
     * @param outY receives the y coordinates
     * @param outYOffset the first index written in {@code outY}
     * @throws IllegalArgumentException if {@code startIndex} or {@code count} is negative, the last index
     *         {@code startIndex + count - 1} exceeds {@code Integer.MAX_VALUE}, or an output range is outside its array
     */
    public static void sobolScrambledBatch2D(int startIndex, int count, int seed, double[] outX, int outXOffset, double[] outY, int outYOffset) {
        checkBatch(startIndex, count, outX.length, outXOffset, outY.length, outYOffset);
        for (int i = 0; i < count; i++) {
            int idx = startIndex + i;
            outX[outXOffset + i] = sobolScrambledDouble(idx, 0, seed);
            outY[outYOffset + i] = sobolScrambledDouble(idx, 1, seed);
        }
    }

    /**
     * Returns one coordinate of the Owen-scrambled Sobol point at {@code index}, in double precision (Burley 2020,
     * "Practical Hash-based Owen Scrambling"). The index is shuffled and each coordinate is nested-uniform scrambled
     * with a hash of {@code seed} and the dimension. Unlike {@link #sobolScrambledDouble} (a random XOR), this keeps
     * the (0, m, 2)-net structure of dimensions 0 and 1 and decorrelates seeds, which removes the structured
     * artifacts of unscrambled Sobol in rendering.
     *
     * @param index the sample index, non-negative
     * @param dimension the dimension, 0 to 3
     * @param seed the scrambling seed; any value
     * @return the coordinate, in {@code [0, 1)}
     * @throws IllegalArgumentException if {@code index} is negative or {@code dimension} is outside 0 to 3
     */
    public static double sobolOwenDouble(int index, int dimension, int seed) {
        checkIndexDimension(index, dimension);
        int shuffled = nestedUniformScramble(index, hash(seed));
        int bits = sobolBits(shuffled, dimension);
        bits = nestedUniformScramble(bits, hash(hashCombine(seed, dimension)));
        return (bits & 0xFFFFFFFFL) * TO_UNIT_D;
    }

    /**
     * Returns one coordinate of the Owen-scrambled Sobol point at {@code index}, in single precision. See
     * {@link #sobolOwenDouble}.
     *
     * @param index the sample index, non-negative
     * @param dimension the dimension, 0 to 3
     * @param seed the scrambling seed; any value
     * @return the coordinate, in {@code [0, 1)}
     * @throws IllegalArgumentException if {@code index} is negative or {@code dimension} is outside 0 to 3
     */
    public static float sobolOwen(int index, int dimension, int seed) {
        return toUnitFloat(sobolOwenDouble(index, dimension, seed));
    }

    /**
     * Writes dimensions 0 and 1 of the Owen-scrambled Sobol point at {@code index} into {@code dest}, in double
     * precision. See {@link #sobolOwenDouble}.
     *
     * @param index the sample index, non-negative
     * @param seed the scrambling seed; any value
     * @param dest receives the point, in {@code [0, 1)²}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector2d sobolOwen2D(int index, int seed, Vector2d dest) {
        return dest.set(sobolOwenDouble(index, 0, seed), sobolOwenDouble(index, 1, seed));
    }

    /**
     * Writes dimensions 0 and 1 of the Owen-scrambled Sobol point at {@code index} into {@code dest}, in single
     * precision. See {@link #sobolOwenDouble}.
     *
     * @param index the sample index, non-negative
     * @param seed the scrambling seed; any value
     * @param dest receives the point, in {@code [0, 1)²}
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code index} is negative
     */
    public static Vector2f sobolOwen2D(int index, int seed, Vector2f dest) {
        return dest.set(sobolOwen(index, 0, seed), sobolOwen(index, 1, seed));
    }

    /** Laine-Karras style permutation of the bits of {@code x} (Burley's improved constants), applied to reversed bits. */
    private static int laineKarrasPermutation(int x, int seed) {
        x ^= x * 0x3d20adea;
        x += seed;
        x *= (seed >>> 16) | 1;
        x ^= x * 0x05526c56;
        x ^= x * 0x53a22864;
        return x;
    }

    private static int nestedUniformScramble(int x, int seed) {
        return Integer.reverse(laineKarrasPermutation(Integer.reverse(x), seed));
    }

    private static int hashCombine(int seed, int v) {
        return seed ^ (v + (seed << 6) + (seed >>> 2));
    }

    private static int hash(int x) {
        // lowbias32 (Wellons)
        x ^= x >>> 16;
        x *= 0x21f0aaad;
        x ^= x >>> 15;
        x *= 0xd35a2d97;
        x ^= x >>> 15;
        return x;
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
        // long arithmetic: int sums of large values would wrap and pass the checks
        if ((long) startIndex + count - 1 > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("startIndex + count exceeds the int index range");
        }
        if (outXOffset < 0 || outYOffset < 0 || (long) outXOffset + count > xLen || (long) outYOffset + count > yLen) {
            throw new IllegalArgumentException("output bounds");
        }
    }

    /**
     * Narrow a [0, 1) double to float without leaving [0, 1): rounding to float sends values within half an ulp
     * of 1 up to exactly 1.0f.
     */
    private static float toUnitFloat(double v) {
        return java.lang.Math.min((float) v, ONE_BELOW_F);
    }

    private static final float ONE_BELOW_F = java.lang.Math.nextDown(1.0f);
}
