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
package org.vectrix.affine;

import org.vectrix.experimental.KernelConfig;
import org.vectrix.experimental.MathMode;
import org.vectrix.simd.SimdSupport;
import org.vectrix.soa.TransformSoA;

/**
 * Batch kernels for transform processing.
 */
public final class TransformKernels {
    private static final int SIMD_BATCH_THRESHOLD = 32;

    private TransformKernels() {
    }

    /**
     * Compute {@code out[i] = parents[i] * locals[i]} for {@code count} transforms.
     */
    public static void composeBatch(TransformSoA parents, TransformSoA locals, TransformSoA out, int count) {
        if (count <= 0) {
            return;
        }
        if (KernelConfig.mathMode() == MathMode.STRICT) {
            composeBatchScalarStrict(parents, locals, out, count);
            return;
        }
        if (SimdSupport.backend() == SimdSupport.Backend.VECTOR_API && count >= SIMD_BATCH_THRESHOLD) {
            composeBatchVectorFast(parents, locals, out, count);
            return;
        }
        composeBatchScalarFast(parents, locals, out, count);
    }

    private static void composeBatchVectorFast(TransformSoA parents, TransformSoA locals, TransformSoA out, int count) {
        // Vector path currently falls back to scalar math while keeping dispatch separation.
        // This keeps the API stable as true Vector API kernels are iterated on.
        composeBatchScalarFast(parents, locals, out, count);
    }

    private static void composeBatchScalarFast(TransformSoA parents, TransformSoA locals, TransformSoA out, int count) {
        for (int i = 0; i < count; i++) {
            float psx = parents.sx[i];
            float psy = parents.sy[i];
            float psz = parents.sz[i];

            float ltx = locals.tx[i] * psx;
            float lty = locals.ty[i] * psy;
            float ltz = locals.tz[i] * psz;

            float pqx = parents.qx[i];
            float pqy = parents.qy[i];
            float pqz = parents.qz[i];
            float pqw = parents.qw[i];

            float tx2 = 2.0f * (pqy * ltz - pqz * lty);
            float ty2 = 2.0f * (pqz * ltx - pqx * ltz);
            float tz2 = 2.0f * (pqx * lty - pqy * ltx);
            float rx = ltx + pqw * tx2 + (pqy * tz2 - pqz * ty2);
            float ry = lty + pqw * ty2 + (pqz * tx2 - pqx * tz2);
            float rz = ltz + pqw * tz2 + (pqx * ty2 - pqy * tx2);

            out.tx[i] = parents.tx[i] + rx;
            out.ty[i] = parents.ty[i] + ry;
            out.tz[i] = parents.tz[i] + rz;

            float lqx = locals.qx[i];
            float lqy = locals.qy[i];
            float lqz = locals.qz[i];
            float lqw = locals.qw[i];

            out.qx[i] = pqw * lqx + pqx * lqw + pqy * lqz - pqz * lqy;
            out.qy[i] = pqw * lqy - pqx * lqz + pqy * lqw + pqz * lqx;
            out.qz[i] = pqw * lqz + pqx * lqy - pqy * lqx + pqz * lqw;
            out.qw[i] = pqw * lqw - pqx * lqx - pqy * lqy - pqz * lqz;

            out.sx[i] = psx * locals.sx[i];
            out.sy[i] = psy * locals.sy[i];
            out.sz[i] = psz * locals.sz[i];
        }
    }

    private static void composeBatchScalarStrict(TransformSoA parents, TransformSoA locals, TransformSoA out, int count) {
        for (int i = 0; i < count; i++) {
            float psx = parents.sx[i];
            float psy = parents.sy[i];
            float psz = parents.sz[i];

            float ltx = locals.tx[i] * psx;
            float lty = locals.ty[i] * psy;
            float ltz = locals.tz[i] * psz;

            float pqx = parents.qx[i];
            float pqy = parents.qy[i];
            float pqz = parents.qz[i];
            float pqw = parents.qw[i];

            float tx2 = 2.0f * (pqy * ltz - pqz * lty);
            float ty2 = 2.0f * (pqz * ltx - pqx * ltz);
            float tz2 = 2.0f * (pqx * lty - pqy * ltx);
            float rx = ltx + pqw * tx2 + (pqy * tz2 - pqz * ty2);
            float ry = lty + pqw * ty2 + (pqz * tx2 - pqx * tz2);
            float rz = ltz + pqw * tz2 + (pqx * ty2 - pqy * tx2);

            out.tx[i] = parents.tx[i] + rx;
            out.ty[i] = parents.ty[i] + ry;
            out.tz[i] = parents.tz[i] + rz;

            float lqx = locals.qx[i];
            float lqy = locals.qy[i];
            float lqz = locals.qz[i];
            float lqw = locals.qw[i];

            float oqx = pqw * lqx + pqx * lqw + pqy * lqz - pqz * lqy;
            float oqy = pqw * lqy - pqx * lqz + pqy * lqw + pqz * lqx;
            float oqz = pqw * lqz + pqx * lqy - pqy * lqx + pqz * lqw;
            float oqw = pqw * lqw - pqx * lqx - pqy * lqy - pqz * lqz;

            float norm = (float) java.lang.Math.sqrt(oqx * oqx + oqy * oqy + oqz * oqz + oqw * oqw);
            if (norm > 0.0f) {
                float invNorm = 1.0f / norm;
                oqx *= invNorm;
                oqy *= invNorm;
                oqz *= invNorm;
                oqw *= invNorm;
            } else {
                oqx = 0.0f;
                oqy = 0.0f;
                oqz = 0.0f;
                oqw = 1.0f;
            }
            out.qx[i] = oqx;
            out.qy[i] = oqy;
            out.qz[i] = oqz;
            out.qw[i] = oqw;

            out.sx[i] = psx * locals.sx[i];
            out.sy[i] = psy * locals.sy[i];
            out.sz[i] = psz * locals.sz[i];
        }
    }
}
