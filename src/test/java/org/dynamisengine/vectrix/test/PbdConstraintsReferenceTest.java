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

import java.util.Random;

import org.dynamisengine.vectrix.core.Matrix3f;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.physics.PbdConstraintsf;
import org.junit.jupiter.api.Test;

/**
 * Reference tests for the PBD helpers (review findings C-3, C-4, H-3). Gradients are checked against central
 * finite differences of an independently written constraint function, evaluated in double precision.
 * Before the fix, the bend correction pushed away from the rest angle with an unbounded step, the volume
 * gradients for p0/p2 had the wrong sign, and shape matching applied the inverse rotation.
 */
class PbdConstraintsReferenceTest {

    private static double angle(double[] p0, double[] p1, double[] p2) {
        double ux = p0[0] - p1[0], uy = p0[1] - p1[1], uz = p0[2] - p1[2];
        double vx = p2[0] - p1[0], vy = p2[1] - p1[1], vz = p2[2] - p1[2];
        double cx = uy * vz - uz * vy, cy = uz * vx - ux * vz, cz = ux * vy - uy * vx;
        return Math.atan2(Math.sqrt(cx * cx + cy * cy + cz * cz), ux * vx + uy * vy + uz * vz);
    }

    private static double volume(double[][] p) {
        double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
        double bx = p[2][0] - p[0][0], by = p[2][1] - p[0][1], bz = p[2][2] - p[0][2];
        double cx = p[3][0] - p[0][0], cy = p[3][1] - p[0][1], cz = p[3][2] - p[0][2];
        return (ax * (by * cz - bz * cy) + ay * (bz * cx - bx * cz) + az * (bx * cy - by * cx)) / 6.0;
    }

    private interface Constraint {
        double eval(double[][] p);
    }

    /** Numerical gradient of c with respect to every coordinate of every point. */
    private static double[][] numericGradient(Constraint c, double[][] p) {
        double h = 1e-6;
        double[][] g = new double[p.length][3];
        for (int i = 0; i < p.length; i++) {
            for (int k = 0; k < 3; k++) {
                double old = p[i][k];
                p[i][k] = old + h;
                double plus = c.eval(p);
                p[i][k] = old - h;
                double minus = c.eval(p);
                p[i][k] = old;
                g[i][k] = (plus - minus) / (2 * h);
            }
        }
        return g;
    }

    /** Expected PBD correction d_i = -k C w_i g_i / sum_j w_j |g_j|^2. */
    private static double[][] expectedCorrections(double c, double[][] g, double[] w, double k) {
        double denom = 0;
        for (int i = 0; i < g.length; i++) {
            denom += w[i] * (g[i][0] * g[i][0] + g[i][1] * g[i][1] + g[i][2] * g[i][2]);
        }
        double[][] d = new double[g.length][3];
        for (int i = 0; i < g.length; i++) {
            for (int a = 0; a < 3; a++) {
                d[i][a] = -k * c * w[i] * g[i][a] / denom;
            }
        }
        return d;
    }

    private static double[] rnd(Random r, double scale) {
        return new double[] {(r.nextDouble() - 0.5) * scale, (r.nextDouble() - 0.5) * scale, (r.nextDouble() - 0.5) * scale};
    }

    private static Vector3f v(double[] a) {
        return new Vector3f((float) a[0], (float) a[1], (float) a[2]);
    }

    private static void assertVec(double[] expected, Vector3f actual, double tol, String msg) {
        assertEquals(expected[0], actual.x, tol, msg + ".x");
        assertEquals(expected[1], actual.y, tol, msg + ".y");
        assertEquals(expected[2], actual.z, tol, msg + ".z");
    }

