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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.nn.Loss;
import org.dynamisengine.vectrix.nn.NnMath;
import org.dynamisengine.vectrix.nn.Philox;
import org.dynamisengine.vectrix.nn.Rope;
import org.dynamisengine.vectrix.nn.Sampler;
import org.junit.jupiter.api.Test;

/** Philox, RoPE, cross-entropy and sampling (2.5.0). */
class NnBatch4Test {

    @Test
    void philoxMatchesTheKnownAnswersAndIsStateless() {
        int[] out = new int[4];
        Philox.block(0, 0, 0, 0, 0, 0, out, 0);
        assertArrayEquals(new int[] {0x6627e8d5, 0xe169c58d, 0xbc57ac4c, 0x9b00dbd8}, out);
        Philox.block(-1, -1, -1, -1, -1, -1, out, 0);
        assertArrayEquals(new int[] {0x408f276d, 0x41c83b0e, 0xa20bc7c6, 0x6d5451fd}, out);
        Philox.block(0x243f6a88, 0x85a308d3, 0x13198a2e, 0x03707344, 0xa4093822, 0x299f31d0, out, 0);
        assertArrayEquals(new int[] {0xd16cfe09, 0x94fdcceb, 0x5001e420, 0x24126ea1}, out);
        long seed = 0x0123456789abcdefL;
        for (long idx : new long[] {0, 1, 2, 3, 4, 77, 1L << 40}) {
            long ctr = idx >>> 2;
            Philox.block((int) ctr, (int) (ctr >>> 32), 0, 0, (int) seed, (int) (seed >>> 32), out, 0);
            assertEquals(out[(int) (idx & 3)], Philox.word(seed, idx));
        }
        // statistics: uniform mean, normal mean and variance
        int n = 1_000_000;
        float[] u = new float[n];
        Philox.fillUniform(7, 0, u, 0, n);
        double mean = 0;
        for (float v : u) {
            assertTrue(v >= 0f && v < 1f);
            mean += v;
        }
        assertEquals(0.5, mean / n, 2e-3);
        float[] z = new float[n];
        Philox.fillNormal(7, 0, 0f, 1f, z, 0, n);
        double m1 = 0;
        double m2 = 0;
        for (float v : z) {
            m1 += v;
            m2 += (double) v * v;
        }
        assertEquals(0.0, m1 / n, 4e-3);
        assertEquals(1.0, m2 / n, 6e-3);
        // any split of the work gives the same values
        float[] part = new float[1000];
        Philox.fillNormal(7, 123_457, 0f, 1f, part, 0, 1000);
        for (int i = 0; i < 1000; i++) {
            assertEquals(z[123_457 + i], part[i], 0f);
        }
        // dropout: about a fraction p dropped, the rest scaled; the same call drops the same elements
        float[] x = new float[n];
        java.util.Arrays.fill(x, 2f);
        float[] y = new float[n];
        Philox.dropout(99, 0, 0.25f, x, 0, y, 0, n);
        int dropped = 0;
        for (float v : y) {
            if (v == 0f) {
                dropped++;
            } else {
                assertEquals(2f / 0.75f, v, 0f);
            }
        }
        assertEquals(0.25, dropped / (double) n, 2e-3);
        float[] y2 = new float[n];
        Philox.dropout(99, 0, 0.25f, x, 0, y2, 0, n);
        assertArrayEquals(y, y2, 0f);
    }

    @Test
    void ropeRotatesExactlyAndEncodesRelativePosition() {
        int dim = 64;
        SplittableRandom r = new SplittableRandom(1);
        for (Rope.Layout layout : Rope.Layout.values()) {
            Rope rope = Rope.plan(dim, 10000, layout);
            float[] q = new float[dim];
            for (int i = 0; i < dim; i++) {
                q[i] = (float) r.nextDouble(-1, 1);
            }
            for (long pos : new long[] {0, 1, 17, 4096, 999_983, 1_000_000}) {
                float[] x = q.clone();
                rope.apply(x, 0, pos);
                for (int i = 0; i < dim / 2; i++) {
                    double th = StrictMath.pow(10000, -2.0 * i / dim);
                    double a = pos * th;
                    int i0 = layout == Rope.Layout.INTERLEAVED ? 2 * i : i;
                    int i1 = layout == Rope.Layout.INTERLEAVED ? 2 * i + 1 : i + dim / 2;
                    double want0 = q[i0] * StrictMath.cos(a) - q[i1] * StrictMath.sin(a);
                    double want1 = q[i0] * StrictMath.sin(a) + q[i1] * StrictMath.cos(a);
                    // the angle itself is only as good as pos·θ in double: about 1e-16 relative
                    double tol = 6e-8 + 4e-16 * java.lang.Math.abs(a);
                    assertEquals(want0, x[i0], tol, layout + " pos " + pos + " pair " + i);
                    assertEquals(want1, x[i1], tol);
                }
                // the inline sine and cosine are exactly NnMath.sinD/cosD
                for (int i = 0; i < dim / 2; i++) {
                    double a = pos * NnMath.powD(10000, -2.0 * i / dim);
                    int i0 = layout == Rope.Layout.INTERLEAVED ? 2 * i : i;
                    int i1 = layout == Rope.Layout.INTERLEAVED ? 2 * i + 1 : i + dim / 2;
                    double c = NnMath.cosD(a);
                    double sn = NnMath.sinD(a);
                    assertEquals((float) (q[i0] * c - q[i1] * sn), x[i0], 0f);
                    assertEquals((float) (q[i0] * sn + q[i1] * c), x[i1], 0f);
                }
                rope.applyBackward(x, 0, pos);
                for (int i = 0; i < dim; i++) {
                    assertEquals(q[i], x[i], 2e-7, "inverse rotation");
                }
            }
            // q·k after rotation depends only on the distance between positions
            float[] k = new float[dim];
            for (int i = 0; i < dim; i++) {
                k[i] = (float) r.nextDouble(-1, 1);
            }
            double ref = rotatedDot(rope, q, k, 10, 3);
            assertEquals(ref, rotatedDot(rope, q, k, 1007, 1000), 1e-5);
            assertEquals(ref, rotatedDot(rope, q, k, 500_007, 500_000), 1e-5);
        }
    }

