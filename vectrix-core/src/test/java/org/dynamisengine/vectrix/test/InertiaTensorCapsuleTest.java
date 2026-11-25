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

import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.physics.InertiaTensorf;
import org.junit.jupiter.api.Test;

/**
 * {@link InertiaTensorf#capsule} against direct numerical integration of a uniform-density capsule
 * (review finding M-8: the hemisphere parallel-axis term used h^2/2 instead of h^2/4, overestimating
 * I_xx/I_zz by up to ~38%). This function is used by the DynamisPhysics ODE4J backend.
 */
class InertiaTensorCapsuleTest {

    /** Midpoint-rule volume integration over the capsule's bounding box. Returns {Ixx, Iyy, Izz}. */
    private static double[] integrate(double mass, double r, double h, int n) {
        double halfY = h / 2 + r;
        double dx = 2 * r / n, dy = 2 * halfY / n, dz = 2 * r / n;
        double vol = 0, sxx = 0, syy = 0, szz = 0;
        for (int i = 0; i < n; i++) {
            double x = -r + (i + 0.5) * dx;
            for (int k = 0; k < n; k++) {
                double z = -r + (k + 0.5) * dz;
                double rho2 = x * x + z * z;
                if (rho2 > r * r) {
                    continue;
                }
                for (int j = 0; j < n; j++) {
                    double y = -halfY + (j + 0.5) * dy;
                    double ay = Math.abs(y) - h / 2;
                    if (ay > 0 && rho2 + ay * ay > r * r) {
                        continue; // outside the hemispherical cap
                    }
                    vol += 1;
                    sxx += y * y + z * z;
                    syy += x * x + z * z;
                    szz += x * x + y * y;
                }
            }
        }
        double pointMass = mass / vol;
        return new double[] {sxx * pointMass, syy * pointMass, szz * pointMass};
    }

    private static void check(float mass, float radius, float height) {
        Vector3f actual = InertiaTensorf.capsule(mass, radius, height, new Vector3f());
        double[] ref = integrate(mass, radius, height, 260);
        assertEquals(ref[0], actual.x, ref[0] * 5e-3, "Ixx r=" + radius + " h=" + height);
        assertEquals(ref[1], actual.y, ref[1] * 5e-3, "Iyy r=" + radius + " h=" + height);
        assertEquals(ref[2], actual.z, ref[2] * 5e-3, "Izz r=" + radius + " h=" + height);
    }

    @Test
    void matchesNumericalIntegration() {
        check(1.0f, 0.5f, 2.0f);   // the case from the review: old value 0.9156 vs 0.6656
        check(70.0f, 0.3f, 1.2f);  // character-sized capsule
        check(2.5f, 1.0f, 0.25f);  // nearly a sphere
        check(1.0f, 0.1f, 4.0f);   // long thin rod
    }

    @Test
    void zeroHeightIsASphere() {
        Vector3f s = InertiaTensorf.capsule(3.0f, 0.7f, 0.0f, new Vector3f());
        float expected = 0.4f * 3.0f * 0.7f * 0.7f;
        assertEquals(expected, s.x, 1e-6f);
        assertEquals(expected, s.y, 1e-6f);
        assertEquals(expected, s.z, 1e-6f);
    }
}
