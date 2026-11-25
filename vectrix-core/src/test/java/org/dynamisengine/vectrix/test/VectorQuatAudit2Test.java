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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.Interpolationd;
import org.dynamisengine.vectrix.core.Interpolationf;
import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Quaterniond;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector2f;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4d;
import org.junit.jupiter.api.Test;

/** Second audit of the JOML-inherited vectors, quaternions, interpolation and invsqrt (2.5.0). */
class VectorQuatAudit2Test {

    @Test
    void curvesAndInterpolation() {
        // smoothStep is hermite with zero tangents, exact at both ends
        assertTrue(new Vector3d(1, 1, 1).smoothStep(new Vector3d(2, 2, 2), 0.5, new Vector3d()).equals(1.5, 1.5, 1.5));
        assertTrue(new Vector3d(1e20, 0, 0).smoothStep(new Vector3d(1, 0, 0), 1, new Vector3d()).equals(1, 0, 0));
        assertEquals(1f, new Vector4d(1e20, 0, 0, 0).smoothStep(new Vector4d(1, 0, 0, 0), 1, new Vector4d()).x, 0f);
        // hermite: p0 = this, m0, p1, m1
        Vector3d p0 = new Vector3d(1, 0, 0), m0 = new Vector3d(0, 3, 0), p1 = new Vector3d(2, 0, 0), m1 = new Vector3d(0, 0, 5);
        assertTrue(p0.hermite(m0, p1, m1, 1, new Vector3d()).equals(2, 0, 0), "t = 1 is p1");
        assertTrue(p0.hermite(m0, p1, m1, 0, new Vector3d()).equals(1, 0, 0), "t = 0 is p0");
        assertTrue(p0.hermite(m0, p1, m1, 0.25, new Vector3d()).distance(1.15625, 0.421875, -0.234375) < 1e-15);
        double h = 1e-7;
        Vector3d slope = p0.hermite(m0, p1, m1, h, new Vector3d()).sub(p0).div(h);
        assertTrue(slope.distance(m0) < 1e-5, "the tangent at t = 0 is m0: " + slope);
        // lerp is exact at t = 1 in every vector class
        assertEquals(1.0, new Vector3d(1e20, 0, 0).lerp(new Vector3d(1, 0, 0), 1, new Vector3d()).x, 0.0);
        assertEquals(1f, new Vector2f(1e10f, 0).lerp(new Vector2f(1, 0), 1, new Vector2f()).x, 0f);
        assertEquals(1.0, new Vector4d(1e20, 0, 0, 0).lerp(new Vector4d(1, 0, 0, 0), 1, new Vector4d()).x, 0.0);
        assertEquals(1.0, new Vector2d(1e20, 0).lerp(new Vector2d(1, 0), 1, new Vector2d()).x, 0.0);
        // interpolation derivatives: f = 1e9 + 0.3 x + 0.7 y over a triangle, exact gradient (0.3, 0.7)
        Vector2d gx = Interpolationd.dFdxLinear(0, 0, 1e9, 0, 7, 1, 1e9 + 2.1 + 0.7, 0, 2, 5, 1e9 + 0.6 + 3.5, 0, new Vector2d());
        Vector2d gy = Interpolationd.dFdyLinear(0, 0, 1e9, 0, 7, 1, 1e9 + 2.1 + 0.7, 0, 2, 5, 1e9 + 0.6 + 3.5, 0, new Vector2d());
        assertEquals(0.3, gx.x, 1e-15 * 1e9, "dFdx");
        assertEquals(0.7, gy.x, 1e-15 * 1e9, "dFdy");
        Vector2f gxf = Interpolationf.dFdxLinear(0, 0, 1e4f, 0, 7, 1, 1e4f + 2.8f, 0, 2, 5, 1e4f + 4.1f, 0, new Vector2f());
        assertEquals(0.3f, gxf.x, 2e-4f, "float dFdx");
    }

