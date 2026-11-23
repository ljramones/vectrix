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
package org.dynamisengine.vectrix.geometry;

/**
 * Space-filling-curve codes for sorting points and building linear BVHs: Morton (Z-order) codes in 2D and 3D with
 * 32- and 64-bit results, and Hilbert curve indices, which keep consecutive indices spatially adjacent. Inputs are
 * non-negative integer grid coordinates; quantise floats first (for example {@code (int) ((x - min) / extent * 1024)}).
 * <p>
 * Morton encoders keep only the low bits of each coordinate that fit the code (16, 32, 10 or 21); higher bits are
 * ignored. The class is stateless and safe to call from any thread. The Morton and 2D Hilbert methods do not
 * allocate; the 3D Hilbert methods allocate a three-element {@code int} array per call.
 *
 * @since 2.2.0
 */
public final class SpatialCodes {
    private SpatialCodes() {
    }

    // ---- Morton ----

    private static long spread2(long x) {
        x &= 0xFFFFFFFFL;
        x = (x | (x << 16)) & 0x0000FFFF0000FFFFL;
        x = (x | (x << 8)) & 0x00FF00FF00FF00FFL;
        x = (x | (x << 4)) & 0x0F0F0F0F0F0F0F0FL;
        x = (x | (x << 2)) & 0x3333333333333333L;
        x = (x | (x << 1)) & 0x5555555555555555L;
        return x;
    }

    private static long compact2(long x) {
        x &= 0x5555555555555555L;
        x = (x | (x >>> 1)) & 0x3333333333333333L;
        x = (x | (x >>> 2)) & 0x0F0F0F0F0F0F0F0FL;
        x = (x | (x >>> 4)) & 0x00FF00FF00FF00FFL;
        x = (x | (x >>> 8)) & 0x0000FFFF0000FFFFL;
        x = (x | (x >>> 16)) & 0x00000000FFFFFFFFL;
        return x;
    }

    private static long spread3(long x) {
        x &= 0x1FFFFFL;
        x = (x | (x << 32)) & 0x1F00000000FFFFL;
        x = (x | (x << 16)) & 0x1F0000FF0000FFL;
        x = (x | (x << 8)) & 0x100F00F00F00F00FL;
        x = (x | (x << 4)) & 0x10C30C30C30C30C3L;
        x = (x | (x << 2)) & 0x1249249249249249L;
        return x;
    }

    private static long compact3(long x) {
        x &= 0x1249249249249249L;
        x = (x | (x >>> 2)) & 0x10C30C30C30C30C3L;
        x = (x | (x >>> 4)) & 0x100F00F00F00F00FL;
        x = (x | (x >>> 8)) & 0x1F0000FF0000FFL;
        x = (x | (x >>> 16)) & 0x1F00000000FFFFL;
        x = (x | (x >>> 32)) & 0x1FFFFFL;
        return x;
    }

    /**
     * 2D Morton code of {@code x, y} in {@code [0, 65535]}: bit {@code i} of x goes to bit {@code 2i}, of y to
     * {@code 2i + 1}. All 32 bits are used, so the code is negative when bit 15 of {@code y} is set; compare codes
     * as unsigned ints, and widen with {@link Integer#toUnsignedLong} before decoding.
     *
     * @param x the x cell coordinate; only the low 16 bits are used
     * @param y the y cell coordinate; only the low 16 bits are used
     * @return the 32-bit Morton code, as an unsigned int
     */
    public static int morton2d(int x, int y) {
        return (int) (spread2(x & 0xFFFF) | (spread2(y & 0xFFFF) << 1));
    }

    /**
     * 64-bit 2D Morton code of {@code x, y} in {@code [0, 2³² - 1]} (as unsigned ints). The code can be negative as
     * a {@code long}; compare codes as unsigned longs.
     *
     * @param x the x cell coordinate, read as an unsigned int
     * @param y the y cell coordinate, read as an unsigned int
     * @return the 64-bit Morton code, as an unsigned long
     */
    public static long morton2d64(int x, int y) {
        return spread2(x & 0xFFFFFFFFL) | (spread2(y & 0xFFFFFFFFL) << 1);
    }

