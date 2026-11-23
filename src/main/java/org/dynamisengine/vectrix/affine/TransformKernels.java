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
package org.dynamisengine.vectrix.affine;

import org.dynamisengine.vectrix.core.CoreConfig;
import org.dynamisengine.vectrix.core.MathMode;
import org.dynamisengine.vectrix.simd.SimdSupport;
import org.dynamisengine.vectrix.soa.TransformSoA;
//#ifdef __HAS_VECTOR_API__
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
//#endif

/**
 * Batch kernels for transform processing.
 */
public final class TransformKernels {
    private static final int SIMD_BATCH_THRESHOLD = 32;

    private TransformKernels() {
    }

    /**
     * Compute {@code out[i] = parents[i] * locals[i]} for {@code count} transforms. {@code out} may be
     * {@code parents} or {@code locals}. In {@link MathMode#STRICT} the rotations are validated first (see
     * {@link #composeBatchScalarStrict}).
     */
    public static void composeBatch(TransformSoA parents, TransformSoA locals, TransformSoA out, int count) {
        if (count <= 0) {
            return;
        }
        if (CoreConfig.mathMode() == MathMode.STRICT) {
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
//#ifdef __HAS_VECTOR_API__
        int done = Simd.compose(parents, locals, out, count);
        composeRangeScalar(parents, locals, out, done, count);
//#else
        composeBatchScalarFast(parents, locals, out, count);
//#endif
    }

    private static void composeBatchScalarFast(TransformSoA parents, TransformSoA locals, TransformSoA out, int count) {
        composeRangeScalar(parents, locals, out, 0, count);
    }

    private static void composeRangeScalar(TransformSoA parents, TransformSoA locals, TransformSoA out, int start, int end) {
        for (int i = start; i < end; i++) {
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

    /**
     * {@link MathMode#STRICT} compose: every parent and local rotation must be a unit quaternion (see
     * {@link CoreConfig#withinStrictUnitTolerance(float)}), otherwise {@link IllegalArgumentException} is thrown
     * before anything is written. The results are then bitwise identical to the FAST scalar path; STRICT does not
     * renormalise the output.
     */
    private static void composeBatchScalarStrict(TransformSoA parents, TransformSoA locals, TransformSoA out, int count) {
        for (int i = 0; i < count; i++) {
            requireUnitRotation(parents, i, "parent");
            requireUnitRotation(locals, i, "local");
        }
        composeBatchScalarFast(parents, locals, out, count);
    }

    private static void requireUnitRotation(TransformSoA t, int i, String role) {
        float x = t.qx[i], y = t.qy[i], z = t.qz[i], w = t.qw[i];
        if (!CoreConfig.withinStrictUnitTolerance(x * x + y * y + z * z + w * w)) {
            throw new IllegalArgumentException("STRICT compose requires unit rotations; " + role + " " + i
                    + " is (" + x + ", " + y + ", " + z + ", " + w + ")");
        }
    }

//#ifdef __HAS_VECTOR_API__
    /**
     * Vector API compose, in its own class so that only this path loads {@code jdk.incubator.vector} (review
     * M-15). Every lane performs the scalar path's operations in the same order, so results are bitwise identical.
     * All inputs of a lane block are loaded before anything is stored, so {@code out} may alias an input.
     */
    private static final class Simd {
        static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

        private Simd() {
        }

        /** Composes the largest lane-aligned prefix and returns its length. */
        static int compose(TransformSoA parents, TransformSoA locals, TransformSoA out, int count) {
            int lanes = SPECIES.length();
            int limit = SPECIES.loopBound(count);
            for (int i = 0; i < limit; i += lanes) {
                FloatVector psx = FloatVector.fromArray(SPECIES, parents.sx, i);
                FloatVector psy = FloatVector.fromArray(SPECIES, parents.sy, i);
                FloatVector psz = FloatVector.fromArray(SPECIES, parents.sz, i);
                FloatVector ptx = FloatVector.fromArray(SPECIES, parents.tx, i);
                FloatVector pty = FloatVector.fromArray(SPECIES, parents.ty, i);
                FloatVector ptz = FloatVector.fromArray(SPECIES, parents.tz, i);
                FloatVector pqx = FloatVector.fromArray(SPECIES, parents.qx, i);
                FloatVector pqy = FloatVector.fromArray(SPECIES, parents.qy, i);
                FloatVector pqz = FloatVector.fromArray(SPECIES, parents.qz, i);
                FloatVector pqw = FloatVector.fromArray(SPECIES, parents.qw, i);
                FloatVector lsx = FloatVector.fromArray(SPECIES, locals.sx, i);
                FloatVector lsy = FloatVector.fromArray(SPECIES, locals.sy, i);
                FloatVector lsz = FloatVector.fromArray(SPECIES, locals.sz, i);
                FloatVector lqx = FloatVector.fromArray(SPECIES, locals.qx, i);
                FloatVector lqy = FloatVector.fromArray(SPECIES, locals.qy, i);
                FloatVector lqz = FloatVector.fromArray(SPECIES, locals.qz, i);
                FloatVector lqw = FloatVector.fromArray(SPECIES, locals.qw, i);
                FloatVector ltx = FloatVector.fromArray(SPECIES, locals.tx, i).mul(psx);
                FloatVector lty = FloatVector.fromArray(SPECIES, locals.ty, i).mul(psy);
                FloatVector ltz = FloatVector.fromArray(SPECIES, locals.tz, i).mul(psz);

                FloatVector tx2 = pqy.mul(ltz).sub(pqz.mul(lty)).mul(2.0f);
                FloatVector ty2 = pqz.mul(ltx).sub(pqx.mul(ltz)).mul(2.0f);
                FloatVector tz2 = pqx.mul(lty).sub(pqy.mul(ltx)).mul(2.0f);
                FloatVector rx = ltx.add(pqw.mul(tx2)).add(pqy.mul(tz2).sub(pqz.mul(ty2)));
                FloatVector ry = lty.add(pqw.mul(ty2)).add(pqz.mul(tx2).sub(pqx.mul(tz2)));
                FloatVector rz = ltz.add(pqw.mul(tz2)).add(pqx.mul(ty2).sub(pqy.mul(tx2)));

                FloatVector oqx = pqw.mul(lqx).add(pqx.mul(lqw)).add(pqy.mul(lqz)).sub(pqz.mul(lqy));
                FloatVector oqy = pqw.mul(lqy).sub(pqx.mul(lqz)).add(pqy.mul(lqw)).add(pqz.mul(lqx));
                FloatVector oqz = pqw.mul(lqz).add(pqx.mul(lqy)).sub(pqy.mul(lqx)).add(pqz.mul(lqw));
                FloatVector oqw = pqw.mul(lqw).sub(pqx.mul(lqx)).sub(pqy.mul(lqy)).sub(pqz.mul(lqz));

                ptx.add(rx).intoArray(out.tx, i);
                pty.add(ry).intoArray(out.ty, i);
                ptz.add(rz).intoArray(out.tz, i);
                oqx.intoArray(out.qx, i);
                oqy.intoArray(out.qy, i);
                oqz.intoArray(out.qz, i);
                oqw.intoArray(out.qw, i);
                psx.mul(lsx).intoArray(out.sx, i);
                psy.mul(lsy).intoArray(out.sy, i);
                psz.mul(lsz).intoArray(out.sz, i);
            }
            return limit;
        }
    }
//#endif
}
