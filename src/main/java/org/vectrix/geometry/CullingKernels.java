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
package org.vectrix.geometry;

import org.vectrix.core.Matrix4fc;
import org.vectrix.experimental.KernelConfig;
import org.vectrix.core.MathMode;
import org.vectrix.simd.SimdSupport;
import org.vectrix.soa.AABBSoA;
//#ifdef __HAS_VECTOR_API__
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;
//#endif

/**
 * Scalar batch culling kernels.
 */
public final class CullingKernels {
    private static final String PROP_EXPERIMENTAL_CULLING_SIMD = "vectrix.culling.simd";
    private static final int SIMD_BATCH_THRESHOLD = 64;
    private static final int SIMD_BATCH_MAX = 32768;
    private static final boolean EXPERIMENTAL_CULLING_SIMD = KernelConfig.parseBoolean(System.getProperty(PROP_EXPERIMENTAL_CULLING_SIMD), false);
//#ifdef __HAS_VECTOR_API__
    private static final VectorSpecies<Float> CULL_SPECIES = FloatVector.SPECIES_PREFERRED;
//#endif

    private CullingKernels() {
    }

    /**
     * Compute per-AABB frustum classification into {@code outResult}.
     * Result values are from {@link FrustumIntersection}: {@code INSIDE}, {@code INTERSECT}, {@code OUTSIDE}.
     */
    public static void frustumCullAabbBatch(FrustumPlanes frustum, AABBSoA bounds, int[] outResult, int count) {
        if (count <= 0) {
            return;
        }
        if (KernelConfig.mathMode() == MathMode.STRICT || !KernelConfig.simdEnabled()
                || SimdSupport.backend() != SimdSupport.Backend.VECTOR_API
                || count < SIMD_BATCH_THRESHOLD || count > SIMD_BATCH_MAX
                || !EXPERIMENTAL_CULLING_SIMD) {
            frustumCullAabbBatchScalar(frustum, bounds, outResult, count);
            return;
        }
        frustumCullAabbBatchSimd(frustum, bounds, outResult, count);
    }

