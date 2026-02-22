/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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
package org.vectrix.optics;

/**
 * Refractive-index utility helpers (double-precision).
 */
public final class Iord {
    private Iord() {
    }

    public static double etaRatio(double etaI, double etaT) {
        if (etaI <= 0.0 || etaT <= 0.0) {
            throw new IllegalArgumentException("eta");
        }
        return etaI / etaT;
    }

    /**
     * Schlick F0 from incident and transmitted IORs.
     */
    public static double schlickF0(double etaI, double etaT) {
        if (etaI <= 0.0 || etaT <= 0.0) {
            throw new IllegalArgumentException("eta");
        }
        double r = (etaI - etaT) / (etaI + etaT);
        return r * r;
    }

    /**
     * Schlick F0 from eta ratio (etaI/etaT).
     */
    public static double schlickF0FromEtaRatio(double etaRatio) {
        if (etaRatio <= 0.0) {
            throw new IllegalArgumentException("etaRatio");
        }
        double r = (etaRatio - 1.0) / (etaRatio + 1.0);
        return r * r;
    }
}