    /**
     * Decodes x from a 2D Morton code (32- or 64-bit). Pass a 32-bit code from {@link #morton2d} zero-extended
     * ({@code Integer.toUnsignedLong(code)}): a negative int widened with sign extension decodes to a wrong value.
     *
     * @param code the Morton code
     * @return the x cell coordinate (an unsigned int for 64-bit codes)
     */
    public static int morton2dX(long code) {
        return (int) compact2(code);
    }

    /**
     * Decodes y from a 2D Morton code (32- or 64-bit). Pass a 32-bit code from {@link #morton2d} zero-extended
     * ({@code Integer.toUnsignedLong(code)}): a negative int widened with sign extension decodes to a wrong value.
     *
     * @param code the Morton code
     * @return the y cell coordinate (an unsigned int for 64-bit codes)
     */
    public static int morton2dY(long code) {
        return (int) compact2(code >>> 1);
    }

    /**
     * 3D Morton code of {@code x, y, z} in {@code [0, 1023]} (10 bits each, 30 bits used): bit {@code i} of x goes
     * to bit {@code 3i}, of y to {@code 3i + 1}, of z to {@code 3i + 2}.
     *
     * @param x the x cell coordinate; only the low 10 bits are used
     * @param y the y cell coordinate; only the low 10 bits are used
     * @param z the z cell coordinate; only the low 10 bits are used
     * @return the Morton code, non-negative
     */
    public static int morton3d(int x, int y, int z) {
        return (int) (spread3(x & 0x3FF) | (spread3(y & 0x3FF) << 1) | (spread3(z & 0x3FF) << 2));
    }

    /**
     * 64-bit 3D Morton code of {@code x, y, z} in {@code [0, 2²¹ - 1]} (21 bits each, 63 bits used).
     *
     * @param x the x cell coordinate; only the low 21 bits are used
     * @param y the y cell coordinate; only the low 21 bits are used
     * @param z the z cell coordinate; only the low 21 bits are used
     * @return the Morton code, non-negative
     */
    public static long morton3d64(int x, int y, int z) {
        return spread3(x & 0x1FFFFF) | (spread3(y & 0x1FFFFF) << 1) | (spread3(z & 0x1FFFFF) << 2);
    }

    /**
     * Decodes x from a 3D Morton code (32- or 64-bit).
     *
     * @param code the Morton code
     * @return the x cell coordinate
     */
    public static int morton3dX(long code) {
        return (int) compact3(code);
    }

    /**
     * Decodes y from a 3D Morton code (32- or 64-bit).
     *
     * @param code the Morton code
     * @return the y cell coordinate
     */
    public static int morton3dY(long code) {
        return (int) compact3(code >>> 1);
    }

    /**
     * Decodes z from a 3D Morton code (32- or 64-bit).
     *
     * @param code the Morton code
     * @return the z cell coordinate
     */
    public static int morton3dZ(long code) {
        return (int) compact3(code >>> 2);
    }

    // ---- Hilbert ----

    /**
     * Index along the 2D Hilbert curve of order {@code bits} (a {@code 2^bits × 2^bits} grid, {@code bits <= 31}) of
     * cell {@code (x, y)}. Consecutive indices are always edge-adjacent cells. The curve starts at {@code (0, 0)}.
     *
     * @param x the x cell coordinate, in {@code [0, 2^bits)}
     * @param y the y cell coordinate, in {@code [0, 2^bits)}
     * @param bits the curve order, in {@code [0, 31]}
     * @return the Hilbert index, in {@code [0, 4^bits)}
     */
    public static long hilbert2d(int x, int y, int bits) {
        long d = 0;
        long n = 1L << bits;
        long xx = x, yy = y;
        for (long s = n >>> 1; s > 0; s >>>= 1) {
            long rx = (xx & s) > 0 ? 1 : 0;
            long ry = (yy & s) > 0 ? 1 : 0;
            d += s * s * ((3 * rx) ^ ry);
            if (ry == 0) {
                if (rx == 1) {
                    xx = s - 1 - xx;
                    yy = s - 1 - yy;
                }
                long t = xx;
                xx = yy;
                yy = t;
            }
        }
        return d;
    }

