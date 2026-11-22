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
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dynamisengine.vectrix.color.ColorMathf;
import org.dynamisengine.vectrix.color.ColorScienced;
import org.dynamisengine.vectrix.color.ColorSciencef;
import org.dynamisengine.vectrix.core.Epsilond;
import org.dynamisengine.vectrix.core.Epsilonf;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.fft.Convolutionf;
import org.dynamisengine.vectrix.geometry.CullingKernels;
import org.dynamisengine.vectrix.geometry.FrustumPlanes;
import org.dynamisengine.vectrix.geometry.Intersectiond;
import org.dynamisengine.vectrix.geometry.Intersectionf;
import org.dynamisengine.vectrix.geometry.MeshMath;
import org.dynamisengine.vectrix.gpu.GpuTransformLayout;
import org.dynamisengine.vectrix.ltc.LtcTablef;
import org.dynamisengine.vectrix.optics.Fresneld;
import org.dynamisengine.vectrix.optics.Fresnelf;
import org.dynamisengine.vectrix.sampling.HaltonSequence;
import org.dynamisengine.vectrix.sampling.SobolSequence;
import org.dynamisengine.vectrix.sdf.Sdf3f;
import org.dynamisengine.vectrix.soa.AABBSoA;
import org.junit.jupiter.api.Test;

/** Regression tests for the review's Low findings (section 7) fixed in Batch 5. */
class LowFindingsTest {

    @Test
    void smallTrianglesAreNotDegenerate() {
        float s = 0.001f; // a 1 mm triangle used to give NaN barycentrics and winding 0
        Vector3f a = new Vector3f(0, 0, 0), b = new Vector3f(s, 0, 0), c = new Vector3f(0, s, 0);
        Vector3f bc = MeshMath.barycentric(new Vector3f(0.25f * s, 0.25f * s, 0), a, b, c, new Vector3f());
        assertEquals(0.5f, bc.x, 1e-5f);
        assertEquals(0.25f, bc.y, 1e-5f);
        assertEquals(0.25f, bc.z, 1e-5f);
        assertEquals(1, MeshMath.winding2D(new Vector2f(0, 0), new Vector2f(s, 0), new Vector2f(0, s)));
        assertEquals(-1, MeshMath.winding2D(new Vector2f(0, 0), new Vector2f(0, s), new Vector2f(s, 0)));
        // genuinely degenerate (collinear) input is still rejected
        assertTrue(Float.isNaN(MeshMath.barycentric(new Vector3f(1, 1, 0), a, new Vector3f(1, 1, 0), new Vector3f(2, 2, 0), new Vector3f()).x));
        assertEquals(0, MeshMath.winding2D(new Vector2f(0, 0), new Vector2f(1, 1), new Vector2f(2, 2)));
    }

    @Test
    void rayTouchingAnAarCornerAgreesInFloatAndDouble() {
        // passes exactly through the corner (0, 0): tNear == tFar
        int f = Intersectionf.intersectRayAar(-1f, 1f, 1f, -1f, 0f, 0f, 1f, 1f, new Vector2f());
        int d = Intersectiond.intersectRayAar(-1, 1, 1, -1, 0, 0, 1, 1, new Vector2d());
        assertEquals(d, f);
    }

    @Test
    void zeroToOneDepthNearPlane() {
        float near = 0.5f;
        Matrix4f vk = new Matrix4f().perspective(1.0f, 1.0f, near, 100f, true);
        FrustumPlanes planes = new FrustumPlanes().set(vk, true, true);
        int nz = FrustumPlanes.PLANE_NZ;
        java.util.function.DoubleUnaryOperator dist = z -> planes.x[nz] * 0 + planes.y[nz] * 0 + planes.z[nz] * z + planes.w[nz];
        assertTrue(dist.applyAsDouble(-0.25) < 0, "between camera and near plane must be outside");
        assertTrue(dist.applyAsDouble(-1.0) > 0, "beyond the near plane must be inside");
        assertEquals(0.0, dist.applyAsDouble(-near), 1e-5);
        // the default extraction of an OpenGL matrix is unchanged
        FrustumPlanes gl = new FrustumPlanes().set(new Matrix4f().perspective(1.0f, 1.0f, near, 100f), true);
        assertEquals(0.0, gl.z[nz] * -near + gl.w[nz], 1e-5);
    }

