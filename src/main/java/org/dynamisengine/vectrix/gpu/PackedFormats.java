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

import org.dynamisengine.vectrix.color.ColorMathd;
import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;
import org.dynamisengine.vectrix.core.Vector4f;

/**
 * Packed GPU formats, bit-exact to their Vulkan definitions:
 * <ul>
 * <li>{@code A2B10G10R10_UNORM/SNORM_PACK32} (10_10_10_2);</li>
 * <li>{@code E5B9G9R9_UFLOAT_PACK32} (RGB9E5 shared exponent);</li>
 * <li>{@code B10G11R11_UFLOAT_PACK32} (R11G11B10F);</li>
 * <li>sRGB 8-bit encoding;</li>
 * <li>the QTangent tangent-frame encoding.</li>
 * </ul>
 * All formats are little-endian words, with red in the low bits. The codecs are allocation-free, except the
 * QTangent encoder, which is meant for asset packing.
 *
 * @since 2.4.0
 */
public final class PackedFormats {
    private PackedFormats() {
    }

    private static final float[] SRGB8_DECODE = new float[256];

    static {
        for (int i = 0; i < 256; i++) {
            SRGB8_DECODE[i] = (float) ColorMathd.srgbToLinear(i / 255.0);
        }
    }

    private static double clamp01(double v) {
        return v > 0 ? (v < 1 ? v : 1) : 0; // NaN gives 0
    }

    private static double clamp11(double v) {
        return v > -1 ? (v < 1 ? v : 1) : (v <= -1 ? -1 : 0); // NaN gives 0
    }

    // ---- 10_10_10_2 ----

    /**
     * {@code A2B10G10R10_UNORM_PACK32}: red in bits 0-9, green 10-19, blue 20-29, alpha 30-31. Encoding follows
     * {@link PackedNorm}: round to nearest, ties toward +∞, NaN as 0.
     *
     * @param r red, clamped to {@code [0, 1]}
     * @param g green, clamped to {@code [0, 1]}
     * @param b blue, clamped to {@code [0, 1]}
     * @param a alpha, clamped to {@code [0, 1]}
     * @return the packed word
     */
    public static int packUnorm1010102(float r, float g, float b, float a) {
        return (int) Math.round(clamp01(r) * 1023) | (int) Math.round(clamp01(g) * 1023) << 10
                | (int) Math.round(clamp01(b) * 1023) << 20 | (int) Math.round(clamp01(a) * 3) << 30;
    }

    /**
     * Decodes {@link #packUnorm1010102} exactly ({@code c / (2^b − 1)}).
     *
     * @param packed the packed word
     * @param dest receives red, green, blue and alpha in {@code x, y, z, w}
     * @return {@code dest}
     */
    public static Vector4f unpackUnorm1010102(int packed, Vector4f dest) {
        return dest.set((packed & 0x3FF) / 1023f, (packed >>> 10 & 0x3FF) / 1023f, (packed >>> 20 & 0x3FF) / 1023f,
                (packed >>> 30) / 3f);
    }

    /**
     * {@code A2B10G10R10_SNORM_PACK32}: two's-complement fields in the same positions as the unorm form. Red, green
     * and blue are scaled by 511; the 2-bit alpha is rounded to -1, 0 or 1. Rounding is to nearest, ties toward
     * +∞, and NaN encodes as 0.
     *
     * @param r red, clamped to {@code [-1, 1]}
     * @param g green, clamped to {@code [-1, 1]}
     * @param b blue, clamped to {@code [-1, 1]}
     * @param a alpha, clamped to {@code [-1, 1]}
     * @return the packed word
     */
    public static int packSnorm1010102(float r, float g, float b, float a) {
        return ((int) Math.round(clamp11(r) * 511) & 0x3FF) | ((int) Math.round(clamp11(g) * 511) & 0x3FF) << 10
                | ((int) Math.round(clamp11(b) * 511) & 0x3FF) << 20 | ((int) Math.round(clamp11(a)) & 0x3) << 30;
    }