    private static double rotatedDot(Rope rope, float[] q, float[] k, long pq, long pk) {
        float[] a = q.clone();
        float[] b = k.clone();
        rope.apply(a, 0, pq);
        rope.apply(b, 0, pk);
        double d = 0;
        for (int i = 0; i < a.length; i++) {
            d += (double) a[i] * b[i];
        }
        return d;
    }

    @Test
    void crossEntropyAndItsGradient() {
        SplittableRandom r = new SplittableRandom(2);
        int n = 300;
        float[] x = new float[n];
        for (int i = 0; i < n; i++) {
            x[i] = (float) r.nextDouble(-20, 20);
        }
        float[] scratch = new float[n];
        for (int t : new int[] {0, 17, 299}) {
            double m = Double.NEGATIVE_INFINITY;
            for (float v : x) {
                m = java.lang.Math.max(m, v);
            }
            double s = 0;
            for (float v : x) {
                s += StrictMath.exp(v - m);
            }
            double want = m + StrictMath.log(s) - x[t];
            assertEquals(want, Loss.crossEntropy(x, 0, n, t, scratch, 0), 4e-6 * (1 + java.lang.Math.abs(want)));
            float[] dx = new float[n];
            Loss.crossEntropyBackward(x, 0, n, t, 0.5f, dx, 0);
            for (int i = 0; i < n; i++) {
                double p = StrictMath.exp(x[i] - m) / s;
                assertEquals(0.5 * (p - (i == t ? 1 : 0)), dx[i], 1e-6);
            }
        }
    }

    @Test
    void samplingIsReproducibleAndRespectsItsFilters() {
        float[] logits = {1f, 3f, 3f, 0.5f, 2f, -1f};
        int n = logits.length;
        float[] probs = new float[n];
        int[] order = new int[n];
        assertEquals(1, Sampler.argmax(logits, 0, n), "first of equal maxima");
        assertEquals(1, Sampler.sample(logits, 0, n, 0f, 0, 1f, 0.9f, probs, 0, order, 0), "greedy");
        assertEquals(1, Sampler.sample(logits, 0, n, 1f, 1, 1f, 0.99f, probs, 0, order, 0), "top-1 is greedy");
        // frequencies over a uniform grid equal the renormalized kept probabilities
        int grid = 100_000;
        double[] e = new double[n];
        double z = 0;
        for (int i = 0; i < n; i++) {
            e[i] = StrictMath.exp(logits[i]);
            z += e[i];
        }
        int[] counts = new int[n];
        for (int g = 0; g < grid; g++) {
            counts[Sampler.sample(logits, 0, n, 1f, 0, 1f, (g + 0.5f) / grid, probs, 0, order, 0)]++;
        }
        for (int i = 0; i < n; i++) {
            assertEquals(e[i] / z, counts[i] / (double) grid, 2e-4, "token " + i);
        }
        // top-k = 3 keeps tokens 1, 2 (ties by index) and 4
        java.util.Arrays.fill(counts, 0);
        for (int g = 0; g < grid; g++) {
            counts[Sampler.sample(logits, 0, n, 1f, 3, 1f, (g + 0.5f) / grid, probs, 0, order, 0)]++;
        }
        double kept = e[1] + e[2] + e[4];
        assertEquals(0, counts[0] + counts[3] + counts[5]);
        assertEquals(e[4] / kept, counts[4] / (double) grid, 2e-4);
        // top-p = 0.6: tokens 1 and 2 hold about 0.66 of the mass, so only they remain
        java.util.Arrays.fill(counts, 0);
        for (int g = 0; g < grid; g++) {
            counts[Sampler.sample(logits, 0, n, 1f, 0, 0.6f, (g + 0.5f) / grid, probs, 0, order, 0)]++;
        }
        assertEquals(grid, counts[1] + counts[2]);
        assertEquals(0.5, counts[1] / (double) grid, 1e-4);
        // a large vocabulary: top-k selection agrees with a full sort
        SplittableRandom r = new SplittableRandom(3);
        int v = 50_000;
        float[] big = new float[v];
        for (int i = 0; i < v; i++) {
            big[i] = (float) r.nextGaussian();
        }
        float[] pb = new float[v];
        int[] ob = new int[v];
        int pick = Sampler.sample(big, 0, v, 0.8f, 40, 0.95f, 0.3f, pb, 0, ob, 0);
        Integer[] idx = new Integer[v];
        for (int i = 0; i < v; i++) {
            idx[i] = i;
        }
        java.util.Arrays.sort(idx, (a, b) -> big[a] != big[b] ? Float.compare(big[b], big[a]) : Integer.compare(a, b));
        boolean inTop = false;
        for (int i = 0; i < 40; i++) {
            inTop |= idx[i] == pick;
            assertEquals((int) idx[i], ob[i], "rank " + i);
        }
        assertTrue(inTop);
    }
}