    @Test
    void simdAndScalarCullingAgreeOnNaNBounds() {
        FrustumPlanes fp = new FrustumPlanes().set(new Matrix4f().perspective(1.2f, 1.5f, 0.1f, 100f), false);
        int n = 128;
        AABBSoA boxes = new AABBSoA(n);
        for (int i = 0; i < n; i++) {
            float v = (i % 3 == 0) ? Float.NaN : -1f;
            boxes.set(i, v, -1, -5, 1, 1, -4);
        }
        int[] scalar = new int[n], simd = new int[n];
        CullingKernels.frustumCullAabbBatchScalar(fp, boxes, scalar, n);
        CullingKernels.frustumCullAabbBatchSimd(fp, boxes, simd, n);
        assertArrayEquals(scalar, simd);
    }

    @Test
    void gpuLayoutSizeOverflowIsDetected() {
        GpuTransformLayout layout = GpuTransformLayout.compactTRS();
        assertEquals(28 * 1000, layout.requiredBytes(1000));
        assertThrows(ArithmeticException.class, () -> layout.requiredBytes(100_000_000)); // was -1494967296
    }

    @Test
    void lowDiscrepancyFloatsStayBelowOne() {
        // these indices give doubles within half a float ulp of 1, which a plain cast rounded to 1.0f
        assertTrue((float) HaltonSequence.haltonDouble((1 << 26) - 1, 2) == 1.0f, "precondition");
        assertTrue(HaltonSequence.halton((1 << 26) - 1, 2) < 1.0f);
        assertTrue((float) SobolSequence.sobolDouble(0x55555555, 0) == 1.0f, "precondition");
        assertTrue(SobolSequence.sobol(0x55555555, 0) < 1.0f);
    }

    @Test
    void degenerateCapsuleIsASphere() {
        Vector3f a = new Vector3f(1, 2, 3), p = new Vector3f(4, 6, 3);
        assertEquals(Sdf3f.sphere(new Vector3f(p).sub(a), 0.5f), Sdf3f.capsule(p, a, a, 0.5f), 1e-6f); // was NaN
    }

