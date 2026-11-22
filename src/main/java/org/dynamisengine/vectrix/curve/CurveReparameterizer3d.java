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

package org.dynamisengine.vectrix.curve;

import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3dc;
import org.dynamisengine.vectrix.curve.vec3.Bezier3d;
import org.dynamisengine.vectrix.curve.vec3.CatmullRom3d;
import org.dynamisengine.vectrix.curve.vec3.Hermite3d;
import org.dynamisengine.vectrix.curve.vec3.UniformBSpline3d;

/**
 * Arc-length table builders and lookup helpers for vec3 curve segments.
 * <p>
 * Typical usage:
 * <ol>
 *   <li>Build a normalized cumulative arc-length table with fixed sample count.</li>
 *   <li>Map desired normalized arc length {@code s in [0,1]} to parametric {@code t}.</li>
 *   <li>Evaluate the original curve at mapped {@code t}.</li>
 * </ol>
 */
public final class CurveReparameterizer3d {
    private CurveReparameterizer3d() {}

    /**
     * Build a normalized cumulative arc-length table for one cubic Bezier segment.
     *
     * @since 1.0.0
     */
    public static double[] buildArcLengthTableForBezier(Vector3dc p0, Vector3dc p1, Vector3dc p2, Vector3dc p3, int samples, double[] dest) {
        checkSamples(samples);
        if (dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3d prev = Bezier3d.evaluate(p0, p1, p2, p3, 0.0, new Vector3d());
        Vector3d curr = new Vector3d();
        dest[0] = 0.0;
        double acc = 0.0;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / (double) samples;
            Bezier3d.evaluate(p0, p1, p2, p3, t, curr);
            acc += prev.distance(curr);
            dest[i] = acc;
            Vector3d swap = prev;
            prev = curr;
            curr = swap;
        }
        normalize(dest, samples, acc);
        return dest;
    }

    public static double[] buildArcLengthTable(UniformBSpline3d curve, int segmentIndex, int samples, double[] dest) {
        checkSamples(samples);
        if (dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3d prev = curve.evaluate(segmentIndex, 0.0, new Vector3d());
        Vector3d curr = new Vector3d();
        dest[0] = 0.0;
        double acc = 0.0;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / (double) samples;
            curve.evaluate(segmentIndex, t, curr);
            acc += prev.distance(curr);
            dest[i] = acc;
            Vector3d swap = prev;
            prev = curr;
            curr = swap;
        }
        normalize(dest, samples, acc);
        return dest;
    }

    /**
     * Build a normalized cumulative arc-length table for one cubic Hermite segment.
     *
     * @since 1.0.0
     */
    public static double[] buildArcLengthTableForHermite(Vector3dc p0, Vector3dc m0, Vector3dc p1, Vector3dc m1, int samples, double[] dest) {
        checkSamples(samples);
        if (dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3d prev = Hermite3d.evaluate(p0, m0, p1, m1, 0.0, new Vector3d());
        Vector3d curr = new Vector3d();
        dest[0] = 0.0;
        double acc = 0.0;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / (double) samples;
            Hermite3d.evaluate(p0, m0, p1, m1, t, curr);
            acc += prev.distance(curr);
            dest[i] = acc;
            Vector3d swap = prev;
            prev = curr;
            curr = swap;
        }
        normalize(dest, samples, acc);
        return dest;
    }

    /**
     * Build a normalized cumulative arc-length table for one Catmull-Rom segment.
     *
     * @since 1.0.0
     */
    public static double[] buildArcLengthTableForCatmullRom(Vector3dc p0, Vector3dc p1, Vector3dc p2, Vector3dc p3, double tension,
        int samples, double[] dest) {
        checkSamples(samples);
        if (dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3d prev = CatmullRom3d.evaluate(p0, p1, p2, p3, 0.0, tension, new Vector3d());
        Vector3d curr = new Vector3d();
        dest[0] = 0.0;
        double acc = 0.0;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / (double) samples;
            CatmullRom3d.evaluate(p0, p1, p2, p3, t, tension, curr);
            acc += prev.distance(curr);
            dest[i] = acc;
            Vector3d swap = prev;
            prev = curr;
            curr = swap;
        }
        normalize(dest, samples, acc);
        return dest;
    }

    /**
     * Map normalized arc length to parametric curve value via table lookup and linear interpolation.
     *
     * @since 1.0.0
     */
    /**
     * Map normalised arc length {@code s01} in [0,1] to the curve parameter t, using a table built for
     * {@code arcLengthTable.length - 1} samples. If the table was built for fewer samples than its length
     * allows (a reused or oversized array), use {@link #mapArcLengthToT(double, double[], int)}.
     */
    public static double mapArcLengthToT(double s01, double[] arcLengthTable) {
        return mapArcLengthToT(s01, arcLengthTable, arcLengthTable.length - 1);
    }

    /**
     * Map normalised arc length {@code s01} to the curve parameter t, using entries {@code [0..samples]} of a
     * table built with the same {@code samples}. Values outside [0,1] clamp; NaN propagates.
     */
    public static double mapArcLengthToT(double s01, double[] arcLengthTable, int samples) {
        if (samples <= 0 || samples > arcLengthTable.length - 1) {
            throw new IllegalArgumentException("samples");
        }
        if (Double.isNaN(s01)) return Double.NaN;
        if (s01 <= 0) return 0;
        if (s01 >= 1) return 1;
        int hi = samples;
        int lo = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            double v = arcLengthTable[mid];
            if (v < s01) lo = mid + 1; else hi = mid - 1;
        }
        int i1 = Math.min(lo, samples);
        int i0 = Math.max(0, i1 - 1);
        double a0 = arcLengthTable[i0];
        double a1 = arcLengthTable[i1];
        if (a1 <= a0) return (double) i0 / (double) samples;
        double alpha = (s01 - a0) / (a1 - a0);
        return ((double) i0 + alpha) / (double) samples;
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