    /**
     * Decodes {@link #packSnorm1010102}: {@code max(c / (2^(b−1) − 1), −1)}.
     *
     * @param packed the packed word
     * @param dest receives red, green, blue and alpha in {@code x, y, z, w}, each in {@code [-1, 1]}
     * @return {@code dest}
     */
    public static Vector4f unpackSnorm1010102(int packed, Vector4f dest) {
        int r = packed << 22 >> 22, g = packed << 12 >> 22, b = packed << 2 >> 22, a = packed >> 30;
        return dest.set(Math.max(r / 511f, -1f), Math.max(g / 511f, -1f), Math.max(b / 511f, -1f), Math.max(a, -1));
    }

    // ---- RGB9E5 ----

    /**
     * {@code E5B9G9R9_UFLOAT_PACK32}: three 9-bit mantissas sharing a 5-bit exponent. This is the exact algorithm
     * of the Vulkan specification ("Shared exponent"), the same as {@code EXT_texture_shared_exponent}. Components
     * are clamped to {@code [0, 65408]}, with NaN as 0, and rounded to nearest, ties up. Red is in bits 0-8, green
     * 9-17, blue 18-26 and the exponent 27-31.
     *
     * @param r red
     * @param g green
     * @param b blue
     * @return the packed word
     */
    public static int packRgb9e5(float r, float g, float b) {
        final double max = 65408.0; // (2^9 - 1) / 2^9 * 2^(31 - 15)
        double rc = r > 0 ? Math.min(r, max) : 0, gc = g > 0 ? Math.min(g, max) : 0, bc = b > 0 ? Math.min(b, max) : 0;
        double maxc = Math.max(rc, Math.max(gc, bc));
        int expP = Math.max(-16, maxc > 0 ? Math.getExponent(maxc) : -16) + 1 + 15;
        int maxs = (int) Math.floor(maxc / Math.scalb(1.0, expP - 15 - 9) + 0.5);
        int exp = maxs == 512 ? expP + 1 : expP;
        double scale = Math.scalb(1.0, -(exp - 15 - 9));
        int rs = (int) Math.floor(rc * scale + 0.5), gs = (int) Math.floor(gc * scale + 0.5);
        int bs = (int) Math.floor(bc * scale + 0.5);
        return rs | gs << 9 | bs << 18 | exp << 27;
    }

    /**
     * Decodes {@link #packRgb9e5} exactly: {@code mantissa × 2^(exponent − 24)}.
     *
     * @param packed the packed word
     * @param dest receives red, green and blue
     * @return {@code dest}
     */
    public static Vector3f unpackRgb9e5(int packed, Vector3f dest) {
        double scale = Math.scalb(1.0, (packed >>> 27) - 15 - 9);
        return dest.set((float) ((packed & 0x1FF) * scale), (float) ((packed >>> 9 & 0x1FF) * scale),
                (float) ((packed >>> 18 & 0x1FF) * scale));
    }

    // ---- R11G11B10F ----

    /**
     * {@code B10G11R11_UFLOAT_PACK32}: red and green as unsigned 11-bit floats (5-bit exponent, 6-bit mantissa),
     * blue as an unsigned 10-bit float (5-bit mantissa); red is in the low bits. Values are rounded to nearest-even.
     * Negative values and {@code −0} become 0, and NaN stays NaN. Values beyond the largest finite value round to
     * {@code +∞}, as IEEE rounding does (65024 is the largest finite 11-bit value, 64512 the largest 10-bit one).
     *
     * @param r red, in bits 0-10
     * @param g green, in bits 11-21
     * @param b blue, in bits 22-31
     * @return the packed word
     */
    public static int packR11G11B10F(float r, float g, float b) {
        return packUfloat(r, 6) | packUfloat(g, 6) << 11 | packUfloat(b, 5) << 22;
    }