    @Test
    void emptyConvolutionInputIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Convolutionf.linear(new float[0], new float[] {1, 0}, new float[4]));
    }

    @Test
    void epsilonEqualsHandlesInfinities() {
        assertTrue(Epsilonf.equals(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY));
        assertTrue(Epsilond.equals(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY));
        assertFalse(Epsilonf.equals(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY));
        assertFalse(Epsilonf.equals(Float.NaN, Float.NaN));
        assertFalse(Epsilonf.equals(Float.POSITIVE_INFINITY, 5.0f)); // the tolerance was infinite too
        assertFalse(Epsilond.equals(1.0, Double.POSITIVE_INFINITY));
    }

    @Test
    void ltcSamplingRejectsNaN() {
        float[] table = new float[2 * 2 * 9];
        assertThrows(IllegalArgumentException.class, () -> LtcTablef.sampleMat3(table, 2, 2, Float.NaN, 0.5f, new float[9]));
    }

    @Test
    void conductorFresnelIsExact() {
        for (float deg = 0; deg <= 89; deg += 1) {
            float c = (float) java.lang.Math.cos(java.lang.Math.toRadians(deg));
            // with k = 0 a conductor is a dielectric; the old approximation was 0.099 off at 79 degrees, n = 1.5
            assertEquals(Fresnelf.dielectric(c, 1.0f, 1.5f), Fresnelf.conductor(c, 1.5f, 0.0f), 2e-6f, "deg " + deg);
            assertEquals(Fresneld.dielectric(c, 1.0, 1.5), Fresneld.conductor(c, 1.5, 0.0), 1e-12, "deg " + deg);
        }
        double n = 0.2, k = 3.0; // normal incidence: ((n-1)^2 + k^2) / ((n+1)^2 + k^2)
        assertEquals(((n - 1) * (n - 1) + k * k) / ((n + 1) * (n + 1) + k * k), Fresneld.conductor(1.0, n, k), 1e-12);
        // a negative cosine is a ray leaving the medium: the indices swap (it used to return ~1)
        assertEquals(Fresnelf.dielectric(0.8f, 1.5f, 1.0f), Fresnelf.dielectric(-0.8f, 1.0f, 1.5f), 1e-7f);
    }

    @Test
    void colorConversionsShareOneAccurateMatrix() {
        Vector3f viaScience = ColorSciencef.linearSrgbToXyz(0.2f, 0.5f, 0.9f, new Vector3f());
        Vector3f viaMath = ColorMathf.linearSrgbToXyz(new Vector3f(0.2f, 0.5f, 0.9f), new Vector3f());
        assertEquals(viaMath, viaScience);
        Vector3f back = ColorSciencef.xyzToLinearSrgb(viaScience.x, viaScience.y, viaScience.z, new Vector3f());
        // the 7-digit pair round-trips to float precision; the old 4-digit pair was off by about 1e-4
        assertEquals(0.2f, back.x, 2e-6f);
        assertEquals(0.5f, back.y, 2e-6f);
        assertEquals(0.9f, back.z, 2e-6f);
        for (float kelvin = 1000; kelvin <= 40000; kelvin += 250) {
            Vector3f f = ColorSciencef.kelvinToLinearRgb(kelvin, new Vector3f());
            Vector3d d = ColorScienced.kelvinToLinearRgb(kelvin, new Vector3d());
            assertEquals(d.x, f.x, 1e-6);
            assertEquals(d.y, f.y, 1e-6);
            assertEquals(d.z, f.z, 1e-6);
        }
    }

    @Test
    void smallAngularRatesAndErrorsAreKept() {
        float earth = 7.292e-5f; // rad/s, below the old 1e-4 cut-off
        Quaternionf hour = Quaternionf.integrateAngularVelocity(new Vector3f(0, 0, earth), 3600f, new Quaternionf());
        assertEquals(earth * 3600f, hour.angle(), 1e-5f); // was the identity
        Quaternionf tiny = new Quaternionf().rotationY(1e-6f);
        assertEquals(1e-6f, Quaternionf.angularError(new Quaternionf(), tiny, new Vector3f()).y, 1e-9f); // was 0
        Quaterniond tinyD = new Quaterniond().rotationY(1e-9);
        assertEquals(1e-9, Quaterniond.angularError(new Quaterniond(), tinyD, new Vector3d()).y, 1e-15);
        Quaterniond slow = Quaterniond.integrateAngularVelocity(new Vector3d(5e-7, 0, 0), 10.0, new Quaterniond());
        assertEquals(5e-6, slow.angle(), 1e-15);
    }

    @Test
    void quaternionAngleAndAxisAreAccurateForSmallRotations() {
        // angle() and get(AxisAngle) used 2 * acos(w): 0 in float below ~5e-4 rad, and a non-unit axis below 2e-3
        Quaternionf q = new Quaternionf().rotationAxis(1e-4f, 0.6f, 0.8f, 0f);
        assertEquals(1e-4f, q.angle(), 1e-9f);
        org.dynamisengine.vectrix.core.AxisAngle4f aa = q.get(new org.dynamisengine.vectrix.core.AxisAngle4f());
        assertEquals(1e-4f, aa.angle, 1e-9f);
        assertEquals(1.0f, (float) java.lang.Math.sqrt(aa.x * aa.x + aa.y * aa.y + aa.z * aa.z), 1e-6f);
        assertEquals(0.6f, aa.x, 1e-6f);
        Quaterniond qd = new Quaterniond().rotationAxis(1e-9, 0, 0, 1);
        assertEquals(1e-9, qd.angle(), 1e-20);
        assertEquals(1.0, qd.get(new org.dynamisengine.vectrix.core.AxisAngle4d()).z, 1e-15);
        // large angles are unchanged
        assertEquals(2.5f, new Quaternionf().rotationX(2.5f).angle(), 1e-6f);
    }
}
