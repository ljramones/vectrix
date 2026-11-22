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
import org.dynamisengine.vectrix.core.Matrix4d;
import org.dynamisengine.vectrix.core.Matrix4dc;

/**
 * Householder QR decomposition of 3×3 and 4×4 matrices (double precision): {@code a = Q R} with {@code Q}
 * orthogonal and {@code R} upper triangular with a non-negative diagonal (unique for invertible {@code a}).
 *
 * @since 2.2.0
 */
public final class Qrd {
    private Qrd() {
    }

    /** 3×3 QR. Outputs may alias {@code a}. */
    public static void decompose(Matrix3dc a, Matrix3d q, Matrix3d r) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] qq = ws.out9a, rr = ws.out9b;
        LinalgKernels.qr(Conv.rm(a, ws.in9), 3, 3, qq, rr);
        Conv.set(q, qq);
        Conv.set(r, rr);
    }

    /** 4×4 QR. Outputs may alias {@code a}. */
    public static void decompose(Matrix4dc a, Matrix4d q, Matrix4d r) {
        LinalgKernels.Workspace ws = LinalgKernels.ws();
        double[] qq = ws.out9a, rr = ws.out9b;
        LinalgKernels.qr(Conv.rm(a, ws.in9), 4, 4, qq, rr);
        Conv.set(q, qq);
        Conv.set(r, rr);
    }
}