    /**
     * Decodes {@link #packR11G11B10F} exactly; infinity and NaN codes decode to {@code +∞} and NaN.
     *
     * @param packed the packed word
     * @param dest receives red, green and blue
     * @return {@code dest}
     */
    public static Vector3f unpackR11G11B10F(int packed, Vector3f dest) {
        return dest.set(unpackUfloat(packed & 0x7FF, 6), unpackUfloat(packed >>> 11 & 0x7FF, 6),
                unpackUfloat(packed >>> 22 & 0x3FF, 5));
    }

    /** Unsigned float with a 5-bit exponent (bias 15) and {@code m} mantissa bits, round to nearest-even. */
    static int packUfloat(float f, int m) {
        if (f != f) {
            return 31 << m | 1 << (m - 1); // quiet NaN
        }
        if (!(f > 0)) {
            return 0;
        }
        if (f == Float.POSITIVE_INFINITY) {
            return 31 << m;
        }
        int e = Math.max(Math.getExponent(f), -14);
        double scaled = Math.scalb((double) f, m - e); // exact: a power-of-two scaling of a float
        long q = (long) Math.rint(scaled); // ties to even
        if (q >= 2L << m) { // rounded up to the next binade
            e++;
            q >>= 1;
        }
        if (e > 15) {
            return 31 << m; // +inf
        }
        if (q < 1L << m) {
            return (int) q; // subnormal (e == -14)
        }
        return (e + 15) << m | (int) (q - (1L << m));
    }

    static float unpackUfloat(int bits, int m) {
        int exp = bits >>> m, man = bits & ((1 << m) - 1);
        if (exp == 31) {
            return man == 0 ? Float.POSITIVE_INFINITY : Float.NaN;
        }
        if (exp == 0) {
            return (float) Math.scalb((double) man, -14 - m);
        }
        return (float) Math.scalb((double) ((1 << m) | man), exp - 15 - m);
    }

    // ---- sRGB 8-bit ----

    /**
     * The 8-bit sRGB code of a linear value: {@code round(255 · encode(clamp(linear, 0, 1)))}, using the exact
     * piecewise transfer function in double. This is how a GPU writes an {@code _SRGB} format. NaN encodes as 0.
     * A Reinhard-style approximation such as {@code pow(x, 1/2.2)} does not round-trip through an {@code _SRGB}
     * image.
     *
     * @param linear the linear value, clamped to {@code [0, 1]}
     * @return the sRGB code in {@code [0, 255]}
     */
    public static int encodeSrgb8(float linear) {
        return (int) Math.rint(255 * ColorMathd.linearToSrgb(clamp01(linear)));
    }

    /**
     * The linear value of an 8-bit sRGB code, from a table built with the exact transfer function.
     *
     * @param code the sRGB code; only the low 8 bits are read
     * @return the linear value in {@code [0, 1]}
     */
    public static float decodeSrgb8(int code) {
        return SRGB8_DECODE[code & 0xFF];
    }

    // ---- QTangent ----