    @Test
    void bendMatchesPbdProjectionWithNumericGradients() {
        Random r = new Random(7);
        for (int trial = 0; trial < 200; trial++) {
            double scale = trial % 2 == 0 ? 1.0 : 0.02; // also centimetre-scale edges
            double[][] p = {rnd(r, scale), rnd(r, scale), rnd(r, scale)};
            double[] w = {r.nextDouble() + 0.1, r.nextDouble() + 0.1, r.nextDouble() + 0.1};
            double rest = r.nextDouble() * Math.PI;
            double theta = angle(p[0], p[1], p[2]);
            if (theta < 0.05 || theta > Math.PI - 0.05) {
                continue;
            }
            double[][] g = numericGradient(q -> angle(q[0], q[1], q[2]), p);
            double[][] e = expectedCorrections(theta - rest, g, w, 0.8);
            Vector3f d0 = new Vector3f(), d1 = new Vector3f(), d2 = new Vector3f();
            PbdConstraintsf.bendConstraint(v(p[0]), (float) w[0], v(p[1]), (float) w[1], v(p[2]), (float) w[2],
                    (float) rest, 0.8f, d0, d1, d2);
            double tol = 2e-3 * scale + 1e-5;
            assertVec(e[0], d0, tol, "d0 trial " + trial);
            assertVec(e[1], d1, tol, "d1 trial " + trial);
            assertVec(e[2], d2, tol, "d2 trial " + trial);
        }
    }

    @Test
    void bendStepMovesTowardRestAndConservesMomentum() {
        Vector3f p0 = new Vector3f(1, 0, 0), p1 = new Vector3f(0, 0, 0), p2 = new Vector3f(0, 1, 0);
        Vector3f d0 = new Vector3f(), d1 = new Vector3f(), d2 = new Vector3f();
        float w0 = 1.0f, w1 = 0.5f, w2 = 2.0f;
        PbdConstraintsf.bendConstraint(p0, w0, p1, w1, p2, w2, 1.0f, 1.0f, d0, d1, d2);
        double before = Math.abs(Math.PI / 2 - 1.0);
        double after = Math.abs(angle(arr(p0.add(d0, new Vector3f())), arr(p1.add(d1, new Vector3f())),
                arr(p2.add(d2, new Vector3f()))) - 1.0);
        assertTrue(after < 0.1 * before, "angle error " + before + " -> " + after); // was 0.571 -> 1.29
        // momentum: sum m_i d_i = sum d_i / w_i = 0
        assertEquals(0.0, d0.x / w0 + d1.x / w1 + d2.x / w2, 1e-5);
        assertEquals(0.0, d0.y / w0 + d1.y / w1 + d2.y / w2, 1e-5);
        // centimetre edges give centimetre-scale corrections (was ~57 units)
        Vector3f s0 = new Vector3f(0.01f, 0, 0), s2 = new Vector3f(0, 0.01f, 0);
        PbdConstraintsf.bendConstraint(s0, p1, s2, 1.0f, 1.0f, d0, d1, d2);
        assertTrue(d0.length() < 0.01f && d2.length() < 0.01f, "|d0|=" + d0.length());
    }

    @Test
    void pinnedVertexDoesNotMove() {
        Vector3f d0 = new Vector3f(), d1 = new Vector3f(), d2 = new Vector3f();
        PbdConstraintsf.bendConstraint(new Vector3f(1, 0, 0), 0.0f, new Vector3f(), 1.0f, new Vector3f(0, 1, 0), 1.0f,
                2.0f, 1.0f, d0, d1, d2);
        assertEquals(0.0f, d0.length());
        assertTrue(d2.length() > 0.0f);
    }