    /**
     * Force scalar implementation regardless of runtime backend.
     */
    public static void frustumCullAabbBatchScalar(FrustumPlanes frustum, AABBSoA bounds, int[] outResult, int count) {
        float nxX = frustum.x[FrustumPlanes.PLANE_NX], nxY = frustum.y[FrustumPlanes.PLANE_NX], nxZ = frustum.z[FrustumPlanes.PLANE_NX], nxW = frustum.w[FrustumPlanes.PLANE_NX];
        float pxX = frustum.x[FrustumPlanes.PLANE_PX], pxY = frustum.y[FrustumPlanes.PLANE_PX], pxZ = frustum.z[FrustumPlanes.PLANE_PX], pxW = frustum.w[FrustumPlanes.PLANE_PX];
        float nyX = frustum.x[FrustumPlanes.PLANE_NY], nyY = frustum.y[FrustumPlanes.PLANE_NY], nyZ = frustum.z[FrustumPlanes.PLANE_NY], nyW = frustum.w[FrustumPlanes.PLANE_NY];
        float pyX = frustum.x[FrustumPlanes.PLANE_PY], pyY = frustum.y[FrustumPlanes.PLANE_PY], pyZ = frustum.z[FrustumPlanes.PLANE_PY], pyW = frustum.w[FrustumPlanes.PLANE_PY];
        float nzX = frustum.x[FrustumPlanes.PLANE_NZ], nzY = frustum.y[FrustumPlanes.PLANE_NZ], nzZ = frustum.z[FrustumPlanes.PLANE_NZ], nzW = frustum.w[FrustumPlanes.PLANE_NZ];
        float pzX = frustum.x[FrustumPlanes.PLANE_PZ], pzY = frustum.y[FrustumPlanes.PLANE_PZ], pzZ = frustum.z[FrustumPlanes.PLANE_PZ], pzW = frustum.w[FrustumPlanes.PLANE_PZ];
        for (int i = 0; i < count; i++) {
            float minX = bounds.minX[i], minY = bounds.minY[i], minZ = bounds.minZ[i];
            float maxX = bounds.maxX[i], maxY = bounds.maxY[i], maxZ = bounds.maxZ[i];
            boolean inside = true;
            float dMax = nxX * (nxX < 0.0f ? minX : maxX) + nxY * (nxY < 0.0f ? minY : maxY) + nxZ * (nxZ < 0.0f ? minZ : maxZ) + nxW;
            if (dMax < 0.0f) {
                outResult[i] = FrustumIntersection.OUTSIDE;
                continue;
            }
            float dMin = nxX * (nxX < 0.0f ? maxX : minX) + nxY * (nxY < 0.0f ? maxY : minY) + nxZ * (nxZ < 0.0f ? maxZ : minZ) + nxW;
            inside &= dMin >= 0.0f;

            dMax = pxX * (pxX < 0.0f ? minX : maxX) + pxY * (pxY < 0.0f ? minY : maxY) + pxZ * (pxZ < 0.0f ? minZ : maxZ) + pxW;
            if (dMax < 0.0f) {
                outResult[i] = FrustumIntersection.OUTSIDE;
                continue;
            }
            dMin = pxX * (pxX < 0.0f ? maxX : minX) + pxY * (pxY < 0.0f ? maxY : minY) + pxZ * (pxZ < 0.0f ? maxZ : minZ) + pxW;
            inside &= dMin >= 0.0f;

            dMax = nyX * (nyX < 0.0f ? minX : maxX) + nyY * (nyY < 0.0f ? minY : maxY) + nyZ * (nyZ < 0.0f ? minZ : maxZ) + nyW;
            if (dMax < 0.0f) {
                outResult[i] = FrustumIntersection.OUTSIDE;
                continue;
            }
            dMin = nyX * (nyX < 0.0f ? maxX : minX) + nyY * (nyY < 0.0f ? maxY : minY) + nyZ * (nyZ < 0.0f ? maxZ : minZ) + nyW;
            inside &= dMin >= 0.0f;

            dMax = pyX * (pyX < 0.0f ? minX : maxX) + pyY * (pyY < 0.0f ? minY : maxY) + pyZ * (pyZ < 0.0f ? minZ : maxZ) + pyW;
            if (dMax < 0.0f) {
                outResult[i] = FrustumIntersection.OUTSIDE;
                continue;
            }
            dMin = pyX * (pyX < 0.0f ? maxX : minX) + pyY * (pyY < 0.0f ? maxY : minY) + pyZ * (pyZ < 0.0f ? maxZ : minZ) + pyW;
            inside &= dMin >= 0.0f;

            dMax = nzX * (nzX < 0.0f ? minX : maxX) + nzY * (nzY < 0.0f ? minY : maxY) + nzZ * (nzZ < 0.0f ? minZ : maxZ) + nzW;
            if (dMax < 0.0f) {
                outResult[i] = FrustumIntersection.OUTSIDE;
                continue;
            }
            dMin = nzX * (nzX < 0.0f ? maxX : minX) + nzY * (nzY < 0.0f ? maxY : minY) + nzZ * (nzZ < 0.0f ? maxZ : minZ) + nzW;
            inside &= dMin >= 0.0f;

            dMax = pzX * (pzX < 0.0f ? minX : maxX) + pzY * (pzY < 0.0f ? minY : maxY) + pzZ * (pzZ < 0.0f ? minZ : maxZ) + pzW;
            if (dMax < 0.0f) {
                outResult[i] = FrustumIntersection.OUTSIDE;
                continue;
            }
            dMin = pzX * (pzX < 0.0f ? maxX : minX) + pzY * (pzY < 0.0f ? maxY : minY) + pzZ * (pzZ < 0.0f ? maxZ : minZ) + pzW;
            inside &= dMin >= 0.0f;

            outResult[i] = inside ? FrustumIntersection.INSIDE : FrustumIntersection.INTERSECT;
        }
    }