    /**
     * Inverse of {@link #hilbert2d}: writes {@code x} to {@code out[0]} and {@code y} to {@code out[1]}.
     *
     * @param d the Hilbert index, in {@code [0, 4^bits)}
     * @param bits the curve order, in {@code [0, 31]}
     * @param out receives the cell coordinates; at least 2 elements
     */
    public static void hilbert2dToXy(long d, int bits, int[] out) {
        long n = 1L << bits;
        long x = 0, y = 0, t = d;
        for (long s = 1; s < n; s <<= 1) {
            long rx = 1 & (t >>> 1);
            long ry = 1 & (t ^ rx);
            if (ry == 0) {
                if (rx == 1) {
                    x = s - 1 - x;
                    y = s - 1 - y;
                }
                long tmp = x;
                x = y;
                y = tmp;
            }
            x += s * rx;
            y += s * ry;
            t >>>= 2;
        }
        out[0] = (int) x;
        out[1] = (int) y;
    }

    /**
     * Index along the 3D Hilbert curve of order {@code bits} ({@code bits <= 21}) of cell {@code (x, y, z)}, by
     * Skilling's transpose algorithm (2004). Consecutive indices are face-adjacent cells. Allocates a three-element
     * {@code int} array per call.
     *
     * @param x the x cell coordinate, in {@code [0, 2^bits)}
     * @param y the y cell coordinate, in {@code [0, 2^bits)}
     * @param z the z cell coordinate, in {@code [0, 2^bits)}
     * @param bits the curve order, in {@code [1, 21]}
     * @return the Hilbert index, in {@code [0, 8^bits)}
     */
    public static long hilbert3d(int x, int y, int z, int bits) {
        int[] c = {x, y, z};
        int m = 1 << (bits - 1);
        // inverse undo
        for (int q = m; q > 1; q >>>= 1) {
            int p = q - 1;
            for (int i = 0; i < 3; i++) {
                if ((c[i] & q) != 0) {
                    c[0] ^= p;
                } else {
                    int t = (c[0] ^ c[i]) & p;
                    c[0] ^= t;
                    c[i] ^= t;
                }
            }
        }
        // Gray encode
        for (int i = 1; i < 3; i++) {
            c[i] ^= c[i - 1];
        }
        int t = 0;
        for (int q = m; q > 1; q >>>= 1) {
            if ((c[2] & q) != 0) {
                t ^= q - 1;
            }
        }
        for (int i = 0; i < 3; i++) {
            c[i] ^= t;
        }
        // interleave the transposed form, most significant bit first: bit b of c[0], c[1], c[2]
        long d = 0;
        for (int b = bits - 1; b >= 0; b--) {
            for (int i = 0; i < 3; i++) {
                d = (d << 1) | ((c[i] >>> b) & 1);
            }
        }
        return d;
    }

    /**
     * Inverse of {@link #hilbert3d}: writes {@code x, y, z} to {@code out[0..2]}. Allocates a three-element
     * {@code int} array per call.
     *
     * @param d the Hilbert index, in {@code [0, 8^bits)}
     * @param bits the curve order, in {@code [1, 21]}
     * @param out receives the cell coordinates; at least 3 elements
     */
    public static void hilbert3dToXyz(long d, int bits, int[] out) {
        int[] c = new int[3];
        for (int b = bits - 1, k = 3 * bits - 1; b >= 0; b--) {
            for (int i = 0; i < 3; i++, k--) {
                c[i] |= (int) ((d >>> k) & 1) << b;
            }
        }
        int n = 2 << (bits - 1);
        // Gray decode by H ^ (H/2)
        int t = c[2] >>> 1;
        for (int i = 2; i > 0; i--) {
            c[i] ^= c[i - 1];
        }
        c[0] ^= t;
        // undo excess work
        for (int q = 2; q != n; q <<= 1) {
            int p = q - 1;
            for (int i = 2; i >= 0; i--) {
                if ((c[i] & q) != 0) {
                    c[0] ^= p;
                } else {
                    int tt = (c[0] ^ c[i]) & p;
                    c[0] ^= tt;
                    c[i] ^= tt;
                }
            }
        }
        out[0] = c[0];
        out[1] = c[1];
        out[2] = c[2];
    }
}
