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

import org.dynamisengine.vectrix.simd.SimdSupport;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;
import java.util.SplittableRandom;

import org.dynamisengine.vectrix.fft.BlockConvolverd;
import org.dynamisengine.vectrix.fft.BlockConvolverf;
import org.dynamisengine.vectrix.fft.ComplexFft2d;
import org.dynamisengine.vectrix.fft.ComplexFft2f;
import org.dynamisengine.vectrix.fft.ComplexFftd;
import org.dynamisengine.vectrix.fft.ComplexFftf;
import org.dynamisengine.vectrix.fft.FFT1d;
import org.dynamisengine.vectrix.fft.FFT1f;
import org.dynamisengine.vectrix.fft.RealFft2d;
import org.dynamisengine.vectrix.fft.RealFft2f;
import org.dynamisengine.vectrix.fft.RealFftd;
import org.dynamisengine.vectrix.fft.RealFftf;
import org.junit.jupiter.api.Test;

/** The FFT plans (2.5.0): exactness against a compensated DFT, twins, layouts, aliasing, streaming and contracts. */
class FftPlanTest {

    private static final int[] SIZES = {1, 2, 3, 4, 5, 6, 7, 8, 9, 12, 15, 16, 25, 30, 49, 60, 61, 64, 67, 97, 100,
        121, 128, 210, 243, 360, 1000, 1009, 1024, 2310};

    /** A DFT summed in double-double, with exactly reduced roots: correct to about one ulp of the result. */
    static double[] exactDft(double[] x, int n, boolean inverse) {
        double[] c = new double[n];
        double[] s = new double[n];
        for (int k = 0; k < n; k++) {
            int q = (int) ((4L * k) / n);
            double a = 2 * java.lang.Math.PI * k / n;
            c[k] = StrictMath.cos(a);
            s[k] = (inverse ? 1 : -1) * StrictMath.sin(a);
            if (4L * k == (long) q * n) { // exact quarter turns
                c[k] = new double[] {1, 0, -1, 0}[q];
                s[k] = (inverse ? 1 : -1) * new double[] {0, 1, 0, -1}[q];
            }
        }
        double[] out = new double[2 * n];
        for (int k = 0; k < n; k++) {
            double[] re = {0, 0};
            double[] im = {0, 0};
            for (int j = 0; j < n; j++) {
                int e = (int) ((long) j * k % n);
                add(re, x[2 * j], c[e]);
                add(re, -x[2 * j + 1], s[e]);
                add(im, x[2 * j], s[e]);
                add(im, x[2 * j + 1], c[e]);
            }
            out[2 * k] = re[0] + re[1];
            out[2 * k + 1] = im[0] + im[1];
        }
        return out;
    }

    private static void add(double[] acc, double a, double b) {
        double p = a * b;
        double pe = java.lang.Math.fma(a, b, -p);
        double s = acc[0] + p;
        double bb = s - acc[0];
        acc[1] += (acc[0] - (s - bb)) + (p - bb) + pe;
        acc[0] = s;
    }

    private static double relRms(double[] got, double[] want) {
        double num = 0;
        double den = 0;
        for (int i = 0; i < want.length; i++) {
            num += (got[i] - want[i]) * (got[i] - want[i]);
            den += want[i] * want[i];
        }
        return den == 0 ? java.lang.Math.sqrt(num) : java.lang.Math.sqrt(num / den);
    }

    private static double[] gaussian(SplittableRandom r, int len) {
        double[] a = new double[len];
        for (int i = 0; i < len; i++) {
            a[i] = r.nextDouble(-1, 1) + r.nextDouble(-1, 1);
        }
        return a;
    }

    private static float[] toFloat(double[] a) {
        float[] f = new float[a.length];
        for (int i = 0; i < a.length; i++) {
            f[i] = (float) a[i];
        }
        return f;
    }

