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
package org.dynamisengine.vectrix.sh;

import org.dynamisengine.vectrix.core.Vector3dc;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Real SH basis, bands 0..3 (16 functions), as used throughout {@code org.dynamisengine.vectrix.sh}.
 * <p>
 * This is the standard real basis used for irradiance in graphics (Ramamoorthi and Hanrahan 2001; Sloan, "Stupid
 * Spherical Harmonics Tricks", without the Condon–Shortley phase): function {@code Y(l, m)} is at index
 * {@code l * (l + 1) + m}, and the formulas are evaluated on the direction {@code (x, y, z)} exactly as passed, with
 * no axis remapping. The zonal functions ({@code m = 0}) are symmetric about the z axis of the caller's frame.
 * Standard SH rotation matrices and shader code using this convention can consume the coefficients unchanged.
 * <table>
 * <caption>Basis index, band, order and function</caption>
 * <tr><th>i</th><th>l</th><th>m</th><th>function</th></tr>
 * <tr><td>0</td><td>0</td><td>0</td><td>0.2820947918</td></tr>
 * <tr><td>1</td><td>1</td><td>-1</td><td>0.4886025119 y</td></tr>
 * <tr><td>2</td><td>1</td><td>0</td><td>0.4886025119 z</td></tr>
 * <tr><td>3</td><td>1</td><td>1</td><td>0.4886025119 x</td></tr>
 * <tr><td>4</td><td>2</td><td>-2</td><td>1.0925484306 xy</td></tr>
 * <tr><td>5</td><td>2</td><td>-1</td><td>1.0925484306 yz</td></tr>
 * <tr><td>6</td><td>2</td><td>0</td><td>0.3153915653 (3z² − 1)</td></tr>
 * <tr><td>7</td><td>2</td><td>1</td><td>1.0925484306 xz</td></tr>
 * <tr><td>8</td><td>2</td><td>2</td><td>0.5462742153 (x² − y²)</td></tr>
 * <tr><td>9</td><td>3</td><td>-3</td><td>0.5900435899 y (3x² − y²)</td></tr>
 * <tr><td>10</td><td>3</td><td>-2</td><td>2.8906114426 xyz</td></tr>
 * <tr><td>11</td><td>3</td><td>-1</td><td>0.4570457995 y (5z² − 1)</td></tr>
 * <tr><td>12</td><td>3</td><td>0</td><td>0.3731763325 z (5z² − 3)</td></tr>
 * <tr><td>13</td><td>3</td><td>1</td><td>0.4570457995 x (5z² − 1)</td></tr>
 * <tr><td>14</td><td>3</td><td>2</td><td>1.4453057213 z (x² − y²)</td></tr>
 * <tr><td>15</td><td>3</td><td>3</td><td>0.5900435899 x (x² − 3y²)</td></tr>
 * </table>
  * @since 1.0.0
  */
public final class ShBasis {
    static final float C0 = 0.2820947918f;
    static final float C1 = 0.4886025119f;
    static final float C2 = 1.0925484306f;
    static final float C3 = 0.3153915653f;
    static final float C4 = 0.5462742153f;
    static final float C5 = 0.5900435899f;
    static final float C6 = 2.8906114426f;
    static final float C7 = 0.4570457995f;
    static final float C8 = 0.3731763325f;
    static final float C9 = 1.4453057213f;

    // full double precision: sqrt((2l+1)/(4π) (l-m)!/(l+m)!) times the Legendre factor
    static final double D0 = 0.28209479177387814;
    static final double D1 = 0.4886025119029199;
    static final double D2 = 1.0925484305920792;
    static final double D3 = 0.31539156525252005;
    static final double D4 = 0.5462742152960396;
    static final double D5 = 0.5900435899266435;
    static final double D6 = 2.890611442640554;
    static final double D7 = 0.4570457994644658;
    static final double D8 = 0.3731763325901154;
    static final double D9 = 1.445305721320277;

    private ShBasis() {
    }

    public static float[] evaluateL2(Vector3fc dir, float[] dest9) {
        return evaluateL2(dir.x(), dir.y(), dir.z(), dest9);
    }

    public static float[] evaluateL2(float x, float y, float z, float[] dest9) {
        if (dest9.length < 9) {
            throw new IllegalArgumentException("dest9");
        }
        dest9[0] = C0;
        dest9[1] = C1 * y;
        dest9[2] = C1 * z;
        dest9[3] = C1 * x;
        dest9[4] = C2 * x * y;
        dest9[5] = C2 * y * z;
        dest9[6] = C3 * (3.0f * z * z - 1.0f);
        dest9[7] = C2 * x * z;
        dest9[8] = C4 * (x * x - y * y);
        return dest9;
    }

    public static double[] evaluateL2(Vector3dc dir, double[] dest9) {
        return evaluateL2(dir.x(), dir.y(), dir.z(), dest9);
    }

    public static double[] evaluateL2(double x, double y, double z, double[] dest9) {
        if (dest9.length < 9) {
            throw new IllegalArgumentException("dest9");
        }
        dest9[0] = D0;
        dest9[1] = D1 * y;
        dest9[2] = D1 * z;
        dest9[3] = D1 * x;
        dest9[4] = D2 * x * y;
        dest9[5] = D2 * y * z;
        dest9[6] = D3 * (3.0 * z * z - 1.0);
        dest9[7] = D2 * x * z;
        dest9[8] = D4 * (x * x - y * y);
        return dest9;
    }

    public static float[] evaluateL3(Vector3fc dir, float[] dest16) {
        return evaluateL3(dir.x(), dir.y(), dir.z(), dest16);
    }

    public static float[] evaluateL3(float x, float y, float z, float[] dest16) {
        if (dest16.length < 16) {
            throw new IllegalArgumentException("dest16");
        }
        dest16[0] = C0;
        dest16[1] = C1 * y;
        dest16[2] = C1 * z;
        dest16[3] = C1 * x;
        dest16[4] = C2 * x * y;
        dest16[5] = C2 * y * z;
        dest16[6] = C3 * (3.0f * z * z - 1.0f);
        dest16[7] = C2 * x * z;
        dest16[8] = C4 * (x * x - y * y);
        dest16[9] = C5 * y * (3.0f * x * x - y * y);
        dest16[10] = C6 * x * y * z;
        dest16[11] = C7 * y * (5.0f * z * z - 1.0f);
        dest16[12] = C8 * z * (5.0f * z * z - 3.0f);
        dest16[13] = C7 * x * (5.0f * z * z - 1.0f);
        dest16[14] = C9 * z * (x * x - y * y);
        dest16[15] = C5 * x * (x * x - 3.0f * y * y);
        return dest16;
    }

    public static double[] evaluateL3(Vector3dc dir, double[] dest16) {
        return evaluateL3(dir.x(), dir.y(), dir.z(), dest16);
    }

    public static double[] evaluateL3(double x, double y, double z, double[] dest16) {
        if (dest16.length < 16) {
            throw new IllegalArgumentException("dest16");
        }
        dest16[0] = D0;
        dest16[1] = D1 * y;
        dest16[2] = D1 * z;
        dest16[3] = D1 * x;
        dest16[4] = D2 * x * y;
        dest16[5] = D2 * y * z;
        dest16[6] = D3 * (3.0 * z * z - 1.0);
        dest16[7] = D2 * x * z;
        dest16[8] = D4 * (x * x - y * y);
        dest16[9] = D5 * y * (3.0 * x * x - y * y);
        dest16[10] = D6 * x * y * z;
        dest16[11] = D7 * y * (5.0 * z * z - 1.0);
        dest16[12] = D8 * z * (5.0 * z * z - 3.0);
        dest16[13] = D7 * x * (5.0 * z * z - 1.0);
        dest16[14] = D9 * z * (x * x - y * y);
        dest16[15] = D5 * x * (x * x - 3.0 * y * y);
        return dest16;
    }

    /** Adds the weighted sample {@code (rw, gw, bw)} times each of the first 9 basis functions to {@code c}. */
    static void accumulate9(float x, float y, float z, float rw, float gw, float bw, float[] c) {
        float b0 = C0;
        float b1 = C1 * y;
        float b2 = C1 * z;
        float b3 = C1 * x;
        float b4 = C2 * x * y;
        float b5 = C2 * y * z;
        float b6 = C3 * (3.0f * z * z - 1.0f);
        float b7 = C2 * x * z;
        float b8 = C4 * (x * x - y * y);
        c[0] += rw * b0;
        c[1] += gw * b0;
        c[2] += bw * b0;
        c[3] += rw * b1;
        c[4] += gw * b1;
        c[5] += bw * b1;
        c[6] += rw * b2;
        c[7] += gw * b2;
        c[8] += bw * b2;
        c[9] += rw * b3;
        c[10] += gw * b3;
        c[11] += bw * b3;
        c[12] += rw * b4;
        c[13] += gw * b4;
        c[14] += bw * b4;
        c[15] += rw * b5;
        c[16] += gw * b5;
        c[17] += bw * b5;
        c[18] += rw * b6;
        c[19] += gw * b6;
        c[20] += bw * b6;
        c[21] += rw * b7;
        c[22] += gw * b7;
        c[23] += bw * b7;
        c[24] += rw * b8;
        c[25] += gw * b8;
        c[26] += bw * b8;
    }

    /** {@code dest = sum_i c_i * Y_i(x, y, z)} over the first 9 basis functions, per RGB channel. */
    static Vector3f dot9(float[] c, float x, float y, float z, Vector3f dest) {
        float b0 = C0;
        float b1 = C1 * y;
        float b2 = C1 * z;
        float b3 = C1 * x;
        float b4 = C2 * x * y;
        float b5 = C2 * y * z;
        float b6 = C3 * (3.0f * z * z - 1.0f);
        float b7 = C2 * x * z;
        float b8 = C4 * (x * x - y * y);
        float r = 0.0f;
        float g = 0.0f;
        float bl = 0.0f;
        r += c[0] * b0;
        g += c[1] * b0;
        bl += c[2] * b0;
        r += c[3] * b1;
        g += c[4] * b1;
        bl += c[5] * b1;
        r += c[6] * b2;
        g += c[7] * b2;
        bl += c[8] * b2;
        r += c[9] * b3;
        g += c[10] * b3;
        bl += c[11] * b3;
        r += c[12] * b4;
        g += c[13] * b4;
        bl += c[14] * b4;
        r += c[15] * b5;
        g += c[16] * b5;
        bl += c[17] * b5;
        r += c[18] * b6;
        g += c[19] * b6;
        bl += c[20] * b6;
        r += c[21] * b7;
        g += c[22] * b7;
        bl += c[23] * b7;
        r += c[24] * b8;
        g += c[25] * b8;
        bl += c[26] * b8;
        return dest.set(r, g, bl);
    }

    /** Adds the weighted sample {@code (rw, gw, bw)} times each of the first 16 basis functions to {@code c}. */
    static void accumulate16(float x, float y, float z, float rw, float gw, float bw, float[] c) {
        float b0 = C0;
        float b1 = C1 * y;
        float b2 = C1 * z;
        float b3 = C1 * x;
        float b4 = C2 * x * y;
        float b5 = C2 * y * z;
        float b6 = C3 * (3.0f * z * z - 1.0f);
        float b7 = C2 * x * z;
        float b8 = C4 * (x * x - y * y);
        float b9 = C5 * y * (3.0f * x * x - y * y);
        float b10 = C6 * x * y * z;
        float b11 = C7 * y * (5.0f * z * z - 1.0f);
        float b12 = C8 * z * (5.0f * z * z - 3.0f);
        float b13 = C7 * x * (5.0f * z * z - 1.0f);
        float b14 = C9 * z * (x * x - y * y);
        float b15 = C5 * x * (x * x - 3.0f * y * y);
        c[0] += rw * b0;
        c[1] += gw * b0;
        c[2] += bw * b0;
        c[3] += rw * b1;
        c[4] += gw * b1;
        c[5] += bw * b1;
        c[6] += rw * b2;
        c[7] += gw * b2;
        c[8] += bw * b2;
        c[9] += rw * b3;
        c[10] += gw * b3;
        c[11] += bw * b3;
        c[12] += rw * b4;
        c[13] += gw * b4;
        c[14] += bw * b4;
        c[15] += rw * b5;
        c[16] += gw * b5;
        c[17] += bw * b5;
        c[18] += rw * b6;
        c[19] += gw * b6;
        c[20] += bw * b6;
        c[21] += rw * b7;
        c[22] += gw * b7;
        c[23] += bw * b7;
        c[24] += rw * b8;
        c[25] += gw * b8;
        c[26] += bw * b8;
        c[27] += rw * b9;
        c[28] += gw * b9;
        c[29] += bw * b9;
        c[30] += rw * b10;
        c[31] += gw * b10;
        c[32] += bw * b10;
        c[33] += rw * b11;
        c[34] += gw * b11;
        c[35] += bw * b11;
        c[36] += rw * b12;
        c[37] += gw * b12;
        c[38] += bw * b12;
        c[39] += rw * b13;
        c[40] += gw * b13;
        c[41] += bw * b13;
        c[42] += rw * b14;
        c[43] += gw * b14;
        c[44] += bw * b14;
        c[45] += rw * b15;
        c[46] += gw * b15;
        c[47] += bw * b15;
    }

    /** {@code dest = sum_i c_i * Y_i(x, y, z)} over the first 16 basis functions, per RGB channel. */
    static Vector3f dot16(float[] c, float x, float y, float z, Vector3f dest) {
        float b0 = C0;
        float b1 = C1 * y;
        float b2 = C1 * z;
        float b3 = C1 * x;
        float b4 = C2 * x * y;
        float b5 = C2 * y * z;
        float b6 = C3 * (3.0f * z * z - 1.0f);
        float b7 = C2 * x * z;
        float b8 = C4 * (x * x - y * y);
        float b9 = C5 * y * (3.0f * x * x - y * y);
        float b10 = C6 * x * y * z;
        float b11 = C7 * y * (5.0f * z * z - 1.0f);
        float b12 = C8 * z * (5.0f * z * z - 3.0f);
        float b13 = C7 * x * (5.0f * z * z - 1.0f);
        float b14 = C9 * z * (x * x - y * y);
        float b15 = C5 * x * (x * x - 3.0f * y * y);
        float r = 0.0f;
        float g = 0.0f;
        float bl = 0.0f;
        r += c[0] * b0;
        g += c[1] * b0;
        bl += c[2] * b0;
        r += c[3] * b1;
        g += c[4] * b1;
        bl += c[5] * b1;
        r += c[6] * b2;
        g += c[7] * b2;
        bl += c[8] * b2;
        r += c[9] * b3;
        g += c[10] * b3;
        bl += c[11] * b3;
        r += c[12] * b4;
        g += c[13] * b4;
        bl += c[14] * b4;
        r += c[15] * b5;
        g += c[16] * b5;
        bl += c[17] * b5;
        r += c[18] * b6;
        g += c[19] * b6;
        bl += c[20] * b6;
        r += c[21] * b7;
        g += c[22] * b7;
        bl += c[23] * b7;
        r += c[24] * b8;
        g += c[25] * b8;
        bl += c[26] * b8;
        r += c[27] * b9;
        g += c[28] * b9;
        bl += c[29] * b9;
        r += c[30] * b10;
        g += c[31] * b10;
        bl += c[32] * b10;
        r += c[33] * b11;
        g += c[34] * b11;
        bl += c[35] * b11;
        r += c[36] * b12;
        g += c[37] * b12;
        bl += c[38] * b12;
        r += c[39] * b13;
        g += c[40] * b13;
        bl += c[41] * b13;
        r += c[42] * b14;
        g += c[43] * b14;
        bl += c[44] * b14;
        r += c[45] * b15;
        g += c[46] * b15;
        bl += c[47] * b15;
        return dest.set(r, g, bl);
    }

    /** Adds the weighted sample {@code (rw, gw, bw)} times each of the first 9 basis functions to {@code c}. */
    static void accumulate9(double x, double y, double z, double rw, double gw, double bw, double[] c) {
        double b0 = D0;
        double b1 = D1 * y;
        double b2 = D1 * z;
        double b3 = D1 * x;
        double b4 = D2 * x * y;
        double b5 = D2 * y * z;
        double b6 = D3 * (3.0 * z * z - 1.0);
        double b7 = D2 * x * z;
        double b8 = D4 * (x * x - y * y);
        c[0] += rw * b0;
        c[1] += gw * b0;
        c[2] += bw * b0;
        c[3] += rw * b1;
        c[4] += gw * b1;
        c[5] += bw * b1;
        c[6] += rw * b2;
        c[7] += gw * b2;
        c[8] += bw * b2;
        c[9] += rw * b3;
        c[10] += gw * b3;
        c[11] += bw * b3;
        c[12] += rw * b4;
        c[13] += gw * b4;
        c[14] += bw * b4;
        c[15] += rw * b5;
        c[16] += gw * b5;
        c[17] += bw * b5;
        c[18] += rw * b6;
        c[19] += gw * b6;
        c[20] += bw * b6;
        c[21] += rw * b7;
        c[22] += gw * b7;
        c[23] += bw * b7;
        c[24] += rw * b8;
        c[25] += gw * b8;
        c[26] += bw * b8;
    }

    /** {@code dest = sum_i c_i * Y_i(x, y, z)} over the first 9 basis functions, per RGB channel. */
    static Vector3d dot9(double[] c, double x, double y, double z, Vector3d dest) {
        double b0 = D0;
        double b1 = D1 * y;
        double b2 = D1 * z;
        double b3 = D1 * x;
        double b4 = D2 * x * y;
        double b5 = D2 * y * z;
        double b6 = D3 * (3.0 * z * z - 1.0);
        double b7 = D2 * x * z;
        double b8 = D4 * (x * x - y * y);
        double r = 0.0;
        double g = 0.0;
        double bl = 0.0;
        r += c[0] * b0;
        g += c[1] * b0;
        bl += c[2] * b0;
        r += c[3] * b1;
        g += c[4] * b1;
        bl += c[5] * b1;
        r += c[6] * b2;
        g += c[7] * b2;
        bl += c[8] * b2;
        r += c[9] * b3;
        g += c[10] * b3;
        bl += c[11] * b3;
        r += c[12] * b4;
        g += c[13] * b4;
        bl += c[14] * b4;
        r += c[15] * b5;
        g += c[16] * b5;
        bl += c[17] * b5;
        r += c[18] * b6;
        g += c[19] * b6;
        bl += c[20] * b6;
        r += c[21] * b7;
        g += c[22] * b7;
        bl += c[23] * b7;
        r += c[24] * b8;
        g += c[25] * b8;
        bl += c[26] * b8;
        return dest.set(r, g, bl);
    }

    /** Adds the weighted sample {@code (rw, gw, bw)} times each of the first 16 basis functions to {@code c}. */
    static void accumulate16(double x, double y, double z, double rw, double gw, double bw, double[] c) {
        double b0 = D0;
        double b1 = D1 * y;
        double b2 = D1 * z;
        double b3 = D1 * x;
        double b4 = D2 * x * y;
        double b5 = D2 * y * z;
        double b6 = D3 * (3.0 * z * z - 1.0);
        double b7 = D2 * x * z;
        double b8 = D4 * (x * x - y * y);
        double b9 = D5 * y * (3.0 * x * x - y * y);
        double b10 = D6 * x * y * z;
        double b11 = D7 * y * (5.0 * z * z - 1.0);
        double b12 = D8 * z * (5.0 * z * z - 3.0);
        double b13 = D7 * x * (5.0 * z * z - 1.0);
        double b14 = D9 * z * (x * x - y * y);
        double b15 = D5 * x * (x * x - 3.0 * y * y);
        c[0] += rw * b0;
        c[1] += gw * b0;
        c[2] += bw * b0;
        c[3] += rw * b1;
        c[4] += gw * b1;
        c[5] += bw * b1;
        c[6] += rw * b2;
        c[7] += gw * b2;
        c[8] += bw * b2;
        c[9] += rw * b3;
        c[10] += gw * b3;
        c[11] += bw * b3;
        c[12] += rw * b4;
        c[13] += gw * b4;
        c[14] += bw * b4;
        c[15] += rw * b5;
        c[16] += gw * b5;
        c[17] += bw * b5;
        c[18] += rw * b6;
        c[19] += gw * b6;
        c[20] += bw * b6;
        c[21] += rw * b7;
        c[22] += gw * b7;
        c[23] += bw * b7;
        c[24] += rw * b8;
        c[25] += gw * b8;
        c[26] += bw * b8;
        c[27] += rw * b9;
        c[28] += gw * b9;
        c[29] += bw * b9;
        c[30] += rw * b10;
        c[31] += gw * b10;
        c[32] += bw * b10;
        c[33] += rw * b11;
        c[34] += gw * b11;
        c[35] += bw * b11;
        c[36] += rw * b12;
        c[37] += gw * b12;
        c[38] += bw * b12;
        c[39] += rw * b13;
        c[40] += gw * b13;
        c[41] += bw * b13;
        c[42] += rw * b14;
        c[43] += gw * b14;
        c[44] += bw * b14;
        c[45] += rw * b15;
        c[46] += gw * b15;
        c[47] += bw * b15;
    }

    /** {@code dest = sum_i c_i * Y_i(x, y, z)} over the first 16 basis functions, per RGB channel. */
    static Vector3d dot16(double[] c, double x, double y, double z, Vector3d dest) {
        double b0 = D0;
        double b1 = D1 * y;
        double b2 = D1 * z;
        double b3 = D1 * x;
        double b4 = D2 * x * y;
        double b5 = D2 * y * z;
        double b6 = D3 * (3.0 * z * z - 1.0);
        double b7 = D2 * x * z;
        double b8 = D4 * (x * x - y * y);
        double b9 = D5 * y * (3.0 * x * x - y * y);
        double b10 = D6 * x * y * z;
        double b11 = D7 * y * (5.0 * z * z - 1.0);
        double b12 = D8 * z * (5.0 * z * z - 3.0);
        double b13 = D7 * x * (5.0 * z * z - 1.0);
        double b14 = D9 * z * (x * x - y * y);
        double b15 = D5 * x * (x * x - 3.0 * y * y);
        double r = 0.0;
        double g = 0.0;
        double bl = 0.0;
        r += c[0] * b0;
        g += c[1] * b0;
        bl += c[2] * b0;
        r += c[3] * b1;
        g += c[4] * b1;
        bl += c[5] * b1;
        r += c[6] * b2;
        g += c[7] * b2;
        bl += c[8] * b2;
        r += c[9] * b3;
        g += c[10] * b3;
        bl += c[11] * b3;
        r += c[12] * b4;
        g += c[13] * b4;
        bl += c[14] * b4;
        r += c[15] * b5;
        g += c[16] * b5;
        bl += c[17] * b5;
        r += c[18] * b6;
        g += c[19] * b6;
        bl += c[20] * b6;
        r += c[21] * b7;
        g += c[22] * b7;
        bl += c[23] * b7;
        r += c[24] * b8;
        g += c[25] * b8;
        bl += c[26] * b8;
        r += c[27] * b9;
        g += c[28] * b9;
        bl += c[29] * b9;
        r += c[30] * b10;
        g += c[31] * b10;
        bl += c[32] * b10;
        r += c[33] * b11;
        g += c[34] * b11;
        bl += c[35] * b11;
        r += c[36] * b12;
        g += c[37] * b12;
        bl += c[38] * b12;
        r += c[39] * b13;
        g += c[40] * b13;
        bl += c[41] * b13;
        r += c[42] * b14;
        g += c[43] * b14;
        bl += c[44] * b14;
        r += c[45] * b15;
        g += c[46] * b15;
        bl += c[47] * b15;
        return dest.set(r, g, bl);
    }
}
