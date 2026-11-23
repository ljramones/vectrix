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
package org.dynamisengine.vectrix.optics;

/**
 * Refractive-index utility helpers (double-precision).
  * @since 1.0.0
  */
public final class Iord {
    private Iord() {
    }

    /**
     * Returns the relative refractive index {@code etaI / etaT}.
     *
     * @param etaI the refractive index of the incident medium, greater than 0
     * @param etaT the refractive index of the transmitting medium, greater than 0
     * @return {@code etaI / etaT}
     * @throws IllegalArgumentException if {@code etaI} or {@code etaT} is not greater than 0 (NaN included)
     */
    public static double etaRatio(double etaI, double etaT) {
        if (!(etaI > 0.0) || !(etaT > 0.0)) {
            throw new IllegalArgumentException("eta");
        }
        return etaI / etaT;
    }

    /**
     * Returns the reflectance at normal incidence, {@code F0 = ((etaI − etaT) / (etaI + etaT))²}, as used by
     * Schlick's approximation.
     *
     * @param etaI the refractive index of the incident medium, greater than 0
     * @param etaT the refractive index of the transmitting medium, greater than 0
     * @return {@code F0}, in {@code [0, 1)}
     * @throws IllegalArgumentException if {@code etaI} or {@code etaT} is not greater than 0 (NaN included)
     */
    public static double schlickF0(double etaI, double etaT) {
        if (!(etaI > 0.0) || !(etaT > 0.0)) {
            throw new IllegalArgumentException("eta");
        }
        double r = (etaI - etaT) / (etaI + etaT);
        return r * r;
    }

    /**
     * Returns the reflectance at normal incidence from the relative index {@code η = etaI / etaT},
     * {@code F0 = ((η − 1) / (η + 1))²}.
     *
     * @param etaRatio the relative refractive index, greater than 0
     * @return {@code F0}, in {@code [0, 1)}
     * @throws IllegalArgumentException if {@code etaRatio} is not greater than 0 (NaN included)
     */
    public static double schlickF0FromEtaRatio(double etaRatio) {
        if (!(etaRatio > 0.0)) {
            throw new IllegalArgumentException("etaRatio");
        }
        double r = (etaRatio - 1.0) / (etaRatio + 1.0);
        return r * r;
    }
}