    @Test
    void complexPlansMatchTheExactDft() {
        SplittableRandom r = new SplittableRandom(1);
        for (int n : SIZES) {
            double[] x = gaussian(r, 2 * n);
            ComplexFftd p = ComplexFftd.plan(n);
            assertEquals(n, p.size());
            // offsets and dirty scratch must not matter
            double[] d = new double[2 * n + 3];
            System.arraycopy(x, 0, d, 3, 2 * n);
            double[] s = new double[p.scratchSize() + 5];
            Arrays.fill(s, Double.NaN);
            p.forward(d, 3, s, 5);
            double[] y = Arrays.copyOfRange(d, 3, 3 + 2 * n);
            assertTrue(relRms(y, exactDft(x, n, false)) < 6e-16, n + ": " + relRms(y, exactDft(x, n, false)));
            double[] want = exactDft(x, n, true);
            for (int i = 0; i < want.length; i++) {
                want[i] /= n;
            }
            double[] z = x.clone();
            p.inverse(z, 0, s, 0);
            assertTrue(relRms(z, want) < 6e-16, n + " inverse: " + relRms(z, want));
            // float twin, against the same reference
            ComplexFftf pf = ComplexFftf.plan(n);
            float[] xf = toFloat(x);
            pf.forward(xf, 0, new float[pf.scratchSize()], 0);
            double[] exactF = exactDft(toDouble(toFloat(x)), n, false);
            assertTrue(relRms(toDouble(xf), exactF) < 2.5e-7, n + " float: " + relRms(toDouble(xf), exactF));
        }
    }

