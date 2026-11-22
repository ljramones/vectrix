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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Matrix3f;
import org.dynamisengine.vectrix.core.Matrix4d;
import org.dynamisengine.vectrix.core.Matrix4x3d;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector4d;
import org.dynamisengine.vectrix.linalg.Eigen3d;
import org.dynamisengine.vectrix.linalg.LeastSquaresd;
import org.dynamisengine.vectrix.linalg.Lie3d;
import org.dynamisengine.vectrix.linalg.Polar3d;
import org.dynamisengine.vectrix.linalg.Qrd;
import org.dynamisengine.vectrix.linalg.Solversd;
import org.dynamisengine.vectrix.linalg.Svd3d;
import org.dynamisengine.vectrix.physics.InertiaTensorf;
import org.junit.jupiter.api.Test;

/**
 * Acceptance tests for the {@code linalg} package (review §16): each decomposition reproduces its input and has the
 * promised structure on random, repeated-eigenvalue, rank-deficient and reflecting matrices; solvers have small
 * residuals and detect bad input; the SO(3)/SE(3) maps invert each other up to π; fits recover known geometry.
 */
class LinalgTest {

    private static Matrix3d random(SplittableRandom rnd) {
        Matrix3d m = new Matrix3d();
        m.set(rnd.nextDouble(-2, 2), rnd.nextDouble(-2, 2), rnd.nextDouble(-2, 2), rnd.nextDouble(-2, 2),
                rnd.nextDouble(-2, 2), rnd.nextDouble(-2, 2), rnd.nextDouble(-2, 2), rnd.nextDouble(-2, 2),
                rnd.nextDouble(-2, 2));
        return m;
    }

    private static void assertClose(Matrix3d expected, Matrix3d actual, double tol, String what) {
        assertTrue(expected.equals(actual, tol), what + ": expected\n" + expected + "but got\n" + actual);
    }

    private static void assertRotation(Matrix3d r, double tol, String what) {
        Matrix3d rtr = new Matrix3d(r).transpose().mul(r);
        assertClose(new Matrix3d(), rtr, tol, what + " orthonormal");
        assertEquals(1.0, r.determinant(), tol, what + " det");
    }

    @Test
    void symmetricEigenDecomposition() {
        SplittableRandom rnd = new SplittableRandom(1);
        Vector3d w = new Vector3d();
        Matrix3d v = new Matrix3d();
        Matrix3d[] cases = new Matrix3d[300];
        for (int i = 0; i < cases.length; i++) {
            Matrix3d m = random(rnd);
            cases[i] = new Matrix3d(m).add(new Matrix3d(m).transpose()); // symmetric
        }
        cases[0] = new Matrix3d().scaling(2, 2, 2); // triple eigenvalue
        cases[1] = new Matrix3d().scaling(5, 1, 1); // double eigenvalue
        cases[2] = new Matrix3d().zero();
        cases[3] = new Matrix3d(new Matrix3d().rotateXYZ(0.3, 1.1, -0.7)).scale(1); // rotated scaling below
        cases[3] = new Matrix3d(new Matrix3d().rotateXYZ(0.3, 1.1, -0.7)).mul(new Matrix3d().scaling(3, 3, -1))
                .mul(new Matrix3d(new Matrix3d().rotateXYZ(0.3, 1.1, -0.7)).transpose());
        for (Matrix3d a : cases) {
            Eigen3d.symmetric(a, w, v);
            assertTrue(w.x >= w.y && w.y >= w.z, "sorted " + w);
            assertRotation(v, 1e-12, "eigenvectors");
            Matrix3d rebuilt = new Matrix3d(v).mul(new Matrix3d().scaling(w.x, w.y, w.z)).mul(new Matrix3d(v).transpose());
            assertClose(a, rebuilt, 1e-12 * Math.max(1, Math.abs(w.x) + Math.abs(w.z)), "V Λ Vᵀ");
        }
        // principal axes of an inertia tensor (float API)
        Matrix3f inertia = new Matrix3f(new Matrix3f().rotateZ(0.4f)).mul(new Matrix3f().scaling(3, 2, 1))
                .mul(new Matrix3f(new Matrix3f().rotateZ(0.4f)).transpose());
        Vector3f moments = InertiaTensorf.principalAxes(inertia, new Vector3f(), new Matrix3f());
        assertEquals(3f, moments.x, 1e-6f);
        assertEquals(2f, moments.y, 1e-6f);
        assertEquals(1f, moments.z, 1e-6f);
    }

