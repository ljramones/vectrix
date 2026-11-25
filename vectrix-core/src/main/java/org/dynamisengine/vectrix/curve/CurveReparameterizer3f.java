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

import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;
import org.dynamisengine.vectrix.curve.vec3.Bezier3f;
import org.dynamisengine.vectrix.curve.vec3.CatmullRom3f;
import org.dynamisengine.vectrix.curve.vec3.Hermite3f;
import org.dynamisengine.vectrix.curve.vec3.UniformBSpline3f;

/**
 * Arc-length table builders and lookup helpers for one 3D curve segment, to move along a curve at constant speed.
 * <p>
 * Typical usage:
 * <ol>
 *   <li>Build a normalized cumulative arc-length table with a fixed sample count.</li>
 *   <li>Map the desired normalized arc length {@code s} in {@code [0, 1]} to the curve parameter {@code t}.</li>
 *   <li>Evaluate the original curve at the mapped {@code t}.</li>
 * </ol>
 * <p>
 * A table for {@code samples} samples has {@code samples + 1} entries: entry {@code i} is the length of the polyline
 * through the curve points at {@code t = k/samples}, {@code k <= i}, divided by the polyline's total length, so it
 * runs from 0 to 1. The polyline slightly underestimates the true length; more samples reduce the error. Each table
 * is normalized per segment, and each builder returns the segment's absolute (polyline) length. A segment whose
 * length is zero, infinite or NaN gets the uniform table {@code i/samples}. The table builders allocate two temporary
 * vectors per call; the lookups do not allocate. The functions are static and stateless.
 * <p>
 * To move at constant speed across several segments, build one table per segment, keep the lengths the builders
 * return, find the segment that contains the travelled distance from the running sum of those lengths, and map the
 * distance into it: {@code s01 = (distance - lengthBefore) / segmentLength}. {@link CurveReparameterizer3d} is the double twin.
 */
public final class CurveReparameterizer3f {
    private CurveReparameterizer3f() {}

