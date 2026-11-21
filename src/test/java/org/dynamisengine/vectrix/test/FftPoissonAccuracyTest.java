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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.fft.FFT1d;
import org.dynamisengine.vectrix.fft.FFT1f;
import org.dynamisengine.vectrix.sampling.PoissonSampling;
import org.junit.jupiter.api.Test;

/**
 * Review findings M-7 (float FFT twiddle recurrence lost about 3 digits) and M-9 (Poisson disk sampling violated
 * the minimum distance and crashed for radii below 1).
 */
class FftPoissonAccuracyTest {

    /** Relative L2 error of the float FFT against the double FFT of the same (float) input. */
    private static double fftRelativeError(int n, boolean inverse) {
        SplittableRandom rnd = new SplittableRandom(n);
        float[] f = new float[2 * n];
        double[] d = new double[2 * n];
        for (int i = 0; i < f.length; i++) {
            f[i] = (float) (rnd.nextDouble() * 2 - 1);
            d[i] = f[i];
        }
        if (inverse) {
            FFT1f.inverse(f);
            FFT1d.inverse(d);
        } else {
            FFT1f.forward(f);
            FFT1d.forward(d);
        }
        double err = 0, ref = 0;
        for (int i = 0; i < f.length; i++) {
            err += (f[i] - d[i]) * (f[i] - d[i]);
            ref += d[i] * d[i];
        }
        return java.lang.Math.sqrt(err / ref);
    }

    @Test
    void floatFftHasFloatAccuracyAtLargeSizes() {
        for (int n : new int[] {1024, 16384, 65536}) {
            double fwd = fftRelativeError(n, false);
            double inv = fftRelativeError(n, true);
            // Float butterflies alone give about 1e-7 * sqrt(log2 N); the old float recurrence gave 2.2e-4.
            assertTrue(fwd < 1e-6, "forward N=" + n + " rel err " + fwd);
            assertTrue(inv < 1e-6, "inverse N=" + n + " rel err " + inv);
        }
    }

    private static List<float[]> poisson(long seed, float radius, float minDist) {
        List<float[]> pts = new ArrayList<>();
        new PoissonSampling.Disk(seed, radius, minDist, 30, (x, y) -> pts.add(new float[] {x, y}));
        return pts;
    }

    @Test
    void poissonSamplesRespectTheMinimumDistance() {
        for (long seed = 0; seed < 8; seed++) {
            float minDist = 0.05f;
            List<float[]> pts = poisson(seed, 1.0f, minDist);
            assertTrue(pts.size() > 500, "seed " + seed + " produced only " + pts.size() + " samples");
            double min = Double.MAX_VALUE;
            for (int i = 0; i < pts.size(); i++) {
                for (int j = i + 1; j < pts.size(); j++) {
                    double dx = pts.get(i)[0] - pts.get(j)[0], dy = pts.get(i)[1] - pts.get(j)[1];
                    min = java.lang.Math.min(min, dx * dx + dy * dy);
                }
            }
            // was 0.0355 for minDist 0.05
            assertTrue(java.lang.Math.sqrt(min) >= minDist * (1 - 1e-6), "seed " + seed + " min pair " + java.lang.Math.sqrt(min));
        }
    }

    @Test
    void poissonWorksForSmallRadii() {
        for (long seed = 0; seed < 50; seed++) {
            float radius = 0.3f;
            List<float[]> pts = new ArrayList<>();
            long s = seed;
            // was ArrayIndexOutOfBoundsException in 36 of 50 seeds: the first sample was drawn in the unit disk
            assertDoesNotThrow(() -> pts.addAll(poisson(s, radius, 0.02f)), "seed " + seed);
            for (float[] p : pts) {
                assertTrue(p[0] * p[0] + p[1] * p[1] <= radius * radius * (1 + 1e-6f), "seed " + seed + " sample outside disk");
            }
            assertTrue(pts.size() > 100, "seed " + seed);
        }
    }
}