    @Test
    void signedSvd() {
        SplittableRandom rnd = new SplittableRandom(2);
        Matrix3d u = new Matrix3d(), v = new Matrix3d();
        Vector3d s = new Vector3d();
        for (int i = 0; i < 500; i++) {
            Matrix3d a = random(rnd);
            if (i % 5 == 0) {
                a.setColumn(2, a.getColumn(0, new Vector3d()).mul(0.5)); // rank 2
            }
            if (i % 7 == 0) {
                a.zero().set(0, 0, rnd.nextDouble(-1, 1)); // rank 1
            }
            Svd3d.decompose(a, u, s, v);
            assertRotation(u, 1e-11, "U");
            assertRotation(v, 1e-11, "V");
            assertTrue(s.x >= s.y - 1e-12 && s.y >= Math.abs(s.z) - 1e-12, "ordering " + s);
            assertTrue(s.x >= 0 && s.y >= -1e-12, "non-negative " + s);
            if (Math.abs(a.determinant()) > 1e-9) {
                assertEquals(Math.signum(a.determinant()), Math.signum(s.z), 0, "reflection carried by σ2");
            }
            Matrix3d rebuilt = new Matrix3d(u).mul(new Matrix3d().scaling(s.x, s.y, s.z)).mul(new Matrix3d(v).transpose());
            assertClose(a, rebuilt, 1e-11, "U Σ Vᵀ");
        }
        assertEquals(100.0, Svd3d.condition(new Matrix3d().scaling(1, 10, 100)), 1e-9);
        assertTrue(Double.isInfinite(Svd3d.condition(new Matrix3d().zero())));
    }

    /** Independent reference: Higham's iteration R ← (R + R⁻ᵀ) / 2 converges to the polar rotation. */
    private static Matrix3d highamPolar(Matrix3d a) {
        Matrix3d r = new Matrix3d(a);
        for (int i = 0; i < 100; i++) {
            Matrix3d invT = new Matrix3d(r).invert().transpose();
            r.add(invT).scale(0.5);
        }
        return r;
    }

    @Test
    void polarDecomposition() {
        SplittableRandom rnd = new SplittableRandom(3);
        Matrix3d r = new Matrix3d(), sym = new Matrix3d();
        for (int i = 0; i < 300; i++) {
            Matrix3d a = random(rnd);
            if (a.determinant() < 0) {
                a.scale(-1);
            }
            Polar3d.decompose(a, r, sym);
            assertRotation(r, 1e-11, "R");
            assertClose(new Matrix3d(sym).transpose(), sym, 1e-12, "S symmetric");
            assertClose(a, new Matrix3d(r).mul(sym), 1e-11, "R S");
            if (a.determinant() > 1e-3) {
                assertClose(highamPolar(a), r, 1e-9, "against Higham's iteration");
            }
        }
        // a rotation is its own polar rotation, and a reflected matrix still gives a proper rotation
        Matrix3d rot = new Matrix3d().rotateXYZ(0.2, -1.3, 2.9);
        assertClose(rot, Polar3d.rotation(rot, new Matrix3d()), 1e-12, "rotation is fixed");
        Matrix3d refl = new Matrix3d(rot).mul(new Matrix3d().scaling(1, 1, -2));
        assertRotation(Polar3d.rotation(refl, new Matrix3d()), 1e-12, "R of a reflection");
    }

