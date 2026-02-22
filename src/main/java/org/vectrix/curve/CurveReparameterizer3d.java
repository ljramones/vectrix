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

package org.vectrix.curve;

import org.vectrix.core.Vector3d;
import org.vectrix.core.Vector3dc;
import org.vectrix.curve.vec3.Bezier3d;
import org.vectrix.curve.vec3.CatmullRom3d;
import org.vectrix.curve.vec3.Hermite3d;
import org.vectrix.curve.vec3.UniformBSpline3d;

public final class CurveReparameterizer3d {
    private CurveReparameterizer3d() {}

    public static double[] buildArcLengthTableForBezier(Vector3dc p0, Vector3dc p1, Vector3dc p2, Vector3dc p3, int samples, double[] dest) {
        checkSamples(samples);
        if (dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3d prev = Bezier3d.evaluate(p0, p1, p2, p3, 0.0, new Vector3d());
        dest[0] = 0.0;
        double acc = 0.0;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / (double) samples;
            Vector3d curr = Bezier3d.evaluate(p0, p1, p2, p3, t, new Vector3d());
            acc += prev.distance(curr);
            dest[i] = acc;
            prev.set(curr);
        }
        normalize(dest, samples, acc);
        return dest;
    }

    public static double[] buildArcLengthTable(UniformBSpline3d curve, int segmentIndex, int samples, double[] dest) {
        checkSamples(samples);
        if (dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3d prev = curve.evaluate(segmentIndex, 0.0, new Vector3d());
        dest[0] = 0.0;
        double acc = 0.0;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / (double) samples;
            Vector3d curr = curve.evaluate(segmentIndex, t, new Vector3d());
            acc += prev.distance(curr);
            dest[i] = acc;
            prev.set(curr);
        }
        normalize(dest, samples, acc);
        return dest;
    }

    public static double[] buildArcLengthTableForHermite(Vector3dc p0, Vector3dc m0, Vector3dc p1, Vector3dc m1, int samples, double[] dest) {
        checkSamples(samples);
        if (dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3d prev = Hermite3d.evaluate(p0, m0, p1, m1, 0.0, new Vector3d());
        dest[0] = 0.0;
        double acc = 0.0;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / (double) samples;
            Vector3d curr = Hermite3d.evaluate(p0, m0, p1, m1, t, new Vector3d());
            acc += prev.distance(curr);
            dest[i] = acc;
            prev.set(curr);
        }
        normalize(dest, samples, acc);
        return dest;
    }

    public static double[] buildArcLengthTableForCatmullRom(Vector3dc p0, Vector3dc p1, Vector3dc p2, Vector3dc p3, double tension,
        int samples, double[] dest) {
        checkSamples(samples);
        if (dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3d prev = CatmullRom3d.evaluate(p0, p1, p2, p3, 0.0, tension, new Vector3d());
        dest[0] = 0.0;
        double acc = 0.0;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / (double) samples;
            Vector3d curr = CatmullRom3d.evaluate(p0, p1, p2, p3, t, tension, new Vector3d());
            acc += prev.distance(curr);
            dest[i] = acc;
            prev.set(curr);
        }
        normalize(dest, samples, acc);
        return dest;
    }

    public static double mapArcLengthToT(double s01, double[] arcLengthTable) {
        if (s01 <= 0.0) return 0.0;
        if (s01 >= 1.0) return 1.0;
        int hi = arcLengthTable.length - 1;
        int lo = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            double v = arcLengthTable[mid];
            if (v < s01) lo = mid + 1; else hi = mid - 1;
        }
        int i1 = Math.min(lo, arcLengthTable.length - 1);
        int i0 = Math.max(0, i1 - 1);
        double a0 = arcLengthTable[i0];
        double a1 = arcLengthTable[i1];
        if (a1 <= a0) return (double) i0 / (double) (arcLengthTable.length - 1);
        double alpha = (s01 - a0) / (a1 - a0);
        return ((double) i0 + alpha) / (double) (arcLengthTable.length - 1);
    }

    public static Vector3d evaluateByArcLength(UniformBSpline3d curve, int segmentIndex, double s01, double[] arcLengthTable, Vector3d dest) {
        double t = mapArcLengthToT(s01, arcLengthTable);
        return curve.evaluate(segmentIndex, t, dest);
    }

    private static void checkSamples(int samples) { if (samples <= 0) throw new IllegalArgumentException("samples"); }
    private static void normalize(double[] dest, int samples, double total) {
        if (total <= 0.0) {
            for (int i = 0; i <= samples; i++) dest[i] = (double) i / (double) samples;
            return;
        }
        for (int i = 1; i <= samples; i++) dest[i] /= total;
    }
}
