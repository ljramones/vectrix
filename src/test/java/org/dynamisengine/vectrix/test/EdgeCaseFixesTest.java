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
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.dynamisengine.vectrix.animation.QuaternionCurvesf;
import org.dynamisengine.vectrix.color.PerceptualColorf;
import org.dynamisengine.vectrix.core.Matrix3f;
import org.dynamisengine.vectrix.dsp.Biquad;
import org.dynamisengine.vectrix.dsp.Kalman;
import org.dynamisengine.vectrix.sh.ShCoeffs9f;
import org.dynamisengine.vectrix.sh.ShRotation;
import org.dynamisengine.vectrix.sampling.SobolSequence;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.geometry.ClosestPointsf;
import org.dynamisengine.vectrix.geometry.Intersectionf;
import org.dynamisengine.vectrix.ik.TwoBoneIkf;
import org.junit.jupiter.api.Test;

/**
 * Edge cases fixed after the 2.6.0 documentation review: outputs that alias inputs in methods AliasingTest cannot
 * reach (they return a value or work on arrays), degenerate inputs, hue wrapping and index overflow.
 */
class EdgeCaseFixesTest {

    @Test
    void zeroLengthSegmentIsAPoint() {
        assertTrue(Intersectionf.testLineSegmentSphere(1, 0, 0, 1, 0, 0, 0, 0, 0, 2));
        assertFalse(Intersectionf.testLineSegmentSphere(3, 0, 0, 3, 0, 0, 0, 0, 0, 2));
    }

    @Test
    void triangleTriangleOutputsMayAliasVertices() {
        Vector3f a0 = new Vector3f(0, 0, 0), a1 = new Vector3f(1, 0, 0), a2 = new Vector3f(0, 1, 0);
        Vector3f b0 = new Vector3f(0.2f, 0.2f, 1), b1 = new Vector3f(1.2f, 0.2f, 2), b2 = new Vector3f(0.2f, 1.2f, 1.5f);
        Vector3f pa = new Vector3f(), pb = new Vector3f();
        float d = ClosestPointsf.distanceSquaredTriangleTriangle(a0, a1, a2, b0, b1, b2, pa, pb);
        Vector3f a0c = new Vector3f(a0), b0c = new Vector3f(b0);
        float d2 = ClosestPointsf.distanceSquaredTriangleTriangle(a0c, a1, a2, b0c, b1, b2, a0c, b0c);
        assertEquals(d, d2, 0f);
        assertEquals(pa, a0c);
        assertEquals(pb, b0c);
    }

    @Test
    void twoBoneOutputsMayAliasTheTarget() {
        Vector3f root = new Vector3f(), mid = new Vector3f(1, 0, 0), end = new Vector3f(2, 0, 0);
        Vector3f pole = new Vector3f(0, 0, -1);
        Vector3f target = new Vector3f(1.2f, 1.1f, 0);
        Vector3f m = new Vector3f(), e = new Vector3f();
        float miss = TwoBoneIkf.solve(root, mid, end, target, pole, 1f, m, e, new Quaternionf(), new Quaternionf());
        Vector3f t2 = new Vector3f(target);
        Vector3f m2 = new Vector3f();
        float miss2 = TwoBoneIkf.solve(root, mid, end, t2, pole, 1f, m2, t2, new Quaternionf(), new Quaternionf());
        assertEquals(miss, miss2, 0f);
        assertEquals(m, m2);
        assertEquals(e, t2);
    }

    @Test
    void squadControlPointsInPlace() {
        float[] keys = new float[4 * 5];
        Quaternionf q = new Quaternionf();
        for (int k = 0; k < 5; k++) {
            q.rotationXYZ(0.3f * k, 0.7f * k * k * 0.1f, -0.2f * k);
            keys[4 * k] = q.x;
            keys[4 * k + 1] = q.y;
            keys[4 * k + 2] = q.z;
            keys[4 * k + 3] = q.w;
        }
        float[] out = QuaternionCurvesf.squadControlPoints(keys, 5, new float[keys.length]);
        float[] inPlace = keys.clone();
        QuaternionCurvesf.squadControlPoints(inPlace, 5, inPlace);
        assertArrayEquals(out, inPlace, 0f);
    }

    @Test
    void negativeHuesWrap() {
        Vector3f a = PerceptualColorf.hsvToRgb(new Vector3f(-60, 1, 1), new Vector3f());
        Vector3f b = PerceptualColorf.hsvToRgb(new Vector3f(300, 1, 1), new Vector3f());
        assertTrue(a.equals(b, 1e-6f), a + " vs " + b);
        Vector3f c = PerceptualColorf.hslToRgb(new Vector3f(-120, 0.5f, 0.5f), new Vector3f());
        Vector3f d = PerceptualColorf.hslToRgb(new Vector3f(240, 0.5f, 0.5f), new Vector3f());
        assertTrue(c.equals(d, 1e-6f), c + " vs " + d);
        // a hue a hair below 0 used to round up to exactly 360
        float h = PerceptualColorf.rgbToHsv(new Vector3f(1, 0, 1e-8f), new Vector3f()).x;
        assertTrue(h >= 0 && h < 360, "hue " + h);
        assertEquals(0f, PerceptualColorf.rgbToHsv(new Vector3f(1, 0, 0), new Vector3f()).x);
    }

    @Test
    void sobolBatchRejectsIndexOverflowBeforeWriting() {
        float[] x = new float[4];
        float[] y = new float[4];
        x[0] = 42;
        assertThrows(IllegalArgumentException.class,
            () -> SobolSequence.sobolBatch2D(Integer.MAX_VALUE - 1, 4, x, 0, y, 0));
        assertEquals(42f, x[0], 0f);
        assertThrows(IllegalArgumentException.class,
            () -> SobolSequence.sobolBatch2D(0, 4, x, Integer.MAX_VALUE, y, 0));
    }

    @Test
    void kalmanChecksEveryArrayBeforeChangingTheState() {
        double[] x = {1, 2};
        double[] p = {1, 0, 0, 1};
        double[] f = {1, 0.1, 0, 1};
        double[] q = {0, 0, 0};          // too short
        double[] scratch = new double[Kalman.scratchSize(2, 1)];
        assertThrows(IllegalArgumentException.class, () -> Kalman.predict(2, x, p, f, q, scratch));
        assertArrayEquals(new double[] {1, 2}, x, 0);
        assertThrows(IllegalArgumentException.class,
            () -> Kalman.update(2, 1, x, p, new double[] {1, 0}, new double[] {1}, new double[0], scratch));
        assertArrayEquals(new double[] {1, 0, 0, 1}, p, 0);
    }

    @Test
    void shRotationRejectsShortArrays() {
        assertThrows(IllegalArgumentException.class,
            () -> ShRotation.rotationMatrixL2(new Matrix3f(), new float[ShRotation.L2_MATRIX_SIZE - 1]));
        assertThrows(IllegalArgumentException.class,
            () -> ShRotation.applyL2(new float[3], new ShCoeffs9f(), new ShCoeffs9f()));
    }

    @Test
    void emptyBiquadCascadePassesThrough() {
        double[] in = {1, -2, 3};
        double[] out = new double[3];
        Biquad.process(new double[0], 0, 0, new double[0], 0, in, 0, out, 0, 3);
        assertArrayEquals(in, out, 0);
    }
}
