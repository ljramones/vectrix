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
 * Hashes of integer grid cells, for spatial hash tables (broad-phase collision, particle neighbor search). Reduce
 * the result to a bucket index with {@code Integer.remainderUnsigned(hash, buckets)} or a power-of-two mask.
 * <p>
 * The methods are pure functions of their arguments: they give the same bits on every platform, do not allocate and
 * are safe to call from any thread.
 *
 * @since 1.0.0
 */

public final class SpatialHash {
    private static final int PRIME_X = 73856093;
    private static final int PRIME_Y = 19349663;
    private static final int PRIME_Z = 83492791;

    private SpatialHash() {
    }

    /**
     * Hashes the 3D cell {@code (x, y, z)}: the coordinates multiplied by three large primes and combined with XOR,
     * then mixed with {@link PcgHash#hash32(int)}. Different cells can share a hash, so tables must still compare
     * the cell coordinates.
     *
     * @param x the cell's x index
     * @param y the cell's y index
     * @param z the cell's z index
     * @return the hash, any 32-bit value
     */
    public static int hashCell3i(int x, int y, int z) {
        int mix = x * PRIME_X ^ y * PRIME_Y ^ z * PRIME_Z;
        return PcgHash.hash32(mix);
    }
}
