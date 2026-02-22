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

import org.vectrix.core.Vector3f;
import org.vectrix.core.Vector3fc;
import org.vectrix.curve.vec3.Bezier3f;
import org.vectrix.curve.vec3.CatmullRom3f;
import org.vectrix.curve.vec3.Hermite3f;
import org.vectrix.curve.vec3.UniformBSpline3f;

public final class CurveReparameterizer3f {
    private CurveReparameterizer3f() {}

    public static float[] buildArcLengthTableForBezier(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3, int samples, float[] dest) {
        checkSamples(samples);
        if (dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3f prev = Bezier3f.evaluate(p0, p1, p2, p3, 0.0f, new Vector3f());
        dest[0] = 0.0f;
        float acc = 0.0f;
        for (int i = 1; i <= samples; i++) {
            float t = (float) i / (float) samples;
            Vector3f curr = Bezier3f.evaluate(p0, p1, p2, p3, t, new Vector3f());
            acc += prev.distance(curr);
            dest[i] = acc;
            prev.set(curr);
        }
        normalize(dest, samples, acc);
        return dest;
    }

    public static float[] buildArcLengthTable(UniformBSpline3f curve, int segmentIndex, int samples, float[] dest) {
        checkSamples(samples);
        if (dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3f prev = curve.evaluate(segmentIndex, 0.0f, new Vector3f());
        dest[0] = 0.0f;
        float acc = 0.0f;
        for (int i = 1; i <= samples; i++) {
            float t = (float) i / (float) samples;
            Vector3f curr = curve.evaluate(segmentIndex, t, new Vector3f());
            acc += prev.distance(curr);
            dest[i] = acc;
            prev.set(curr);
        }
        normalize(dest, samples, acc);
        return dest;
    }

    public static float[] buildArcLengthTableForHermite(Vector3fc p0, Vector3fc m0, Vector3fc p1, Vector3fc m1, int samples, float[] dest) {
        checkSamples(samples);
        if (dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3f prev = Hermite3f.evaluate(p0, m0, p1, m1, 0.0f, new Vector3f());
        dest[0] = 0.0f;
        float acc = 0.0f;
        for (int i = 1; i <= samples; i++) {
            float t = (float) i / (float) samples;
            Vector3f curr = Hermite3f.evaluate(p0, m0, p1, m1, t, new Vector3f());
            acc += prev.distance(curr);
            dest[i] = acc;
            prev.set(curr);
        }
        normalize(dest, samples, acc);
        return dest;
    }

    public static float[] buildArcLengthTableForCatmullRom(Vector3fc p0, Vector3fc p1, Vector3fc p2, Vector3fc p3, float tension,
        int samples, float[] dest) {
        checkSamples(samples);
        if (dest.length < samples + 1) throw new IllegalArgumentException("dest");
        Vector3f prev = CatmullRom3f.evaluate(p0, p1, p2, p3, 0.0f, tension, new Vector3f());
        dest[0] = 0.0f;
        float acc = 0.0f;
        for (int i = 1; i <= samples; i++) {
            float t = (float) i / (float) samples;
            Vector3f curr = CatmullRom3f.evaluate(p0, p1, p2, p3, t, tension, new Vector3f());
            acc += prev.distance(curr);
            dest[i] = acc;
            prev.set(curr);
        }
        normalize(dest, samples, acc);
        return dest;
    }

    public static float mapArcLengthToT(float s01, float[] arcLengthTable) {
        if (s01 <= 0.0f) return 0.0f;
        if (s01 >= 1.0f) return 1.0f;
        int hi = arcLengthTable.length - 1;
        int lo = 0;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            float v = arcLengthTable[mid];
            if (v < s01) lo = mid + 1; else hi = mid - 1;
        }
        int i1 = Math.min(lo, arcLengthTable.length - 1);
        int i0 = Math.max(0, i1 - 1);
        float a0 = arcLengthTable[i0];
        float a1 = arcLengthTable[i1];
        if (a1 <= a0) return (float) i0 / (float) (arcLengthTable.length - 1);
        float alpha = (s01 - a0) / (a1 - a0);
        return ((float) i0 + alpha) / (float) (arcLengthTable.length - 1);
    }

    public static Vector3f evaluateByArcLength(UniformBSpline3f curve, int segmentIndex, float s01, float[] arcLengthTable, Vector3f dest) {
        float t = mapArcLengthToT(s01, arcLengthTable);
        return curve.evaluate(segmentIndex, t, dest);
    }

    private static void checkSamples(int samples) { if (samples <= 0) throw new IllegalArgumentException("samples"); }
    private static void normalize(float[] dest, int samples, float total) {
        if (total <= 0.0f) {
            for (int i = 0; i <= samples; i++) dest[i] = (float) i / (float) samples;
            return;
        }
        for (int i = 1; i <= samples; i++) dest[i] /= total;
    }
}
