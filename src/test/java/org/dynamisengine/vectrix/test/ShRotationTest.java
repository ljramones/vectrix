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

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.core.Matrix3d;
import org.dynamisengine.vectrix.core.Matrix3f;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.sh.ShBasis;
import org.dynamisengine.vectrix.sh.ShCoeffs16d;
import org.dynamisengine.vectrix.sh.ShCoeffs16f;
import org.dynamisengine.vectrix.sh.ShCoeffs9f;
import org.dynamisengine.vectrix.sh.ShRotation;
import org.junit.jupiter.api.Test;

/**
 * SH rotation: the defining property g(R u) = f(u) at random directions, orthogonality of every band block,
 * composition, the band-1 block against the rotation matrix itself, and aliasing.
 */
class ShRotationTest {

    private static Matrix3d randomRotation(SplittableRandom rnd) {
        return new Matrix3d().rotateXYZ(rnd.nextDouble(-Math.PI, Math.PI), rnd.nextDouble(-Math.PI, Math.PI),
                rnd.nextDouble(-Math.PI, Math.PI));
    }

    private static Vector3d randomDir(SplittableRandom rnd) {
        Vector3d v;
        do {
            v = new Vector3d(rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1), rnd.nextDouble(-1, 1));
        } while (v.lengthSquared() > 1 || v.lengthSquared() < 1e-4);
        return v.normalize();
    }

    private static double eval(double[] c, int channel, Vector3d d) {
        double[] y = ShBasis.evaluateL3(d.x, d.y, d.z, new double[16]);
        double sum = 0;
        for (int i = 0; i < c.length / 3; i++) {
            sum += c[3 * i + channel] * y[i];
        }
        return sum;
    }

    @Test
    void rotatedFunctionMatchesTheOriginalAtTheRotatedDirection() {
        SplittableRandom rnd = new SplittableRandom(1);
        for (int t = 0; t < 200; t++) {
            Matrix3d r = randomRotation(rnd);
            ShCoeffs16d f = new ShCoeffs16d();
            for (int i = 0; i < f.c.length; i++) {
                f.c[i] = rnd.nextDouble(-1, 1);
            }
            ShCoeffs16d g = ShRotation.rotateL3(f, r, new ShCoeffs16d());
            for (int k = 0; k < 5; k++) {
                Vector3d u = randomDir(rnd);
                Vector3d ru = r.transform(new Vector3d(u));
                for (int ch = 0; ch < 3; ch++) {
                    assertEquals(eval(f.c, ch, u), eval(g.c, ch, ru), 1e-11, "trial " + t);
                }
            }
            // float L2 agrees with the double L3 result on the first nine bands
            Matrix3f rf = new Matrix3f((float) r.m00(), (float) r.m01(), (float) r.m02(), (float) r.m10(), (float) r.m11(),
                    (float) r.m12(), (float) r.m20(), (float) r.m21(), (float) r.m22());
            ShCoeffs9f f9 = new ShCoeffs9f();
            for (int i = 0; i < f9.c.length; i++) {
                f9.c[i] = (float) f.c[i];
            }
            ShRotation.rotateL2(f9, rf, f9); // aliased
            for (int i = 0; i < f9.c.length; i++) {
                assertEquals(g.c[i], f9.c[i], 2e-5, "float L2 coefficient " + i);
            }
            ShCoeffs16f f16 = new ShCoeffs16f();
            for (int i = 0; i < f16.c.length; i++) {
                f16.c[i] = (float) f.c[i];
            }
            ShRotation.rotateL3(f16, rf, f16);
            for (int i = 0; i < f16.c.length; i++) {
                assertEquals(g.c[i], f16.c[i], 3e-5, "float L3 coefficient " + i);
            }
        }
    }

    @Test
    void bandBlocksAreOrthogonalAndCompose() {
        SplittableRandom rnd = new SplittableRandom(2);
        double[] identity = ShRotation.rotationMatrixL3(new Matrix3d(), new double[ShRotation.L3_MATRIX_SIZE]);
        int off = 0;
        for (int l = 1; l <= 3; l++) {
            int n = 2 * l + 1;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    assertEquals(i == j ? 1 : 0, identity[off + i * n + j], 1e-12, "identity band " + l);
                }
            }
            off += n * n;
        }
        for (int t = 0; t < 100; t++) {
            Matrix3d a = randomRotation(rnd), b = randomRotation(rnd);
            double[] ma = ShRotation.rotationMatrixL3(a, new double[ShRotation.L3_MATRIX_SIZE]);
            double[] mb = ShRotation.rotationMatrixL3(b, new double[ShRotation.L3_MATRIX_SIZE]);
            double[] mab = ShRotation.rotationMatrixL3(new Matrix3d(a).mul(b), new double[ShRotation.L3_MATRIX_SIZE]);
            off = 0;
            for (int l = 1; l <= 3; l++) {
                int n = 2 * l + 1;
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        double dot = 0, prod = 0;
                        for (int k = 0; k < n; k++) {
                            dot += ma[off + i * n + k] * ma[off + j * n + k];
                            prod += ma[off + i * n + k] * mb[off + k * n + j];
                        }
                        assertEquals(i == j ? 1 : 0, dot, 1e-11, "orthogonality, band " + l);
                        assertEquals(mab[off + i * n + j], prod, 1e-11, "composition, band " + l);
                    }
                }
                off += n * n;
            }
            // band 1 is the rotation itself in the (y, z, x) order of the standard real basis
            int[] axis = {1, 2, 0};
            double[] rm = {a.m00(), a.m10(), a.m20(), a.m01(), a.m11(), a.m21(), a.m02(), a.m12(), a.m22()};
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    assertEquals(rm[axis[i] * 3 + axis[j]], ma[i * 3 + j], 1e-12, "band 1");
                }
            }
        }
    }
}
