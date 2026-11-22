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
package org.dynamisengine.vectrix.linalg;

import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Matrix3dc;
import org.dynamisengine.vectrix.core.Matrix3f;
import org.dynamisengine.vectrix.core.Matrix3fc;
import org.dynamisengine.vectrix.core.Matrix4d;
import org.dynamisengine.vectrix.core.Matrix4dc;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Matrix4fc;

/** Conversions between the column-major matrix types ({@code mCR} = column C, row R) and row-major arrays. */
final class Conv {
    private Conv() {
    }

    static double[] rm(Matrix3dc m, double[] d) {
        d[0] = m.m00();
        d[1] = m.m10();
        d[2] = m.m20();
        d[3] = m.m01();
        d[4] = m.m11();
        d[5] = m.m21();
        d[6] = m.m02();
        d[7] = m.m12();
        d[8] = m.m22();
        return d;
    }

    static double[] rm(Matrix3fc m, double[] d) {
        d[0] = m.m00();
        d[1] = m.m10();
        d[2] = m.m20();
        d[3] = m.m01();
        d[4] = m.m11();
        d[5] = m.m21();
        d[6] = m.m02();
        d[7] = m.m12();
        d[8] = m.m22();
        return d;
    }

    static Matrix3d set(Matrix3d d, double[] a) {
        return d.set(a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8]);
    }

    static Matrix3f set(Matrix3f d, double[] a) {
        return d.set((float) a[0], (float) a[3], (float) a[6], (float) a[1], (float) a[4], (float) a[7], (float) a[2],
                (float) a[5], (float) a[8]);
    }

    static double[] rm(Matrix4dc m, double[] d) {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                d[4 * r + c] = m.get(c, r);
            }
        }
        return d;
    }

    static double[] rm(Matrix4fc m, double[] d) {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                d[4 * r + c] = m.get(c, r);
            }
        }
        return d;
    }

    static Matrix4d set(Matrix4d d, double[] a) {
        return d.set(a[0], a[4], a[8], a[12], a[1], a[5], a[9], a[13], a[2], a[6], a[10], a[14], a[3], a[7], a[11],
                a[15]);
    }

    static Matrix4f set(Matrix4f d, double[] a) {
        return d.set((float) a[0], (float) a[4], (float) a[8], (float) a[12], (float) a[1], (float) a[5], (float) a[9],
                (float) a[13], (float) a[2], (float) a[6], (float) a[10], (float) a[14], (float) a[3], (float) a[7],
                (float) a[11], (float) a[15]);
    }
}
