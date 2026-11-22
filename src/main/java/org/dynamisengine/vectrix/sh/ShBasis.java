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
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Real SH basis, bands 0..3 (16 functions), as used throughout {@code org.dynamisengine.vectrix.sh}.
 * <p>
 * The ordering is <b>not</b> a single standard convention, so shaders and other tools must use this table
 * index for index (review finding, §7): band 1 keeps the Z-up ordering {@code (y, z, x)}, while the zonal terms of
 * bands 2 and 3 use y as the up axis. The functions are orthonormal and self-consistent within this library.
 * <table>
 * <caption>Basis index, polynomial and normalisation constant</caption>
 * <tr><th>i</th><th>function</th></tr>
 * <tr><td>0</td><td>0.2820947918</td></tr>
 * <tr><td>1</td><td>0.4886025119 y</td></tr>
 * <tr><td>2</td><td>0.4886025119 z</td></tr>
 * <tr><td>3</td><td>0.4886025119 x</td></tr>
 * <tr><td>4</td><td>1.0925484306 xy</td></tr>
 * <tr><td>5</td><td>1.0925484306 yz</td></tr>
 * <tr><td>6</td><td>0.3153915653 (3y² − 1)</td></tr>
 * <tr><td>7</td><td>1.0925484306 xz</td></tr>
 * <tr><td>8</td><td>0.5462742153 (x² − z²)</td></tr>
 * <tr><td>9</td><td>−0.5900435899 z (3x² − z²)</td></tr>
 * <tr><td>10</td><td>2.8906114426 xyz</td></tr>
 * <tr><td>11</td><td>−0.4570457995 z (5y² − 1)</td></tr>
 * <tr><td>12</td><td>0.3731763325 y (5y² − 3)</td></tr>
 * <tr><td>13</td><td>−0.4570457995 x (5y² − 1)</td></tr>
 * <tr><td>14</td><td>1.4453057213 y (x² − z²)</td></tr>
 * <tr><td>15</td><td>−0.5900435899 x (x² − 3z²)</td></tr>
 * </table>
  * @since 1.0.0
  */
public final class ShBasis {
    private static final float C0 = 0.2820947918f;
    private static final float C1 = 0.4886025119f;
    private static final float C2 = 1.0925484306f;
    private static final float C3 = 0.3153915653f;
    private static final float C4 = 0.5462742153f;
    private static final float C5 = -0.5900435899f;
    private static final float C6 = 2.8906114426f;
    private static final float C7 = -0.4570457995f;
    private static final float C8 = 0.3731763325f;
    private static final float C9 = 1.4453057213f;

    private static final double D0 = 0.2820947918;
    private static final double D1 = 0.4886025119;
    private static final double D2 = 1.0925484306;
    private static final double D3 = 0.3153915653;
    private static final double D4 = 0.5462742153;
    private static final double D5 = -0.5900435899;
    private static final double D6 = 2.8906114426;
    private static final double D7 = -0.4570457995;
    private static final double D8 = 0.3731763325;
    private static final double D9 = 1.4453057213;

    private ShBasis() {
    }

    public static float[] evaluateL2(Vector3fc dir, float[] dest9) {
        return evaluateL2(dir.x(), dir.y(), dir.z(), dest9);
    }

    public static float[] evaluateL2(float x, float y, float z, float[] dest9) {
        if (dest9.length < 9) {
            throw new IllegalArgumentException("dest9");
        }
        for (int i = 0; i < 9; i++) {
            dest9[i] = basis(i, x, y, z);
        }
        return dest9;
    }

    public static double[] evaluateL2(Vector3dc dir, double[] dest9) {
        return evaluateL2(dir.x(), dir.y(), dir.z(), dest9);
    }

    public static double[] evaluateL2(double x, double y, double z, double[] dest9) {
        if (dest9.length < 9) {
            throw new IllegalArgumentException("dest9");
        }
        for (int i = 0; i < 9; i++) {
            dest9[i] = basis(i, x, y, z);
        }
        return dest9;
    }

    public static float[] evaluateL3(Vector3fc dir, float[] dest16) {
        return evaluateL3(dir.x(), dir.y(), dir.z(), dest16);
    }

    public static float[] evaluateL3(float x, float y, float z, float[] dest16) {
        if (dest16.length < 16) {
            throw new IllegalArgumentException("dest16");
        }
        for (int i = 0; i < 16; i++) {
            dest16[i] = basis(i, x, y, z);
        }
        return dest16;
    }

    public static double[] evaluateL3(Vector3dc dir, double[] dest16) {
        return evaluateL3(dir.x(), dir.y(), dir.z(), dest16);
    }

    public static double[] evaluateL3(double x, double y, double z, double[] dest16) {
        if (dest16.length < 16) {
            throw new IllegalArgumentException("dest16");
        }
        for (int i = 0; i < 16; i++) {
            dest16[i] = basis(i, x, y, z);
        }
        return dest16;
    }

    /**
     * Basis function {@code i} (0..15, see the class documentation for the ordering). The single source of the
     * formulas: the array fillers and the allocation-free projection and evaluation loops in this package all use
     * it, and with a constant index the JIT folds the switch away.
     */
    static float basis(int i, float x, float y, float z) {
        switch (i) {
            case 0: return C0;
            case 1: return C1 * y;
            case 2: return C1 * z;
            case 3: return C1 * x;
            case 4: return C2 * x * y;
            case 5: return C2 * y * z;
            case 6: return C3 * (3.0f * y * y - 1.0f);
            case 7: return C2 * x * z;
            case 8: return C4 * (x * x - z * z);
            case 9: return C5 * z * (3.0f * x * x - z * z);
            case 10: return C6 * x * y * z;
            case 11: return C7 * z * (5.0f * y * y - 1.0f);
            case 12: return C8 * y * (5.0f * y * y - 3.0f);
            case 13: return C7 * x * (5.0f * y * y - 1.0f);
            case 14: return C9 * y * (x * x - z * z);
            case 15: return C5 * x * (x * x - 3.0f * z * z);
            default: throw new IllegalArgumentException("SH basis index " + i);
        }
    }

    /** Double-precision {@link #basis(int, float, float, float)}. */
    static double basis(int i, double x, double y, double z) {
        switch (i) {
            case 0: return D0;
            case 1: return D1 * y;
            case 2: return D1 * z;
            case 3: return D1 * x;
            case 4: return D2 * x * y;
            case 5: return D2 * y * z;
            case 6: return D3 * (3.0 * y * y - 1.0);
            case 7: return D2 * x * z;
            case 8: return D4 * (x * x - z * z);
            case 9: return D5 * z * (3.0 * x * x - z * z);
            case 10: return D6 * x * y * z;
            case 11: return D7 * z * (5.0 * y * y - 1.0);
            case 12: return D8 * y * (5.0 * y * y - 3.0);
            case 13: return D7 * x * (5.0 * y * y - 1.0);
            case 14: return D9 * y * (x * x - z * z);
            case 15: return D5 * x * (x * x - 3.0 * z * z);
            default: throw new IllegalArgumentException("SH basis index " + i);
        }
    }
}
