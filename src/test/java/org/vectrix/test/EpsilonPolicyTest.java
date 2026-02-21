/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.vectrix.core.Epsilond;
import org.vectrix.core.Epsilonf;

class EpsilonPolicyTest {
    @Test
    void floatEpsilonWorks() {
        assertTrue(Epsilonf.isZero(5E-7f));
        assertTrue(Epsilonf.equals(1.0f, 1.0f + 5E-6f));
        assertFalse(Epsilonf.equals(1.0f, 1.001f));
        assertTrue(Epsilonf.lessOrEqual(1.0f, 1.0f + 1E-7f));
        assertTrue(Epsilonf.greaterOrEqual(1.0f + 1E-7f, 1.0f));
    }

    @Test
    void doubleEpsilonWorks() {
        assertTrue(Epsilond.isZero(1E-13));
        assertTrue(Epsilond.equals(1.0, 1.0 + 1E-11));
        assertFalse(Epsilond.equals(1.0, 1.0001));
        assertTrue(Epsilond.lessOrEqual(2.0, 2.0 + 1E-12));
        assertTrue(Epsilond.greaterOrEqual(2.0 + 1E-12, 2.0));
    }
}