    @Test
    void householderQr() {
        SplittableRandom rnd = new SplittableRandom(4);
        Matrix3d q = new Matrix3d(), rr = new Matrix3d();
        for (int i = 0; i < 200; i++) {
            Matrix3d a = random(rnd);
            Qrd.decompose(a, q, rr);
            assertClose(new Matrix3d(), new Matrix3d(q).transpose().mul(q), 1e-12, "Q orthogonal");
            assertTrue(Math.abs(rr.m01()) < 1e-15 && Math.abs(rr.m02()) < 1e-15 && Math.abs(rr.m12()) < 1e-15,
                    "R upper triangular");
            assertTrue(rr.m00() >= 0 && rr.m11() >= 0 && rr.m22() >= 0, "non-negative diagonal");
            assertClose(a, new Matrix3d(q).mul(rr), 1e-12, "Q R");
        }
        Matrix4d a4 = new Matrix4d().rotateXYZ(0.3, 0.4, 0.5).translate(1, 2, 3).scale(2, 0.5, 1.5);
        a4.m33(2.0).m03(0.25);
        Matrix4d q4 = new Matrix4d(), r4 = new Matrix4d();
        Qrd.decompose(a4, q4, r4);
        assertTrue(a4.equals(new Matrix4d(q4).mul(r4), 1e-12), "4x4 Q R");
        assertTrue(new Matrix4d().equals(new Matrix4d(q4).transpose().mul(q4), 1e-12), "4x4 Q orthogonal");
    }

