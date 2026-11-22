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
package org.dynamisengine.vectrix.camera;

import org.dynamisengine.vectrix.core.AccurateMath;

/**
 * Thin-lens physical camera (double precision): field of view and focal length, circle of confusion, hyperfocal
 * distance and depth-of-field limits. All lengths in one unit (metres recommended: a 50 mm lens is {@code 0.05});
 * {@code fNumber} is the f-stop N, so the aperture diameter is {@code focalLength / N}. Exposure from aperture,
 * shutter and ISO is in {@code color.Exposured}.
 *
 * @since 2.3.0
 */
public final class PhysicalCamerad {
    private PhysicalCamerad() {
    }

    /** Field of view (radians) across a sensor dimension of {@code sensorSize}: {@code 2 atan(s / 2f)}. */
    public static double fieldOfView(double focalLength, double sensorSize) {
        return (2.0 * AccurateMath.atan(0.5 * sensorSize / focalLength));
    }

    /** Focal length giving field of view {@code fov} (radians) across {@code sensorSize}. */
    public static double focalLength(double fov, double sensorSize) {
        return (0.5 * sensorSize / AccurateMath.tan(0.5 * fov));
    }

    /**
     * Diameter, on the sensor, of the circle of confusion of a point at {@code objectDistance} with the lens focused
     * at {@code focusDistance} (both greater than {@code focalLength}):
     * {@code c = (f / N) · |S2 − S1| / S2 · f / (S1 − f)}.
     */
    public static double circleOfConfusion(double focalLength, double fNumber, double focusDistance,
            double objectDistance) {
        double f = focalLength, s1 = focusDistance, s2 = objectDistance;
        return (f / fNumber * java.lang.Math.abs(s2 - s1) / s2 * f / (s1 - f));
    }

    /** Converts a sensor-space circle-of-confusion diameter to pixels for an image {@code imageSize} pixels across. */
    public static double cocToPixels(double coc, double sensorSize, double imageSize) {
        return coc / sensorSize * imageSize;
    }

    /** Hyperfocal distance {@code H = f² / (N c) + f} for acceptable circle of confusion {@code coc}. */
    public static double hyperfocalDistance(double focalLength, double fNumber, double coc) {
        double f = focalLength;
        return (f * f / (fNumber * (double) coc) + f);
    }

    /** Near limit of acceptable sharpness when focused at {@code s}: {@code s (H − f) / (H + s − 2f)}. */
    public static double depthOfFieldNear(double focalLength, double fNumber, double coc, double s) {
        double f = focalLength, h = hyperfocalDistanceD(focalLength, fNumber, coc);
        return (s * (h - f) / (h + s - 2.0 * f));
    }

    /** Far limit of acceptable sharpness: {@code s (H − f) / (H − s)}, or {@code +∞} at or beyond {@code H}. */
    public static double depthOfFieldFar(double focalLength, double fNumber, double coc, double s) {
        double f = focalLength, h = hyperfocalDistanceD(focalLength, fNumber, coc);
        return s >= h ? Double.POSITIVE_INFINITY : (s * (h - f) / (h - s));
    }

    private static double hyperfocalDistanceD(double f, double n, double c) {
        return f * f / (n * c) + f;
    }
}