    /**
     * Force SIMD-lane-style implementation (4-at-a-time loop structure).
     */
    public static void frustumCullAabbBatchSimd(FrustumPlanes frustum, AABBSoA bounds, int[] outResult, int count) {
//#ifdef __HAS_VECTOR_API__
        int lanes = CULL_SPECIES.length();
        if (lanes <= 1) {
            frustumCullAabbBatchScalar(frustum, bounds, outResult, count);
            return;
        }
        boolean nxNegX = frustum.x[FrustumPlanes.PLANE_NX] < 0.0f;
        boolean nxNegY = frustum.y[FrustumPlanes.PLANE_NX] < 0.0f;
        boolean nxNegZ = frustum.z[FrustumPlanes.PLANE_NX] < 0.0f;
        boolean pxNegX = frustum.x[FrustumPlanes.PLANE_PX] < 0.0f;
        boolean pxNegY = frustum.y[FrustumPlanes.PLANE_PX] < 0.0f;
        boolean pxNegZ = frustum.z[FrustumPlanes.PLANE_PX] < 0.0f;
        boolean nyNegX = frustum.x[FrustumPlanes.PLANE_NY] < 0.0f;
        boolean nyNegY = frustum.y[FrustumPlanes.PLANE_NY] < 0.0f;
        boolean nyNegZ = frustum.z[FrustumPlanes.PLANE_NY] < 0.0f;
        boolean pyNegX = frustum.x[FrustumPlanes.PLANE_PY] < 0.0f;
        boolean pyNegY = frustum.y[FrustumPlanes.PLANE_PY] < 0.0f;
        boolean pyNegZ = frustum.z[FrustumPlanes.PLANE_PY] < 0.0f;
        boolean nzNegX = frustum.x[FrustumPlanes.PLANE_NZ] < 0.0f;
        boolean nzNegY = frustum.y[FrustumPlanes.PLANE_NZ] < 0.0f;
        boolean nzNegZ = frustum.z[FrustumPlanes.PLANE_NZ] < 0.0f;
        boolean pzNegX = frustum.x[FrustumPlanes.PLANE_PZ] < 0.0f;
        boolean pzNegY = frustum.y[FrustumPlanes.PLANE_PZ] < 0.0f;
        boolean pzNegZ = frustum.z[FrustumPlanes.PLANE_PZ] < 0.0f;

        FloatVector nxXv = FloatVector.broadcast(CULL_SPECIES, frustum.x[FrustumPlanes.PLANE_NX]);
        FloatVector nxYv = FloatVector.broadcast(CULL_SPECIES, frustum.y[FrustumPlanes.PLANE_NX]);
        FloatVector nxZv = FloatVector.broadcast(CULL_SPECIES, frustum.z[FrustumPlanes.PLANE_NX]);
        FloatVector nxWv = FloatVector.broadcast(CULL_SPECIES, frustum.w[FrustumPlanes.PLANE_NX]);
        FloatVector pxXv = FloatVector.broadcast(CULL_SPECIES, frustum.x[FrustumPlanes.PLANE_PX]);
        FloatVector pxYv = FloatVector.broadcast(CULL_SPECIES, frustum.y[FrustumPlanes.PLANE_PX]);
        FloatVector pxZv = FloatVector.broadcast(CULL_SPECIES, frustum.z[FrustumPlanes.PLANE_PX]);
        FloatVector pxWv = FloatVector.broadcast(CULL_SPECIES, frustum.w[FrustumPlanes.PLANE_PX]);
        FloatVector nyXv = FloatVector.broadcast(CULL_SPECIES, frustum.x[FrustumPlanes.PLANE_NY]);
        FloatVector nyYv = FloatVector.broadcast(CULL_SPECIES, frustum.y[FrustumPlanes.PLANE_NY]);
        FloatVector nyZv = FloatVector.broadcast(CULL_SPECIES, frustum.z[FrustumPlanes.PLANE_NY]);
        FloatVector nyWv = FloatVector.broadcast(CULL_SPECIES, frustum.w[FrustumPlanes.PLANE_NY]);
        FloatVector pyXv = FloatVector.broadcast(CULL_SPECIES, frustum.x[FrustumPlanes.PLANE_PY]);
        FloatVector pyYv = FloatVector.broadcast(CULL_SPECIES, frustum.y[FrustumPlanes.PLANE_PY]);
        FloatVector pyZv = FloatVector.broadcast(CULL_SPECIES, frustum.z[FrustumPlanes.PLANE_PY]);
        FloatVector pyWv = FloatVector.broadcast(CULL_SPECIES, frustum.w[FrustumPlanes.PLANE_PY]);
        FloatVector nzXv = FloatVector.broadcast(CULL_SPECIES, frustum.x[FrustumPlanes.PLANE_NZ]);
        FloatVector nzYv = FloatVector.broadcast(CULL_SPECIES, frustum.y[FrustumPlanes.PLANE_NZ]);
        FloatVector nzZv = FloatVector.broadcast(CULL_SPECIES, frustum.z[FrustumPlanes.PLANE_NZ]);
        FloatVector nzWv = FloatVector.broadcast(CULL_SPECIES, frustum.w[FrustumPlanes.PLANE_NZ]);
        FloatVector pzXv = FloatVector.broadcast(CULL_SPECIES, frustum.x[FrustumPlanes.PLANE_PZ]);
        FloatVector pzYv = FloatVector.broadcast(CULL_SPECIES, frustum.y[FrustumPlanes.PLANE_PZ]);
        FloatVector pzZv = FloatVector.broadcast(CULL_SPECIES, frustum.z[FrustumPlanes.PLANE_PZ]);
        FloatVector pzWv = FloatVector.broadcast(CULL_SPECIES, frustum.w[FrustumPlanes.PLANE_PZ]);

        int i = 0;
        int limit = count - (count % lanes);
        VectorMask<Float> allTrue = CULL_SPECIES.indexInRange(0, lanes);
        VectorMask<Float> allFalse = allTrue.not();
        for (; i < limit; i += lanes) {
            FloatVector minXv = FloatVector.fromArray(CULL_SPECIES, bounds.minX, i);
            FloatVector minYv = FloatVector.fromArray(CULL_SPECIES, bounds.minY, i);
            FloatVector minZv = FloatVector.fromArray(CULL_SPECIES, bounds.minZ, i);
            FloatVector maxXv = FloatVector.fromArray(CULL_SPECIES, bounds.maxX, i);
            FloatVector maxYv = FloatVector.fromArray(CULL_SPECIES, bounds.maxY, i);
            FloatVector maxZv = FloatVector.fromArray(CULL_SPECIES, bounds.maxZ, i);

            VectorMask<Float> outsideMask = allFalse;
            VectorMask<Float> insideMask = allTrue;

            FloatVector dMax = (nxNegX ? minXv : maxXv).mul(nxXv).add((nxNegY ? minYv : maxYv).mul(nxYv)).add((nxNegZ ? minZv : maxZv).mul(nxZv)).add(nxWv);
            outsideMask = outsideMask.or(dMax.lt(0.0f));
            FloatVector dMin = (nxNegX ? maxXv : minXv).mul(nxXv).add((nxNegY ? maxYv : minYv).mul(nxYv)).add((nxNegZ ? maxZv : minZv).mul(nxZv)).add(nxWv);
            insideMask = insideMask.and(dMin.lt(0.0f).not());

            dMax = (pxNegX ? minXv : maxXv).mul(pxXv).add((pxNegY ? minYv : maxYv).mul(pxYv)).add((pxNegZ ? minZv : maxZv).mul(pxZv)).add(pxWv);
            outsideMask = outsideMask.or(dMax.lt(0.0f));
            dMin = (pxNegX ? maxXv : minXv).mul(pxXv).add((pxNegY ? maxYv : minYv).mul(pxYv)).add((pxNegZ ? maxZv : minZv).mul(pxZv)).add(pxWv);
            insideMask = insideMask.and(dMin.lt(0.0f).not());

            dMax = (nyNegX ? minXv : maxXv).mul(nyXv).add((nyNegY ? minYv : maxYv).mul(nyYv)).add((nyNegZ ? minZv : maxZv).mul(nyZv)).add(nyWv);
            outsideMask = outsideMask.or(dMax.lt(0.0f));
            dMin = (nyNegX ? maxXv : minXv).mul(nyXv).add((nyNegY ? maxYv : minYv).mul(nyYv)).add((nyNegZ ? maxZv : minZv).mul(nyZv)).add(nyWv);
            insideMask = insideMask.and(dMin.lt(0.0f).not());

            dMax = (pyNegX ? minXv : maxXv).mul(pyXv).add((pyNegY ? minYv : maxYv).mul(pyYv)).add((pyNegZ ? minZv : maxZv).mul(pyZv)).add(pyWv);
            outsideMask = outsideMask.or(dMax.lt(0.0f));
            dMin = (pyNegX ? maxXv : minXv).mul(pyXv).add((pyNegY ? maxYv : minYv).mul(pyYv)).add((pyNegZ ? maxZv : minZv).mul(pyZv)).add(pyWv);
            insideMask = insideMask.and(dMin.lt(0.0f).not());

            dMax = (nzNegX ? minXv : maxXv).mul(nzXv).add((nzNegY ? minYv : maxYv).mul(nzYv)).add((nzNegZ ? minZv : maxZv).mul(nzZv)).add(nzWv);
            outsideMask = outsideMask.or(dMax.lt(0.0f));
            dMin = (nzNegX ? maxXv : minXv).mul(nzXv).add((nzNegY ? maxYv : minYv).mul(nzYv)).add((nzNegZ ? maxZv : minZv).mul(nzZv)).add(nzWv);
            insideMask = insideMask.and(dMin.lt(0.0f).not());

            dMax = (pzNegX ? minXv : maxXv).mul(pzXv).add((pzNegY ? minYv : maxYv).mul(pzYv)).add((pzNegZ ? minZv : maxZv).mul(pzZv)).add(pzWv);
            outsideMask = outsideMask.or(dMax.lt(0.0f));
            dMin = (pzNegX ? maxXv : minXv).mul(pzXv).add((pzNegY ? maxYv : minYv).mul(pzYv)).add((pzNegZ ? maxZv : minZv).mul(pzZv)).add(pzWv);
            insideMask = insideMask.and(dMin.lt(0.0f).not());

            for (int lane = 0; lane < lanes; lane++) {
                if (outsideMask.laneIsSet(lane)) {
                    outResult[i + lane] = FrustumIntersection.OUTSIDE;
                } else if (insideMask.laneIsSet(lane)) {
                    outResult[i + lane] = FrustumIntersection.INSIDE;
                } else {
                    outResult[i + lane] = FrustumIntersection.INTERSECT;
                }
            }
        }
        for (; i < count; i++) {
            classifyInto(frustum, bounds, outResult, i);
        }
//#else
        int i = 0;
        int limit = count & ~3;
        for (; i < limit; i += 4) {
            classifyInto(frustum, bounds, outResult, i);
            classifyInto(frustum, bounds, outResult, i + 1);
            classifyInto(frustum, bounds, outResult, i + 2);
            classifyInto(frustum, bounds, outResult, i + 3);
        }
        for (; i < count; i++) {
            classifyInto(frustum, bounds, outResult, i);
        }
//#endif
    }