    @Test
    void orthogonalizeIsGramSchmidt() {
        assertTrue(new Vector3d(0, 0, 1).orthogonalize(new Vector3d(1, 0, 0), new Vector3d()).equals(0, 0, 1));
        assertTrue(new Vector3d(1, 1, 0).orthogonalize(new Vector3d(1, 0, 0), new Vector3d()).equals(0, 1, 0));
        SplittableRandom r = new SplittableRandom(1);
        for (int i = 0; i < 10000; i++) {
            Vector3d a = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
            Vector3d v = new Vector3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).mul(Math.pow(10, r.nextInt(-100, 100)));
            Vector3d o = a.orthogonalize(v, new Vector3d());
            assertEquals(0, o.dot(new Vector3d(v).normalize()), 1e-14, "perpendicular");
            assertEquals(1, o.length(), 1e-15, "unit");
            assertEquals(0, o.dot(new Vector3d(a).cross(new Vector3d(v).normalize())), 1e-13 * a.length(), "in the plane of this and v");
            Vector3d u = a.orthogonalizeUnit(new Vector3d(v).normalize(), new Vector3d());
            assertTrue(u.distance(o) < 1e-14, "unit form agrees");
            Vector3d vn = new Vector3d(v).normalize(); // floats cannot hold 1e+-100
            Vector3f of = new Vector3f((float) a.x, (float) a.y, (float) a.z).orthogonalize(new Vector3f((float) vn.x, (float) vn.y, (float) vn.z), new Vector3f());
            assertTrue(new Vector3d(of.x, of.y, of.z).distance(o) < 1e-5 || Math.abs(new Vector3d(a).normalize().dot(vn)) > 0.999, "float twin");
        }
    }

    @Test
    void roundingAndRanges() {
        Vector3d rd = new Vector3d(Double.NaN, 1e300, Double.NEGATIVE_INFINITY).round(new Vector3d());
        assertTrue(Double.isNaN(rd.x) && rd.y == 1e300 && rd.z == Double.NEGATIVE_INFINITY, rd.toString());
        Vector3d half = new Vector3d(2.5, -2.5, 0.49999999999999994).round(new Vector3d());
        assertTrue(half.equals(3, -2, 0), "half up, as Math.round: " + half);
        assertEquals(3e9f, new Vector3f(3e9f, 0, 0).round(new Vector3f()).x, 0f);
        // overflow- and underflow-safe lengths and distances
        assertEquals(2e200, new Vector3d(1e200, 0, 0).distance(-1e200, 0, 0), 1e186);
        assertEquals(3e-170, new Vector3d(3e-170, 0, 0).distance(0, 0, 0), 1e-184);
        assertEquals(2e20f, new Vector3f(1e20f, 0, 0).distance(-1e20f, 0, 0), 1e14f);
        assertEquals(Math.sqrt(2) * 1e200, Vector2d.distance(1e200, 1e200, 0, 0), 1e186);
        // angleSigned: overflow-safe and normal-length independent
        assertEquals(Math.atan(0.1), new Vector3d(1e200, 0, 0).angleSigned(new Vector3d(1e200, 1e199, 0), new Vector3d(0, 0, 1)), 1e-15);
        assertEquals(Math.atan(0.1), new Vector3d(1e-170, 0, 0).angleSigned(new Vector3d(1e-170, 1e-171, 0), new Vector3d(0, 0, 1)), 1e-15);
        assertEquals(Math.PI / 4, new Vector3d(1, 0, 0).angleSigned(new Vector3d(1, 1, 0), new Vector3d(0, 0, 10)), 1e-15);
        assertEquals(-Math.PI / 4, new Vector3f(1, 0, 0).angleSigned(new Vector3f(1, 1, 0), new Vector3f(0, 0, -10)), 1e-7);
        // rotateAxis about a non-unit axis rotates about its direction; unit axes are unchanged
        assertTrue(new Vector3d(0, 1, 0).rotateAxis(Math.PI / 2, 0, 0, 2, new Vector3d()).distance(-1, 0, 0) < 1e-15);
        assertTrue(new Vector3f(0, 1, 0).rotateAxis((float) Math.PI / 2, 0, 0.5f, 0.5f, new Vector3f())
                .distance(new Vector3f(0, 1, 0).rotateAxis((float) Math.PI / 2, 0, (float) Math.sqrt(0.5), (float) Math.sqrt(0.5), new Vector3f())) < 1e-6);
    }

    @Test
    void quaternionsOfAnyLength() {
        Quaterniond q = new Quaterniond(0.2, 0.4, 0.6, 1.8);
        Vector3d[] axes = {new Vector3d(1, 0, 0), new Vector3d(0, 1, 0), new Vector3d(0, 0, 1)};
        Vector3d[] pos = {q.positiveX(new Vector3d()), q.positiveY(new Vector3d()), q.positiveZ(new Vector3d())};
        Vector3d[] tpos = {q.transformPositiveX(new Vector3d()), q.transformPositiveY(new Vector3d()), q.transformPositiveZ(new Vector3d())};
        Quaternionf qf = new Quaternionf(0.2f, 0.4f, 0.6f, 1.8f);
        Vector3f[] posf = {qf.positiveX(new Vector3f()), qf.positiveY(new Vector3f()), qf.positiveZ(new Vector3f())};
        Vector3f[] tposf = {qf.transformPositiveX(new Vector3f()), qf.transformPositiveY(new Vector3f()), qf.transformPositiveZ(new Vector3f())};
        for (int i = 0; i < 3; i++) {
            Vector3d inv = q.transformInverse(new Vector3d(axes[i])), fwd = q.transform(new Vector3d(axes[i]));
            assertTrue(pos[i].distance(inv) < 1e-15, "positive axis " + i + ": " + pos[i] + " vs " + inv);
            assertTrue(tpos[i].distance(fwd) < 1e-15, "transformPositive axis " + i);
            assertTrue(new Vector3d(posf[i].x, posf[i].y, posf[i].z).distance(inv) < 1e-6, "float positive " + i);
            assertTrue(new Vector3d(tposf[i].x, tposf[i].y, tposf[i].z).distance(fwd) < 1e-6, "float transformPositive " + i);
        }
        // normalize at extreme magnitudes
        Quaterniond tiny = new Quaterniond(1e-170, 0, 0, 0).normalize();
        assertTrue(tiny.equals(new Quaterniond(1, 0, 0, 0), 1e-15), tiny.toString());
        Quaterniond huge = new Quaterniond(1e200, 1e200, 0, 0).normalize();
        assertEquals(Math.sqrt(0.5), huge.x, 1e-15);
        Quaternionf tinyf = new Quaternionf(1e-25f, 0, 0, 0).normalize();
        assertEquals(1f, tinyf.x, 1e-7f);
        assertEquals(Math.sqrt(0.5), new Quaternionf(1e20f, 1e20f, 0, 0).normalize().x, 1e-7);
        Quaterniond ax = new Quaterniond().fromAxisAngleRad(1e-170, 0, 0, 1);
        assertTrue(ax.equals(new Quaterniond(Math.sin(0.5), 0, 0, Math.cos(0.5)), 1e-15), ax.toString());
        assertTrue(new Quaterniond().rotationAxis(1, 1e200, 0, 0).equals(new Quaterniond(Math.sin(0.5), 0, 0, Math.cos(0.5)), 1e-15));
        // integrate: the small-angle branch is now exact to 1e-15
        for (double half : new double[] {0.0221, 0.02, 1e-3}) {
            Quaterniond qi = new Quaterniond().integrate(1, 2 * half, 0, 0, new Quaterniond());
            assertEquals(Math.cos(half), qi.w, 1e-16, "integrate w at " + half);
            assertEquals(Math.sin(half), qi.x, 1e-16, "integrate x at " + half);
        }
    }

    @Test
    void lookAlongStaysOrthogonalAsUpApproachesDir() {
        Vector3d dir = new Vector3d(0.36, 0.48, 0.8);
        for (double eps : new double[] {1e-3, 1e-6, 1e-9}) {
            Vector3d up = new Vector3d(dir).add(0, eps, 0);
            Quaterniond q = new Quaterniond().lookAlong(dir, up);
            Quaterniond ref = new Quaterniond().setFromNormalized(new Matrix3d().lookAlong(dir, up));
            assertEquals(1, q.lengthSquared(), 1e-15, "unit at " + eps);
            // the quaternion form maps dir to +Z (the matrix form maps it to -Z), as documented
            Vector3d mapped = q.transform(new Vector3d(dir).normalize());
            assertTrue(mapped.distance(0, 0, 1) < 1e-14, "dir goes to +Z at " + eps + ": " + mapped);
            assertTrue(ref.lengthSquared() > 0);
        }
    }

    @Test
    void invsqrtIsCorrectlyRounded() {
        SplittableRandom r = new SplittableRandom(3);
        MathContext mc = new MathContext(60);
        int notNearest = 0;
        double worst = 0;
        float worstF = 0;
        for (int i = 0; i < 20000; i++) {
            double x = Math.scalb(1 + r.nextDouble(), r.nextInt(-900, 900));
            BigDecimal ref = BigDecimal.ONE.divide(new BigDecimal(x).sqrt(mc), mc);
            double y = org.dynamisengine.vectrix.core.Math.invsqrt(x);
            double err = new BigDecimal(y).subtract(ref).abs().doubleValue() / Math.ulp(y);
            worst = Math.max(worst, err);
            if (err > 0.5) {
                notNearest++;
            }
            float xf = (float) Math.scalb(1 + r.nextDouble(), r.nextInt(-120, 120));
            BigDecimal reff = BigDecimal.ONE.divide(new BigDecimal(xf).sqrt(mc), mc);
            float yf = org.dynamisengine.vectrix.core.Math.invsqrt(xf);
            worstF = Math.max(worstF, (float) (new BigDecimal(yf).subtract(reff).abs().doubleValue() / Math.ulp(yf)));
        }
        assertTrue(worst < 0.5 + 1e-6, "double invsqrt worst " + worst + " ulp");
        assertTrue(notNearest <= 2, "not correctly rounded: " + notNearest + " of 20000");
        assertTrue(worstF <= 0.5f + 1e-6f, "float invsqrt worst " + worstF + " ulp");
        assertEquals(Double.POSITIVE_INFINITY, org.dynamisengine.vectrix.core.Math.invsqrt(0.0), 0.0);
        assertTrue(Double.isNaN(org.dynamisengine.vectrix.core.Math.invsqrt(-1.0)));
    }
}
