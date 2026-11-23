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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.affine.TransformKernels;
import org.dynamisengine.vectrix.core.CoreConfig;
import org.dynamisengine.vectrix.core.MathMode;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.soa.TransformSoA;
import org.junit.jupiter.api.Test;

/**
 * The single {@link MathMode#STRICT} contract (review findings M-10, M-11): validate inputs, never repair them,
 * keep FAST scalar results bit for bit, and reduce accurately in a fixed order.
 */
class StrictModeContractTest {

    private interface Body {
        void run();
    }

    private static void strict(Body body) {
        MathMode prev = CoreConfig.mathMode();
        try {
            CoreConfig.setMathMode(MathMode.STRICT);
            body.run();
        } finally {
            CoreConfig.setMathMode(prev);
        }
    }

    private static TransformSoA randomTransforms(SplittableRandom rnd, int n) {
        TransformSoA t = new TransformSoA(n);
        for (int i = 0; i < n; i++) {
            Quaternionf q = new Quaternionf().rotationXYZ((float) rnd.nextDouble(-3, 3), (float) rnd.nextDouble(-3, 3),
                    (float) rnd.nextDouble(-3, 3));
            t.qx[i] = q.x;
            t.qy[i] = q.y;
            t.qz[i] = q.z;
            t.qw[i] = q.w;
            t.tx[i] = (float) rnd.nextDouble(-10, 10);
            t.ty[i] = (float) rnd.nextDouble(-10, 10);
            t.tz[i] = (float) rnd.nextDouble(-10, 10);
            t.sx[i] = (float) rnd.nextDouble(0.5, 2);
            t.sy[i] = (float) rnd.nextDouble(0.5, 2);
            t.sz[i] = (float) rnd.nextDouble(0.5, 2);
        }
        return t;
    }

    @Test
    void strictComposeIsBitwiseFastComposeForValidInput() {
        SplittableRandom rnd = new SplittableRandom(11);
        int n = 256;
        TransformSoA p = randomTransforms(rnd, n);
        TransformSoA l = randomTransforms(rnd, n);
        TransformSoA fast = new TransformSoA(n);
        TransformSoA strict = new TransformSoA(n);
        MathMode prev = CoreConfig.mathMode();
        try {
            CoreConfig.setMathMode(MathMode.FAST);
            TransformKernels.composeBatch(p, l, fast, n);
        } finally {
            CoreConfig.setMathMode(prev);
        }
        strict(() -> TransformKernels.composeBatch(p, l, strict, n));
        for (int i = 0; i < n; i++) {
            assertEquals(fast.qx[i], strict.qx[i], 0.0f, "qx " + i); // the old STRICT path renormalised
            assertEquals(fast.qy[i], strict.qy[i], 0.0f, "qy " + i);
            assertEquals(fast.qz[i], strict.qz[i], 0.0f, "qz " + i);
            assertEquals(fast.qw[i], strict.qw[i], 0.0f, "qw " + i);
            assertEquals(fast.tx[i], strict.tx[i], 0.0f, "tx " + i);
        }
    }

    @Test
    void strictComposeRejectsZeroAndNaNRotations() {
        // The old STRICT path silently replaced a zero product with identity.
        TransformSoA p = randomTransforms(new SplittableRandom(3), 2);
        TransformSoA l = randomTransforms(new SplittableRandom(4), 2);
        TransformSoA o = new TransformSoA(2);
        strict(() -> {
            l.qx[1] = 0;
            l.qy[1] = 0;
            l.qz[1] = 0;
            l.qw[1] = 0;
            assertThrows(IllegalArgumentException.class, () -> TransformKernels.composeBatch(p, l, o, 2));
            l.qw[1] = Float.NaN;
            assertThrows(IllegalArgumentException.class, () -> TransformKernels.composeBatch(p, l, o, 2));
        });
    }

    @Test
    void strictQuaternionValidationRejectsNaN() {
        // |NaN - 1| > tol is false, so NaN used to pass every STRICT unit check.
        Quaternionf nan = new Quaternionf(Float.NaN, 0, 0, 1);
        strict(() -> {
            assertThrows(IllegalArgumentException.class, () -> nan.log(new Quaternionf()));
            assertThrows(IllegalArgumentException.class, () -> new Quaternionf(0, 0, 0, Float.NaN).exp(new Quaternionf()));
            assertThrows(IllegalArgumentException.class, () -> Quaternionf.ensureConsistentHemisphere(new Quaternionf[] {new Quaternionf(), nan}));
        });
        assertTrue(!CoreConfig.withinStrictUnitTolerance(Float.NaN));
        assertTrue(!CoreConfig.withinStrictUnitTolerance(Double.NaN));
    }

    @Test
    void strictAcceptsChainedFloatRotations() {
        // Chaining float products drifts |q|^2 by more than the old 1e-6 tolerance, so STRICT used to reject the
        // output of an ordinary hierarchy. Find such a chain and check it is accepted.
        SplittableRandom rnd = new SplittableRandom(5);
        Quaternionf chained = null;
        for (int attempt = 0; attempt < 2000 && chained == null; attempt++) {
            Quaternionf q = new Quaternionf();
            for (int d = 0; d < 32; d++) {
                q.mul(new Quaternionf().rotationXYZ((float) rnd.nextDouble(-3, 3), (float) rnd.nextDouble(-3, 3),
                        (float) rnd.nextDouble(-3, 3)));
            }
            if (java.lang.Math.abs(q.lengthSquared() - 1.0f) > 1e-6f) {
                chained = q;
            }
        }
        assertTrue(chained != null, "expected float drift beyond 1e-6 within 2000 chains of 32 products");
        Quaternionf q = chained;
        strict(() -> assertDoesNotThrow(() -> q.log(new Quaternionf())));
        strict(() -> assertThrows(IllegalArgumentException.class, () -> new Quaternionf(0.1f, 0, 0, 1).log(new Quaternionf())));
    }
}