    private static void classifyInto(FrustumPlanes frustum, AABBSoA bounds, int[] outResult, int i) {
        outResult[i] = classifyAabb(
                frustum,
                bounds.minX[i], bounds.minY[i], bounds.minZ[i],
                bounds.maxX[i], bounds.maxY[i], bounds.maxZ[i]);
    }

    /**
     * Convenience overload that extracts frustum planes from {@code viewProj} first.
     */
    public static void frustumCullAabbBatch(Matrix4fc viewProj, AABBSoA bounds, int[] outResult, int count) {
        frustumCullAabbBatch(new FrustumPlanes().set(viewProj, false), bounds, outResult, count);
    }

    public static int classifyAabb(FrustumPlanes frustum, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        boolean inside = true;
        for (int p = 0; p < FrustumPlanes.COUNT; p++) {
            float px = frustum.x[p];
            float py = frustum.y[p];
            float pz = frustum.z[p];
            float pw = frustum.w[p];

            float dMax = px * (px < 0.0f ? minX : maxX)
                    + py * (py < 0.0f ? minY : maxY)
                    + pz * (pz < 0.0f ? minZ : maxZ)
                    + pw;
            if (dMax < 0.0f) {
                return FrustumIntersection.OUTSIDE;
            }
            float dMin = px * (px < 0.0f ? maxX : minX)
                    + py * (py < 0.0f ? maxY : minY)
                    + pz * (pz < 0.0f ? maxZ : minZ)
                    + pw;
            inside &= dMin >= 0.0f;
        }
        return inside ? FrustumIntersection.INSIDE : FrustumIntersection.INTERSECT;
    }
}