    /**
     * Builds a normalized cumulative arc-length table for one cubic Bézier segment.
     *
     * @param p0 the start point
     * @param p1 the first control point
     * @param p2 the second control point
     * @param p3 the end point
     * @param samples the number of samples along the segment, at least 1 (see
     *     {@link CurveConstants#DEFAULT_ARCLEN_SAMPLES})
     * @param dest receives the table in {@code dest[0 .. samples]}; at least {@code samples + 1} long
     * @return the segment's absolute arc length (the length of the sampled polyline, before normalization)
     * @throws IllegalArgumentException if {@code samples < 1} or {@code dest} is too short
     * @since 1.0.0
     */
    public static float buildArcLengthTableForBezier(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3, int samples, float[] dest) {
        checkSamples(samples);
        if (dest == null || dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3f prev = Bezier3f.evaluate(p0, p1, p2, p3, 0.0f, new Vector3f());
        Vector3f curr = new Vector3f();
        dest[0] = 0.0f;
        float acc = 0.0f;
        for (int i = 1; i <= samples; i++) {
            float t = (float) i / (float) samples;
            Bezier3f.evaluate(p0, p1, p2, p3, t, curr);
            acc += prev.distance(curr);
            dest[i] = acc;
            Vector3f swap = prev;
            prev = curr;
            curr = swap;
        }
        normalize(dest, samples, acc);
        return acc;
    }

    /**
     * Builds a normalized cumulative arc-length table for one segment of a uniform B-spline.
     *
     * @param curve the spline
     * @param segmentIndex the segment, in {@code [0, curve.segmentCount())}
     * @param samples the number of samples along the segment, at least 1 (see
     *     {@link CurveConstants#DEFAULT_ARCLEN_SAMPLES})
     * @param dest receives the table in {@code dest[0 .. samples]}; at least {@code samples + 1} long
     * @return the segment's absolute arc length (the length of the sampled polyline, before normalization)
     * @throws IllegalArgumentException if {@code samples < 1} or {@code dest} is too short, or {@code segmentIndex} is
     *     out of range
     */
    public static float buildArcLengthTable(UniformBSpline3f curve, int segmentIndex, int samples, float[] dest) {
        checkSamples(samples);
        if (dest == null || dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3f prev = curve.evaluate(segmentIndex, 0.0f, new Vector3f());
        Vector3f curr = new Vector3f();
        dest[0] = 0.0f;
        float acc = 0.0f;
        for (int i = 1; i <= samples; i++) {
            float t = (float) i / (float) samples;
            curve.evaluate(segmentIndex, t, curr);
            acc += prev.distance(curr);
            dest[i] = acc;
            Vector3f swap = prev;
            prev = curr;
            curr = swap;
        }
        normalize(dest, samples, acc);
        return acc;
    }

    /**
     * Builds a normalized cumulative arc-length table for one cubic Hermite segment.
     *
     * @param p0 the start point
     * @param m0 the tangent at the start
     * @param p1 the end point
     * @param m1 the tangent at the end
     * @param samples the number of samples along the segment, at least 1 (see
     *     {@link CurveConstants#DEFAULT_ARCLEN_SAMPLES})
     * @param dest receives the table in {@code dest[0 .. samples]}; at least {@code samples + 1} long
     * @return the segment's absolute arc length (the length of the sampled polyline, before normalization)
     * @throws IllegalArgumentException if {@code samples < 1} or {@code dest} is too short
     * @since 1.0.0
     */
    public static float buildArcLengthTableForHermite(Vector3fc p0, Vector3fc m0, Vector3fc p1, Vector3fc m1, int samples, float[] dest) {
        checkSamples(samples);
        if (dest == null || dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3f prev = Hermite3f.evaluate(p0, m0, p1, m1, 0.0f, new Vector3f());
        Vector3f curr = new Vector3f();
        dest[0] = 0.0f;
        float acc = 0.0f;
        for (int i = 1; i <= samples; i++) {
            float t = (float) i / (float) samples;
            Hermite3f.evaluate(p0, m0, p1, m1, t, curr);
            acc += prev.distance(curr);
            dest[i] = acc;
            Vector3f swap = prev;
            prev = curr;
            curr = swap;
        }
        normalize(dest, samples, acc);
        return acc;
    }

    /**
     * Builds a normalized cumulative arc-length table for one Catmull–Rom (cardinal) segment, from {@code p1} to
     * {@code p2}.
     *
     * @param p0 the point before the segment
     * @param p1 the start point of the segment
     * @param p2 the end point of the segment
     * @param p3 the point after the segment
     * @param tension the cardinal tension: 0 for Catmull–Rom, 1 for zero tangents
     * @param samples the number of samples along the segment, at least 1 (see
     *     {@link CurveConstants#DEFAULT_ARCLEN_SAMPLES})
     * @param dest receives the table in {@code dest[0 .. samples]}; at least {@code samples + 1} long
     * @return the segment's absolute arc length (the length of the sampled polyline, before normalization)
     * @throws IllegalArgumentException if {@code samples < 1} or {@code dest} is too short
     * @since 1.0.0
     */
    public static float buildArcLengthTableForCatmullRom(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3, float tension,
        int samples, float[] dest) {
        checkSamples(samples);
        if (dest == null || dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3f prev = CatmullRom3f.evaluate(p0, p1, p2, p3, 0.0f, tension, new Vector3f());
        Vector3f curr = new Vector3f();
        dest[0] = 0.0f;
        float acc = 0.0f;
        for (int i = 1; i <= samples; i++) {
            float t = (float) i / (float) samples;
            CatmullRom3f.evaluate(p0, p1, p2, p3, t, tension, curr);
            acc += prev.distance(curr);
            dest[i] = acc;
            Vector3f swap = prev;
            prev = curr;
            curr = swap;
        }
        normalize(dest, samples, acc);
        return acc;
    }

    /**
     * Maps the normalized arc length {@code s01} to the curve parameter {@code t}, using a table built for
     * {@code arcLengthTable.length - 1} samples. If the table was built for fewer samples than its length allows (a
     * reused or oversized array), use {@link #mapArcLengthToT(float, float[], int)}.
     *
     * @param s01 the normalized arc length; values outside {@code [0, 1]} clamp, NaN propagates
     * @param arcLengthTable a table from one of the {@code buildArcLengthTable...} methods, filled to its last entry
     * @return the curve parameter, in {@code [0, 1]}
     * @throws IllegalArgumentException if the table is null or has fewer than 2 entries, or if it is not a normalized
     *     table of {@code arcLengthTable.length - 1} samples (its first entry is not 0 or its last entry is not 1),
     *     which is what a table built for fewer samples than the array holds looks like
     * @since 1.0.0
     */
    public static float mapArcLengthToT(float s01, float[] arcLengthTable) {
        if (arcLengthTable == null) throw new IllegalArgumentException("arcLengthTable");
        return mapArcLengthToT(s01, arcLengthTable, arcLengthTable.length - 1);
    }

    /**
     * Maps the normalized arc length {@code s01} to the curve parameter {@code t}, using entries {@code [0 .. samples]}
     * of a table built with the same {@code samples}: a binary search for the bracketing entries, then linear
     * interpolation between their parameters. Where the table is flat (a zero-length span), the lower parameter is
     * returned.
     *
     * @param s01 the normalized arc length; values outside {@code [0, 1]} clamp, NaN propagates
     * @param arcLengthTable the table, non-decreasing in {@code [0 .. samples]}
     * @param samples the sample count the table was built with
     * @return the curve parameter, in {@code [0, 1]}
     * @throws IllegalArgumentException if {@code samples < 1}, the table is null or has fewer than {@code samples + 1}
     *     entries, or entry 0 is not 0 or entry {@code samples} is not 1 (the table was not built for that sample
     *     count)
     */
    public static float mapArcLengthToT(float s01, float[] arcLengthTable, int samples) {
        if (arcLengthTable == null || samples <= 0 || samples > arcLengthTable.length - 1) {
            throw new IllegalArgumentException("samples");
        }
        if (arcLengthTable[0] != 0 || arcLengthTable[samples] != 1) {
            throw new IllegalArgumentException("arcLengthTable is not a normalized table of " + samples + " samples");
        }
        if (Float.isNaN(s01)) return Float.NaN;
        if (s01 <= 0) return 0;
        if (s01 >= 1) return 1;
        int hi = samples;
        int lo = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            float v = arcLengthTable[mid];
            if (v < s01) lo = mid + 1; else hi = mid - 1;
        }
        int i1 = Math.min(lo, samples);
        int i0 = Math.max(0, i1 - 1);
        float a0 = arcLengthTable[i0];
        float a1 = arcLengthTable[i1];
        if (a1 <= a0) return (float) i0 / (float) samples;
        float alpha = (s01 - a0) / (a1 - a0);
        return ((float) i0 + alpha) / (float) samples;
    }

    /**
     * Evaluates one segment of a uniform B-spline at the normalized arc length {@code s01}, using
     * {@link #mapArcLengthToT(float, float[])}. The table must be filled to its last entry, so its length must be the
     * sample count plus 1; a table built for fewer samples than the array holds is rejected.
     *
     * @param curve the spline
     * @param segmentIndex the segment the table was built for
     * @param s01 the normalized arc length; values outside {@code [0, 1]} clamp
     * @param arcLengthTable the table of that segment
     * @param dest receives the point
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code segmentIndex} is out of range, or the table has fewer than 2 entries
     *     or is not a normalized table of {@code arcLengthTable.length - 1} samples
     */
    public static Vector3f evaluateByArcLength(UniformBSpline3f curve, int segmentIndex, float s01, float[] arcLengthTable, Vector3f dest) {
        float t = mapArcLengthToT(s01, arcLengthTable);
        return curve.evaluate(segmentIndex, t, dest);
    }

    private static void checkSamples(int samples) { if (samples <= 0) throw new IllegalArgumentException("samples"); }
    private static void normalize(float[] dest, int samples, float total) {
        if (!(total > 0.0f && total < Float.POSITIVE_INFINITY)) {
            for (int i = 0; i <= samples; i++) dest[i] = (float) i / (float) samples;
            return;
        }
        for (int i = 1; i <= samples; i++) dest[i] /= total;
    }
}