    @Test
    void volumeMatchesPbdProjectionWithNumericGradients() {
        Random r = new Random(11);
        for (int trial = 0; trial < 200; trial++) {
            double[][] p = {rnd(r, 2), rnd(r, 2), rnd(r, 2), rnd(r, 2)};
            double[] w = {r.nextDouble() + 0.1, r.nextDouble() + 0.1, r.nextDouble() + 0.1, r.nextDouble() + 0.1};
            double vol = volume(p);
            double rest = vol * (0.5 + r.nextDouble());
            double[][] g = numericGradient(PbdConstraintsReferenceTest::volume, p);
            double[][] e = expectedCorrections(vol - rest, g, w, 1.0);
            Vector3f[] d = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};
            float got = PbdConstraintsf.volumeConstraint(v(p[0]), (float) w[0], v(p[1]), (float) w[1], v(p[2]),
                    (float) w[2], v(p[3]), (float) w[3], (float) rest, 1.0f, d[0], d[1], d[2], d[3]);
            assertEquals(vol, got, 1e-5, "volume trial " + trial);
            for (int i = 0; i < 4; i++) {
                assertVec(e[i], d[i], 1e-4 * (1 + Math.abs(vol - rest)), "d" + i + " trial " + trial);
            }
        }
    }

    @Test
    void volumeStepMovesTowardRestVolume() {
        Vector3f p0 = new Vector3f(0, 0, 0), p1 = new Vector3f(1, 0, 0), p2 = new Vector3f(0, 1, 0), p3 = new Vector3f(0, 0, 1);
        Vector3f d0 = new Vector3f(), d1 = new Vector3f(), d2 = new Vector3f(), d3 = new Vector3f();
        float target = 1.0f / 3.0f;
        float before = PbdConstraintsf.volumeConstraint(p0, p1, p2, p3, target, 1.0f, d0, d1, d2, d3);
        float after = PbdConstraintsf.volumeConstraint(p0.add(d0, new Vector3f()), p1.add(d1, new Vector3f()),
                p2.add(d2, new Vector3f()), p3.add(d3, new Vector3f()), target, 1.0f,
                new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f());
        // One linearised step on a cubic constraint overshoots a 2x change slightly; it must move toward the
        // target and at least halve the error. The old code moved the wrong way (0.167 -> 0.097).
        assertTrue(after > before, before + " -> " + after);
        assertTrue(Math.abs(after - target) < 0.5f * Math.abs(before - target), before + " -> " + after);
        assertEquals(0.0f, d0.x + d1.x + d2.x + d3.x, 1e-6f);
        assertEquals(0.0f, d0.y + d1.y + d2.y + d3.y, 1e-6f);
        assertEquals(0.0f, d0.z + d1.z + d2.z + d3.z, 1e-6f);
    }

    private static double[] arr(Vector3f a) {
        return new double[] {a.x, a.y, a.z};
    }

    private static Vector3f[] restShape(Random r, int n) {
        // Deliberately anisotropic and not axis-aligned, so its covariance is not diagonal.
        Matrix3f shear = new Matrix3f(1.0f, 0.4f, -0.3f, 0.2f, 0.6f, 0.5f, -0.1f, 0.3f, 1.7f);
        Vector3f[] q = new Vector3f[n];
        for (int i = 0; i < n; i++) {
            q[i] = shear.transform(new Vector3f(r.nextFloat() - 0.5f, r.nextFloat() - 0.5f, r.nextFloat() - 0.5f));
        }
        return q;
    }

    @Test
    void shapeMatchingRecoversRigidMotion() {
        Random r = new Random(3);
        float[] angles = {0.3f, 1.5f, 2.6f, 3.1f};
        for (float ang : angles) {
            int n = 12;
            Vector3f[] rest = restShape(r, n);
            Matrix3f rot = new Matrix3f().rotation(ang, new Vector3f(0.3f, -0.8f, 0.5f).normalize());
            Vector3f t = new Vector3f(2, -1, 0.5f);
            Vector3f[] cur = new Vector3f[n];
            Vector3f[] goal = new Vector3f[n];
            float[] m = new float[n];
            for (int i = 0; i < n; i++) {
                cur[i] = rot.transform(rest[i], new Vector3f()).add(t);
                goal[i] = new Vector3f();
                m[i] = 0.5f + r.nextFloat();
            }
            Matrix3f out = new Matrix3f();
            PbdConstraintsf.shapeMatchingGoal(cur, rest, m, out, goal);
            for (int i = 0; i < n; i++) {
                assertEquals(cur[i].x, goal[i].x, 2e-4f, "angle " + ang + " point " + i);
                assertEquals(cur[i].y, goal[i].y, 2e-4f, "angle " + ang + " point " + i);
                assertEquals(cur[i].z, goal[i].z, 2e-4f, "angle " + ang + " point " + i);
            }
            Vector3f probe = new Vector3f(0.2f, 0.7f, -0.4f);
            Vector3f a = out.transform(probe, new Vector3f()), b = rot.transform(probe, new Vector3f());
            assertTrue(a.distance(b) < 1e-4f, "rotation mismatch at angle " + ang + ": " + a + " vs " + b);
        }
    }

    @Test
    void shapeMatchingRotationIsPolarFactorUnderDeformation() {
        Random r = new Random(5);
        int n = 16;
        Vector3f[] rest = restShape(r, n);
        Matrix3f deform = new Matrix3f().rotation(1.2f, new Vector3f(1, 2, 3).normalize())
                .mul(new Matrix3f(1.3f, 0.1f, 0.0f, 0.2f, 0.8f, 0.1f, 0.0f, -0.15f, 1.1f));
        Vector3f[] cur = new Vector3f[n];
        Vector3f[] goal = new Vector3f[n];
        float[] m = new float[n];
        for (int i = 0; i < n; i++) {
            cur[i] = deform.transform(rest[i], new Vector3f());
            goal[i] = new Vector3f();
            m[i] = 1.0f;
        }
        Matrix3f out = new Matrix3f();
        PbdConstraintsf.shapeMatchingGoal(cur, rest, m, out, goal);
        // Reference: polar factor of A_pq by Higham's iteration X <- (X + X^-T) / 2, in double precision.
        double[][] a = new double[3][3];
        Vector3f cc = new Vector3f(), rc = new Vector3f();
        for (int i = 0; i < n; i++) {
            cc.add(cur[i]);
            rc.add(rest[i]);
        }
        cc.div(n);
        rc.div(n);
        for (int i = 0; i < n; i++) {
            double[] p = {cur[i].x - cc.x, cur[i].y - cc.y, cur[i].z - cc.z};
            double[] q = {rest[i].x - rc.x, rest[i].y - rc.y, rest[i].z - rc.z};
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    a[row][col] += p[row] * q[col];
                }
            }
        }
        double[][] x = a;
        for (int it = 0; it < 60; it++) {
            x = average(x, inverseTranspose(x));
        }
        assertEquals(1.0f, out.determinant(), 1e-4f);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                assertEquals(x[row][col], out.get(col, row), 2e-4, "R[" + row + "][" + col + "]");
            }
        }
    }

    private static double[][] average(double[][] a, double[][] b) {
        double[][] c = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                c[i][j] = 0.5 * (a[i][j] + b[i][j]);
            }
        }
        return c;
    }

    private static double[][] inverseTranspose(double[][] m) {
        double det = m[0][0] * (m[1][1] * m[2][2] - m[1][2] * m[2][1])
                - m[0][1] * (m[1][0] * m[2][2] - m[1][2] * m[2][0])
                + m[0][2] * (m[1][0] * m[2][1] - m[1][1] * m[2][0]);
        double[][] it = new double[3][3];
        // (M^-1)^T = cofactor(M) / det
        it[0][0] = (m[1][1] * m[2][2] - m[1][2] * m[2][1]) / det;
        it[0][1] = -(m[1][0] * m[2][2] - m[1][2] * m[2][0]) / det;
        it[0][2] = (m[1][0] * m[2][1] - m[1][1] * m[2][0]) / det;
        it[1][0] = -(m[0][1] * m[2][2] - m[0][2] * m[2][1]) / det;
        it[1][1] = (m[0][0] * m[2][2] - m[0][2] * m[2][0]) / det;
        it[1][2] = -(m[0][0] * m[2][1] - m[0][1] * m[2][0]) / det;
        it[2][0] = (m[0][1] * m[1][2] - m[0][2] * m[1][1]) / det;
        it[2][1] = -(m[0][0] * m[1][2] - m[0][2] * m[1][0]) / det;
        it[2][2] = (m[0][0] * m[1][1] - m[0][1] * m[1][0]) / det;
        return it;
    }
}