    @Test
    void solvers() {
        SplittableRandom rnd = new SplittableRandom(5);
        Matrix3d l = new Matrix3d();
        Vector3d x = new Vector3d(), d = new Vector3d();
        for (int i = 0; i < 300; i++) {
            Matrix3d m = random(rnd);
            Matrix3d spd = new Matrix3d(m).transpose().mul(m).add(new Matrix3d().scaling(0.1));
            Vector3d b = new Vector3d(rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1));
            assertTrue(Solversd.cholesky(spd, l));
            Solversd.solveCholesky(l, b, x);
            assertTrue(spd.transform(new Vector3d(x)).distance(b) < 1e-10, "Cholesky residual");
            Matrix3d sym = new Matrix3d(m).add(new Matrix3d(m).transpose()); // indefinite in general
            if (Solversd.ldlt(sym, l, d)) {
                Solversd.solveLdlt(l, d, b, x);
                assertTrue(sym.transform(new Vector3d(x)).distance(b) < 1e-7 * Math.max(1, Solversd.condition1(sym)),
                        "LDLᵀ residual");
            }
            assertTrue(Solversd.solve(m, b, x));
            assertTrue(m.transform(new Vector3d(x)).distance(b) < 1e-9 * Math.max(1, Solversd.condition1(m)),
                    "LU residual");
        }
        assertFalse(Solversd.cholesky(new Matrix3d().scaling(1, -1, 1), l), "indefinite matrix");
        assertFalse(Solversd.solve(new Matrix3d().zero(), new Vector3d(1), x), "singular matrix");
        // LU needs pivoting here: a zero leading entry
        Matrix3d piv = new Matrix3d(0, 1, 0, 1, 0, 0, 0, 0, 1);
        assertTrue(Solversd.solve(piv, new Vector3d(2, 3, 4), x));
        assertEquals(3, x.x, 1e-15);
        assertEquals(2, x.y, 1e-15);
        Matrix4d m4 = new Matrix4d().rotateXYZ(0.3, 0.2, 0.1).scale(3, 1, 0.5).m30(0.4).m33(1.5);
        Vector4d b4 = new Vector4d(1, -2, 0.5, 3), x4 = new Vector4d();
        assertTrue(Solversd.solve(m4, b4, x4));
        assertTrue(m4.transform(new Vector4d(x4)).distance(b4) < 1e-12);
        assertEquals(1.0, Solversd.condition1(new Matrix4d()), 0);
        assertEquals(100.0, Solversd.condition1(new Matrix3d().scaling(1, 10, 100)), 1e-12);
    }

    @Test
    void so3ExpAndLog() {
        SplittableRandom rnd = new SplittableRandom(6);
        Matrix3d r = new Matrix3d();
        Vector3d w = new Vector3d();
        for (int i = 0; i < 1000; i++) {
            Vector3d axis = new Vector3d(rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1)).normalize();
            double angle = i < 100 ? rnd.nextDouble(0, 1e-5) : i < 200 ? Math.PI - rnd.nextDouble(0, 1e-4)
                    : rnd.nextDouble(0, Math.PI);
            Vector3d w0 = new Vector3d(axis).mul(angle);
            Lie3d.expSo3(w0, r);
            // against an independent construction (axis-angle; the quaternion route loses ~1e-12 near π)
            Matrix3d ref = new Matrix3d().set(new org.dynamisengine.vectrix.core.AxisAngle4d(angle, axis.x, axis.y, axis.z));
            assertClose(ref, r, 1e-12, "exp vs axis-angle at angle " + angle);
            Lie3d.logSo3(r, w);
            if (Math.PI - angle > 1e-6) {
                assertTrue(w.distance(w0) < 2e-8, "log(exp(w)) = w at angle " + angle + ": " + w + " vs " + w0);
            }
            Matrix3d back = Lie3d.expSo3(w, new Matrix3d());
            assertClose(r, back, 1e-9, "exp(log(R)) = R at angle " + angle);
        }
        Lie3d.logSo3(new Matrix3d(), w);
        assertEquals(0.0, w.length(), 0.0);
    }

    @Test
    void se3ExpAndLog() {
        SplittableRandom rnd = new SplittableRandom(7);
        Matrix4x3d m = new Matrix4x3d();
        Vector3d w = new Vector3d(), v = new Vector3d();
        for (int i = 0; i < 500; i++) {
            Vector3d w0 = new Vector3d(rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1))
                    .normalize().mul(i < 50 ? 1e-6 : rnd.nextDouble(0, 3.1)); // log returns the angle in [0, π]
            Vector3d v0 = new Vector3d(rnd.nextDouble(-5, 5), rnd.nextDouble(-5, 5), rnd.nextDouble(-5, 5));
            Lie3d.expSe3(w0, v0, m);
            Lie3d.logSe3(m, w, v);
            assertTrue(w.distance(w0) < 1e-9 && v.distance(v0) < 1e-8, "log(exp) at " + w0.length());
        }
        // a pure translation twist moves by v; a pure rotation twist about the origin has no translation
        Lie3d.expSe3(new Vector3d(), new Vector3d(1, 2, 3), m);
        assertEquals(1, m.m30(), 0);
        assertEquals(3, m.m32(), 0);
        Lie3d.expSe3(new Vector3d(0, 0, 1), new Vector3d(), m);
        assertEquals(0, m.m30(), 1e-15);
    }

    @Test
    void leastSquaresAndFits() {
        // overdetermined line fit y = 2x + 1 with symmetric noise: exact recovery
        int n = 20;
        double[] a = new double[2 * n], b = new double[n], x = new double[2];
        for (int i = 0; i < n; i++) {
            a[2 * i] = i;
            a[2 * i + 1] = 1;
            b[i] = 2 * i + 1 + (i % 2 == 0 ? 0.01 : -0.01);
        }
        assertTrue(LeastSquaresd.solve(a, b, n, 2, x));
        assertEquals(2.0, x[0], 1e-3);
        assertEquals(1.0, x[1], 2e-2);
        assertFalse(LeastSquaresd.solve(new double[] {1, 2, 2, 4, 3, 6}, new double[] {1, 2, 3}, 3, 2, x), "rank deficient");
        // plane through noisy samples of n = (1, 2, 2)/3, d = -2
        SplittableRandom rnd = new SplittableRandom(8);
        Vector3d nn = new Vector3d(1, 2, 2).normalize();
        Vector3d t1 = new Vector3d(2, -1, 0).normalize(), t2 = new Vector3d(nn).cross(t1);
        double[] pts = new double[3 * 200];
        for (int i = 0; i < 200; i++) {
            Vector3d p = new Vector3d(nn).mul(2).fma(rnd.nextDouble(-5, 5), t1).fma(rnd.nextDouble(-5, 5), t2)
                    .fma(rnd.nextDouble(-1e-3, 1e-3), nn);
            pts[3 * i] = p.x;
            pts[3 * i + 1] = p.y;
            pts[3 * i + 2] = p.z;
        }
        Vector4d plane = LeastSquaresd.fitPlane(pts, 200, new Vector4d());
        double sign = Math.signum(plane.x);
        assertEquals(nn.x, sign * plane.x, 1e-4);
        assertEquals(nn.z, sign * plane.z, 1e-4);
        assertEquals(-2.0, sign * plane.w, 1e-3);
        Vector3d pt = new Vector3d(), dir = new Vector3d();
        LeastSquaresd.fitLine(new double[] {0, 0, 0, 1, 1, 1, 2, 2, 2, 3, 3, 3}, 4, pt, dir);
        assertEquals(1.5, pt.x, 1e-15);
        assertEquals(1 / Math.sqrt(3), Math.abs(dir.y), 1e-12);
    }
}