    private static double[] toDouble(float[] a) {
        double[] d = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            d[i] = a[i];
        }
        return d;
    }

    @Test
    void largeTransformsStayExact() {
        // a pure tone: one bin of height n, every other bin zero
        for (int n : new int[] {1 << 16, 3 * 5 * 4096, 65537}) {
            int k = n / 3;
            double[] d = new double[2 * n];
            for (int m = 0; m < n; m++) {
                double a = 2 * java.lang.Math.PI * ((long) k * m % n) / n;
                d[2 * m] = StrictMath.cos(a);
                d[2 * m + 1] = StrictMath.sin(a);
            }
            ComplexFftd p = ComplexFftd.plan(n);
            p.forward(d, 0, new double[p.scratchSize()], 0);
            double leak = 0;
            for (int i = 0; i < n; i++) {
                if (i != k) {
                    leak = java.lang.Math.max(leak, java.lang.Math.hypot(d[2 * i], d[2 * i + 1]));
                }
            }
            assertEquals(n, java.lang.Math.hypot(d[2 * k], d[2 * k + 1]), 1e-9 * n, "peak " + n);
            assertTrue(leak / n < 3e-16, n + " leakage " + leak / n);
        }
        // the legacy radix-2 API now uses the exact table (it leaked 1.1e-13 at 2^14)
        int n = 1 << 14;
        int k = 4321;
        double[] d = new double[2 * n];
        float[] f = new float[2 * n];
        for (int m = 0; m < n; m++) {
            double a = 2 * java.lang.Math.PI * ((long) k * m % n) / n;
            d[2 * m] = StrictMath.cos(a);
            d[2 * m + 1] = StrictMath.sin(a);
            f[2 * m] = (float) d[2 * m];
            f[2 * m + 1] = (float) d[2 * m + 1];
        }
        FFT1d.forward(d);
        FFT1f.forward(f);
        double leak = 0;
        double leakF = 0;
        for (int i = 0; i < n; i++) {
            if (i != k) {
                leak = java.lang.Math.max(leak, java.lang.Math.hypot(d[2 * i], d[2 * i + 1]));
                leakF = java.lang.Math.max(leakF, java.lang.Math.hypot(f[2 * i], f[2 * i + 1]));
            }
        }
        assertTrue(leak / n < 3e-16, "FFT1d leakage " + leak / n);
        assertTrue(leakF / n < 3e-7, "FFT1f leakage " + leakF / n);
    }

    @Test
    void realPlansMatchTheComplexTransform() {
        SplittableRandom r = new SplittableRandom(2);
        for (int n : SIZES) {
            double[] x = gaussian(r, n);
            double[] z = new double[2 * n];
            for (int i = 0; i < n; i++) {
                z[2 * i] = x[i];
            }
            double[] want = Arrays.copyOf(exactDft(z, n, false), 2 * (n / 2 + 1));
            RealFftd p = RealFftd.plan(n);
            assertEquals(2 * (n / 2 + 1), p.spectrumLength());
            double[] sp = new double[p.spectrumLength() + 1];
            double[] s = new double[p.scratchSize() + 2];
            p.forward(x, 0, sp, 1, s, 2);
            double[] got = Arrays.copyOfRange(sp, 1, sp.length);
            assertTrue(relRms(got, want) < 6e-16, n + ": " + relRms(got, want));
            double[] back = new double[n];
            p.inverse(got, 0, back, 0, s, 0);
            assertTrue(relRms(back, x) < 6e-16, n + " round trip: " + relRms(back, x));
            // in place, and through segments: identical bits
            double[] buf = Arrays.copyOf(x, p.spectrumLength());
            p.forward(buf, 0, buf, 0, s, 0);
            assertArrayEquals(got, buf, 0.0, "in place " + n);
            MemorySegment seg = Arena.ofAuto().allocate(8L * p.spectrumLength());
            MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_DOUBLE_UNALIGNED, 0, n);
            double[] ss = new double[(int) p.segmentScratchSize()];
            p.forward(seg, seg, ss);
            assertArrayEquals(got, seg.toArray(ValueLayout.JAVA_DOUBLE), 0.0, "segment " + n);
            p.inverse(seg, seg, ss);
            assertArrayEquals(back, Arrays.copyOf(seg.toArray(ValueLayout.JAVA_DOUBLE), n), 0.0, "segment inverse " + n);
            // float twin
            RealFftf pf = RealFftf.plan(n);
            float[] xf = toFloat(x);
            float[] spf = new float[pf.spectrumLength()];
            float[] sf = new float[pf.scratchSize()];
            pf.forward(xf, 0, spf, 0, sf, 0);
            assertTrue(relRms(toDouble(spf), want) < 2.5e-7, n + " float: " + relRms(toDouble(spf), want));
            float[] bf = new float[n];
            pf.inverse(spf, 0, bf, 0, sf, 0);
            assertTrue(relRms(toDouble(bf), toDouble(xf)) < 3e-7, n + " float round trip");
        }
    }

    @Test
    void segmentsMatchArrays() {
        SplittableRandom r = new SplittableRandom(3);
        for (int n : new int[] {1, 6, 97, 1024}) {
            double[] x = gaussian(r, 2 * n);
            ComplexFftd p = ComplexFftd.plan(n);
            double[] a = x.clone();
            p.forward(a, 0, new double[p.scratchSize()], 0);
            MemorySegment seg = Arena.ofAuto().allocate(16L * n + 8).asSlice(8); // unaligned for 16 bytes
            MemorySegment.copy(x, 0, seg, ValueLayout.JAVA_DOUBLE_UNALIGNED, 0, 2 * n);
            double[] ss = new double[(int) p.segmentScratchSize()];
            p.forward(seg, ss);
            assertArrayEquals(a, seg.toArray(ValueLayout.JAVA_DOUBLE), 0.0);
            p.inverse(seg, ss);
            p.inverse(a, 0, new double[p.scratchSize()], 0);
            assertArrayEquals(a, seg.toArray(ValueLayout.JAVA_DOUBLE), 0.0);
            ComplexFftf pf = ComplexFftf.plan(n);
            float[] xf = toFloat(x);
            MemorySegment sf = Arena.ofAuto().allocate(8L * n);
            MemorySegment.copy(xf, 0, sf, ValueLayout.JAVA_FLOAT_UNALIGNED, 0, 2 * n);
            pf.forward(xf, 0, new float[pf.scratchSize()], 0);
            pf.forward(sf, new float[(int) pf.segmentScratchSize()]);
            assertArrayEquals(xf, sf.toArray(ValueLayout.JAVA_FLOAT), 0f);
        }
    }

    @Test
    void twoDimensionalPlansAreSeparable() {
        SplittableRandom r = new SplittableRandom(4);
        for (int[] g : new int[][] {{1, 1}, {1, 7}, {5, 1}, {3, 4}, {17, 33}, {20, 61}}) {
            int rows = g[0];
            int cols = g[1];
            double[] x = gaussian(r, 2 * rows * cols);
            // reference: exact 1D DFT of every row, then of every column
            double[] ref = x.clone();
            for (int i = 0; i < rows; i++) {
                double[] row = exactDft(Arrays.copyOfRange(ref, 2 * i * cols, 2 * (i + 1) * cols), cols, false);
                System.arraycopy(row, 0, ref, 2 * i * cols, 2 * cols);
            }
            for (int c = 0; c < cols; c++) {
                double[] col = new double[2 * rows];
                for (int i = 0; i < rows; i++) {
                    col[2 * i] = ref[2 * (i * cols + c)];
                    col[2 * i + 1] = ref[2 * (i * cols + c) + 1];
                }
                col = exactDft(col, rows, false);
                for (int i = 0; i < rows; i++) {
                    ref[2 * (i * cols + c)] = col[2 * i];
                    ref[2 * (i * cols + c) + 1] = col[2 * i + 1];
                }
            }
            ComplexFft2d p = ComplexFft2d.plan(rows, cols);
            double[] y = x.clone();
            double[] s = new double[p.scratchSize()];
            p.forward(y, 0, s, 0);
            assertTrue(relRms(y, ref) < 1e-15, rows + "x" + cols + ": " + relRms(y, ref));
            p.inverse(y, 0, s, 0);
            assertTrue(relRms(y, x) < 1e-15, rows + "x" + cols + " round trip");
            ComplexFft2f pf = ComplexFft2f.plan(rows, cols);
            float[] yf = toFloat(x);
            pf.forward(yf, 0, new float[pf.scratchSize()], 0);
            assertTrue(relRms(toDouble(yf), ref) < 5e-7, rows + "x" + cols + " float");
            // real grid: the half spectrum is the first cols/2 + 1 columns of the complex transform
            double[] re = new double[rows * cols];
            double[] z = new double[2 * rows * cols];
            for (int i = 0; i < re.length; i++) {
                re[i] = x[2 * i];
                z[2 * i] = x[2 * i];
            }
            p.forward(z, 0, s, 0);
            RealFft2d rp = RealFft2d.plan(rows, cols);
            double[] sp = new double[rp.spectrumLength()];
            double[] rs = new double[rp.scratchSize()];
            rp.forward(re, 0, sp, 0, rs, 0);
            int h = cols / 2 + 1;
            double[] want = new double[sp.length];
            for (int i = 0; i < rows; i++) {
                System.arraycopy(z, 2 * i * cols, want, i * 2 * h, 2 * h);
            }
            assertTrue(relRms(sp, want) < 1e-15, rows + "x" + cols + " real");
            RealFft2f rf = RealFft2f.plan(rows, cols);
            float[] spf = new float[rf.spectrumLength()];
            rf.forward(toFloat(re), 0, spf, 0, new float[rf.scratchSize()], 0);
            assertTrue(relRms(toDouble(spf), want) < 5e-7, rows + "x" + cols + " real float");
            double[] back = new double[rows * cols];
            rp.inverse(sp, 0, back, 0, rs, 0);
            assertTrue(relRms(back, re) < 1e-15, rows + "x" + cols + " real round trip");
        }
    }

    @Test
    void blockConvolutionStreamsExactly() {
        SplittableRandom r = new SplittableRandom(5);
        for (int[] c : new int[][] {{1, 1}, {3, 16}, {16, 4}, {40, 1}, {100, 64}, {257, 256}, {1000, 37}}) {
            int len = c[0];
            int block = c[1];
            int total = 3000;
            double[] h = gaussian(r, len);
            double[] x = gaussian(r, total);
            double[] ref = new double[total + len - 1];
            for (int t = 0; t < ref.length; t++) {
                double[] acc = {0, 0};
                for (int j = java.lang.Math.max(0, t - total + 1); j <= java.lang.Math.min(len - 1, t); j++) {
                    add(acc, h[j], x[t - j]);
                }
                ref[t] = acc[0] + acc[1];
            }
            for (int mode = 0; mode < 2; mode++) {
                BlockConvolverd cv = BlockConvolverd.plan(h, 0, len, block);
                double[] st = new double[cv.stateSize()];
                double[] sc = new double[cv.scratchSize()];
                double[] y = new double[ref.length];
                int pos = 0;
                while (pos < total) {
                    int cnt = java.lang.Math.min(total - pos, mode == 0 ? block : 1 + r.nextInt(block));
                    if (mode == 0) {
                        cv.process(x, pos, cnt, y, pos, st, 0, sc, 0);
                    } else { // in place
                        System.arraycopy(x, pos, y, pos, cnt);
                        cv.process(y, pos, cnt, y, pos, st, 0, sc, 0);
                    }
                    pos += cnt;
                }
                cv.flush(y, total, st, 0);
                assertTrue(relRms(y, ref) < 1e-15, len + "/" + block + " fft=" + cv.usesFft() + ": " + relRms(y, ref));
                for (double v : st) {
                    assertEquals(0.0, v, 0.0, "flush clears the state");
                }
            }
            BlockConvolverf cf = BlockConvolverf.plan(toFloat(h), 0, len, block);
            float[] xf = toFloat(x);
            float[] yf = new float[ref.length];
            float[] stf = new float[cf.stateSize()];
            float[] scf = new float[cf.scratchSize()];
            for (int pos = 0; pos < total; pos += block) {
                cf.process(xf, pos, java.lang.Math.min(block, total - pos), yf, pos, stf, 0, scf, 0);
            }
            cf.flush(yf, total, stf, 0);
            assertTrue(relRms(toDouble(yf), ref) < 5e-7, len + "/" + block + " float");
        }
        assertFalse(BlockConvolverd.plan(new double[16], 0, 16, 1024).usesFft());
        assertTrue(BlockConvolverd.plan(new double[512], 0, 512, 512).usesFft());
    }

    @Test
    void simdAndScalarPathsGiveTheSameBits() {
        // In the simd test run the Vector API kernels are used when enabled; both paths must agree bit for bit.
        SplittableRandom r = new SplittableRandom(6);
        boolean was = org.dynamisengine.vectrix.simd.SimdSupport.isEnabled();
        try {
            for (int n : new int[] {2, 4, 8, 12, 16, 20, 45, 64, 96, 100, 128, 250, 256, 384, 1000, 1009, 4096, 7 * 64}) {
                double[] x = gaussian(r, 2 * n);
                ComplexFftd p = ComplexFftd.plan(n);
                ComplexFftf pf = ComplexFftf.plan(n);
                double[][] out = new double[2][];
                float[][] outF = new float[2][];
                for (int mode = 0; mode < 2; mode++) {
                    org.dynamisengine.vectrix.simd.SimdSupport.setEnabled(mode == 1);
                    double[] y = x.clone();
                    double[] s = new double[p.scratchSize()];
                    p.forward(y, 0, s, 0);
                    double[] z = y.clone();
                    p.inverse(z, 0, s, 0);
                    out[mode] = concat(y, z);
                    float[] yf = toFloat(x);
                    float[] sf = new float[pf.scratchSize()];
                    pf.forward(yf, 0, sf, 0);
                    float[] zf = yf.clone();
                    pf.inverse(zf, 0, sf, 0);
                    outF[mode] = concatF(yf, zf);
                }
                assertTrue(Arrays.equals(out[0], out[1]), "double, n = " + n);
                assertTrue(Arrays.equals(outF[0], outF[1]), "float, n = " + n);
            }
        } finally {
            org.dynamisengine.vectrix.simd.SimdSupport.setEnabled(was);
        }
    }

    private static double[] concat(double[] a, double[] b) {
        double[] c = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    private static float[] concatF(float[] a, float[] b) {
        float[] c = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    @Test
    void fastSizesAndContracts() {
        int[][] fast = {{1, 1}, {7, 8}, {11, 12}, {13, 15}, {17, 18}, {1001, 1024}, {1025, 1080}, {2049, 2160}};
        for (int[] f : fast) {
            assertEquals(f[1], ComplexFftd.fastSize(f[0]), "fastSize(" + f[0] + ")");
            assertEquals(f[1], ComplexFftf.fastSize(f[0]));
        }
        for (int m = 1; m < 3000; m++) {
            int v = ComplexFftd.fastSize(m);
            int w = v;
            for (int p : new int[] {2, 3, 5}) {
                while (w % p == 0) {
                    w /= p;
                }
            }
            assertTrue(v >= m && w == 1, "fastSize(" + m + ") = " + v);
            for (int u = m; u < v; u++) {
                int t = u;
                for (int p : new int[] {2, 3, 5}) {
                    while (t % p == 0) {
                        t /= p;
                    }
                }
                assertTrue(t != 1, "fastSize(" + m + ") skipped " + u);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> ComplexFftd.plan(0));
        assertThrows(IllegalArgumentException.class, () -> ComplexFftd.plan(ComplexFftd.MAX_SIZE + 1));
        ComplexFftd p = ComplexFftd.plan(8);
        assertThrows(IllegalArgumentException.class, () -> p.forward(new double[15], 0, new double[p.scratchSize()], 0));
        assertThrows(IllegalArgumentException.class, () -> p.forward(new double[16], 1, new double[p.scratchSize()], 0));
        assertThrows(IllegalArgumentException.class, () -> p.forward(new double[16], 0, new double[p.scratchSize() - 1], 0));
        BlockConvolverd b = BlockConvolverd.plan(new double[] {1, 2}, 0, 2, 4);
        assertThrows(IllegalArgumentException.class,
            () -> b.process(new double[5], 0, 5, new double[5], 0, new double[1], 0, new double[b.scratchSize()], 0));
        // the legacy API keeps its power-of-two contract
        assertThrows(IllegalArgumentException.class, () -> FFT1d.forward(new double[6]));
    }
}
