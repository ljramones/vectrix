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
package org.vectrix.sh;

import org.vectrix.core.Vector3dc;
import org.vectrix.core.Vector3fc;

/**
 * Real SH basis for L0..L2 under a right-handed Y-up convention.
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
        // Bands ordered as [L00, L1(y,z,x), L2(xy,yz,3y^2-1,xz,x^2-z^2)] for Y-up.
        dest9[0] = C0;
        dest9[1] = C1 * y;
        dest9[2] = C1 * z;
        dest9[3] = C1 * x;
        dest9[4] = C2 * x * y;
        dest9[5] = C2 * y * z;
        dest9[6] = C3 * (3.0f * y * y - 1.0f);
        dest9[7] = C2 * x * z;
        dest9[8] = C4 * (x * x - z * z);
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
        dest9[6] = D3 * (3.0 * y * y - 1.0);
        dest9[7] = D2 * x * z;
        dest9[8] = D4 * (x * x - z * z);
        return dest9;
    }

    public static float[] evaluateL3(Vector3fc dir, float[] dest16) {
        return evaluateL3(dir.x(), dir.y(), dir.z(), dest16);
    }

    public static float[] evaluateL3(float x, float y, float z, float[] dest16) {
        if (dest16.length < 16) {
            throw new IllegalArgumentException("dest16");
        }
        evaluateL2(x, y, z, dest16);
        // L3 terms in Y-up convention (y is up-axis).
        dest16[9] = C5 * z * (3.0f * x * x - z * z);
        dest16[10] = C6 * x * y * z;
        dest16[11] = C7 * z * (5.0f * y * y - 1.0f);
        dest16[12] = C8 * y * (5.0f * y * y - 3.0f);
        dest16[13] = C7 * x * (5.0f * y * y - 1.0f);
        dest16[14] = C9 * y * (x * x - z * z);
        dest16[15] = C5 * x * (x * x - 3.0f * z * z);
        return dest16;
    }

    public static double[] evaluateL3(Vector3dc dir, double[] dest16) {
        return evaluateL3(dir.x(), dir.y(), dir.z(), dest16);
    }

    public static double[] evaluateL3(double x, double y, double z, double[] dest16) {
        if (dest16.length < 16) {
            throw new IllegalArgumentException("dest16");
        }
        evaluateL2(x, y, z, dest16);
        dest16[9] = D5 * z * (3.0 * x * x - z * z);
        dest16[10] = D6 * x * y * z;
        dest16[11] = D7 * z * (5.0 * y * y - 1.0);
        dest16[12] = D8 * y * (5.0 * y * y - 3.0);
        dest16[13] = D7 * x * (5.0 * y * y - 1.0);
        dest16[14] = D9 * y * (x * x - z * z);
        dest16[15] = D5 * x * (x * x - 3.0 * z * z);
        return dest16;
    }
}
