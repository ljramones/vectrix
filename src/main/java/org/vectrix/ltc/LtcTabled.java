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
package org.vectrix.ltc;

import org.vectrix.renderingmath.Interpolationd;

/**
 * LTC table sampling helpers (double-precision).
  * @since 1.0.0
  */
public final class LtcTabled {
    private LtcTabled() {
    }

    public static void sampleMat3(double[] table, int width, int height, double roughness, double ndotv, double[] dest9) {
        if (table == null || dest9 == null || dest9.length < 9 || width <= 1 || height <= 1 || table.length < width * height * 9) {
            throw new IllegalArgumentException("table/dest");
        }
        double x = clamp01(roughness) * (width - 1);
        double y = clamp01(ndotv) * (height - 1);
        int x0 = (int) java.lang.Math.floor(x);
        int y0 = (int) java.lang.Math.floor(y);
        int x1 = java.lang.Math.min(x0 + 1, width - 1);
        int y1 = java.lang.Math.min(y0 + 1, height - 1);
        double tx = x - x0;
        double ty = y - y0;
        int o00 = ((y0 * width + x0) * 9);
        int o10 = ((y0 * width + x1) * 9);
        int o01 = ((y1 * width + x0) * 9);
        int o11 = ((y1 * width + x1) * 9);
        for (int i = 0; i < 9; i++) {
            dest9[i] = Interpolationd.bilinear(table[o00 + i], table[o10 + i], table[o01 + i], table[o11 + i], tx, ty);
        }
    }

    public static void sampleVec4(double[] table, int width, int height, double roughness, double ndotv, double[] dest4) {
        if (table == null || dest4 == null || dest4.length < 4 || width <= 1 || height <= 1 || table.length < width * height * 4) {
            throw new IllegalArgumentException("table/dest");
        }
        double x = clamp01(roughness) * (width - 1);
        double y = clamp01(ndotv) * (height - 1);
        int x0 = (int) java.lang.Math.floor(x);
        int y0 = (int) java.lang.Math.floor(y);
        int x1 = java.lang.Math.min(x0 + 1, width - 1);
        int y1 = java.lang.Math.min(y0 + 1, height - 1);
        double tx = x - x0;
        double ty = y - y0;
        int o00 = ((y0 * width + x0) * 4);
        int o10 = ((y0 * width + x1) * 4);
        int o01 = ((y1 * width + x0) * 4);
        int o11 = ((y1 * width + x1) * 4);
        for (int i = 0; i < 4; i++) {
            dest4[i] = Interpolationd.bilinear(table[o00 + i], table[o10 + i], table[o01 + i], table[o11 + i], tx, ty);
        }
    }

    private static double clamp01(double v) {
        return java.lang.Math.max(0.0, java.lang.Math.min(1.0, v));
    }
}