    /**
     * The QTangent of a tangent frame: a unit quaternion whose rotation maps (x, y, z) to the frame's (tangent,
     * cross(normal, tangent), normal), with the bitangent sign in the sign of {@code w}.
     * <p>
     * The tangent is first made orthogonal to the normal (Gram-Schmidt). {@code |w|} is kept at least
     * {@code 1 / (2^(bits−1) − 1)}, so that the sign survives quantization to {@code bits}-bit snorm. Writes to
     * {@code dest}, computed in double. It allocates two temporary objects (asset-packing use).
     * <p>
     * Neither vector needs to be unit length. A zero normal, or a tangent parallel to the normal, gives NaN.
     *
     * @param normal the frame's normal
     * @param tangent the frame's tangent
     * @param bitangentSign
     *            the glTF {@code tangent.w}: the bitangent is {@code bitangentSign · cross(normal, tangent)}; a
     *            negative value negates the result, anything else (including 0) keeps {@code w} positive
     * @param bits the snorm bit count the result will be quantized to, from 2 to 31
     * @param dest receives the QTangent
     * @return {@code dest}
     */
    public static Quaternionf qTangent(Vector3fc normal, Vector3fc tangent, float bitangentSign, int bits, Quaternionf dest) {
        double nx = normal.x(), ny = normal.y(), nz = normal.z();
        double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx /= nl;
        ny /= nl;
        nz /= nl;
        double tx = tangent.x(), ty = tangent.y(), tz = tangent.z();
        double d = tx * nx + ty * ny + tz * nz;
        tx -= d * nx;
        ty -= d * ny;
        tz -= d * nz;
        double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
        tx /= tl;
        ty /= tl;
        tz /= tl;
        double bx = ny * tz - nz * ty, by = nz * tx - nx * tz, bz = nx * ty - ny * tx;
        Quaterniond q = new Quaterniond().setFromNormalized(new Matrix3d(tx, ty, tz, bx, by, bz, nx, ny, nz));
        double x = q.x, y = q.y, z = q.z, w = q.w;
        if (w < 0) {
            x = -x;
            y = -y;
            z = -z;
            w = -w;
        }
        double bias = 1.0 / ((1 << (bits - 1)) - 1);
        if (w < bias) {
            double s = Math.sqrt((1 - bias * bias) / (x * x + y * y + z * z));
            x *= s;
            y *= s;
            z *= s;
            w = bias;
        }
        if (bitangentSign < 0) {
            x = -x;
            y = -y;
            z = -z;
            w = -w;
        }
        return dest.set((float) x, (float) y, (float) z, (float) w);
    }

    /**
     * {@link #qTangent} with 16 bits, packed as four snorm16 values (x in the low 16 bits, then y, z, w), the layout
     * of {@code R16G16B16A16_SNORM}. Allocates (asset-packing use).
     *
     * @param normal the frame's normal
     * @param tangent the frame's tangent
     * @param bitangentSign the glTF {@code tangent.w}; see {@link #qTangent}
     * @return the packed QTangent
     */
    public static long packQTangent(Vector3fc normal, Vector3fc tangent, float bitangentSign) {
        Quaternionf q = qTangent(normal, tangent, bitangentSign, 16, new Quaternionf());
        return (PackedNorm.packSnorm16(q.x) & 0xFFFFL) | (PackedNorm.packSnorm16(q.y) & 0xFFFFL) << 16
                | (PackedNorm.packSnorm16(q.z) & 0xFFFFL) << 32 | (PackedNorm.packSnorm16(q.w) & 0xFFFFL) << 48;
    }

    /**
     * Decodes {@link #packQTangent} into a unit {@code normal} and {@code tangent}. The decoded quaternion is
     * renormalized first. Does not allocate.
     *
     * @param packed the packed QTangent
     * @param normal receives the normal; must not be the same object as {@code tangent}
     * @param tangent receives the tangent, orthogonal to {@code normal}
     *
     * @return the bitangent sign ({@code 1} or {@code −1}): the bitangent is {@code sign · cross(normal, tangent)}
     */
    public static float unpackQTangent(long packed, Vector3f normal, Vector3f tangent) {
        double x = PackedNorm.unpackSnorm16((int) packed), y = PackedNorm.unpackSnorm16((int) (packed >>> 16));
        double z = PackedNorm.unpackSnorm16((int) (packed >>> 32)), w = PackedNorm.unpackSnorm16((int) (packed >>> 48));
        float sign = w < 0 ? -1f : 1f;
        double l = Math.sqrt(x * x + y * y + z * z + w * w);
        x /= l;
        y /= l;
        z /= l;
        w /= l;
        // columns of the rotation matrix: tangent = R e_x, normal = R e_z
        tangent.set((float) (1 - 2 * (y * y + z * z)), (float) (2 * (x * y + w * z)), (float) (2 * (x * z - w * y)));
        normal.set((float) (2 * (x * z + w * y)), (float) (2 * (y * z - w * x)), (float) (1 - 2 * (x * x + y * y)));
        return sign;
    }
}
