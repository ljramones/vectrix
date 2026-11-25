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
package org.dynamisengine.vectrix.hash;

/**
 * Stateless 32-bit integer hashes built on the PCG generator: one LCG step followed by the RXS-M-XS output
 * permutation. Each input bit affects every output bit, so consecutive inputs give unrelated outputs, which suits
 * procedural noise, per-cell random values and seeding. Not cryptographic.
 * <p>
 * The methods are pure functions of their arguments: they give the same bits on every platform, do not allocate and
 * are safe to call from any thread.
 *
 * @since 1.0.0
 */

public final class PcgHash {
    private static final int PCG_MULTIPLIER = 747796405;
    private static final int PCG_INCREMENT = (int) 2891336453L;

    private PcgHash() {
    }

    /**
     * Hashes one 32-bit value.
     *
     * @param input the value to hash
     * @return the hash, any 32-bit value
     */
    public static int hash32(int input) {
        int state = input * PCG_MULTIPLIER + PCG_INCREMENT;
        int word = ((state >>> ((state >>> 28) + 4)) ^ state) * 277803737;
        return (word >>> 22) ^ word;
    }

    /**
     * Hashes three 32-bit values by chaining {@link #hash32(int)}: {@code hash32(hash32(hash32(x) ^ y) ^ z)}. The
     * order of the arguments matters.
     *
     * @param x the first value
     * @param y the second value
     * @param z the third value
     * @return the hash, any 32-bit value
     */
    public static int hash32(int x, int y, int z) {
        int h = hash32(x);
        h = hash32(h ^ y);
        h = hash32(h ^ z);
        return h;
    }
}
