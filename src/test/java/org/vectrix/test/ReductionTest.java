/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.vectrix.experimental.KernelConfig;
import org.vectrix.experimental.MathMode;
import org.vectrix.experimental.Reduction;

class ReductionTest {
    @Test
    void strictAndFastProduceStableResults() {
        float[] values = new float[1001];
        values[0] = 1.0f;
        for (int i = 1; i < values.length; i++) {
            values[i] = 1.0E-5f;
        }

        KernelConfig.setMathMode(MathMode.FAST);
        float fast = Reduction.sum(values);

        KernelConfig.setMathMode(MathMode.STRICT);
        float strict = Reduction.sum(values);

        assertTrue(java.lang.Math.abs(strict - 1.01f) <= java.lang.Math.abs(fast - 1.01f));

        KernelConfig.setMathMode(MathMode.FAST);
    }

    @Test
    void dotWorks() {
        float[] a = {1.0f, 2.0f, 3.0f};
        float[] b = {4.0f, 5.0f, 6.0f};
        KernelConfig.setMathMode(MathMode.FAST);
        assertEquals(32.0f, Reduction.dot(a, b), 0.0f);
        KernelConfig.setMathMode(MathMode.STRICT);
        assertEquals(32.0f, Reduction.dot(a, b), 0.0f);
        KernelConfig.setMathMode(MathMode.FAST);
    }
}
