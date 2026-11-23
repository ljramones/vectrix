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
 * Thin-lens physical camera (single precision): field of view and focal length, circle of confusion, hyperfocal
 * distance and depth-of-field limits. All lengths in one unit (metres recommended: a 50 mm lens is {@code 0.05});
 * {@code fNumber} is the f-stop N, so the aperture diameter is {@code focalLength / N}. Exposure from aperture,
 * shutter and ISO is in {@code color.Exposuref}.
 *
 * @since 2.3.0
 */
public final class PhysicalCameraf {
    private PhysicalCameraf() {
    }

    /**
     * Returns the field of view across a sensor dimension: {@code 2 atan(s / 2f)}.
     *
     * @param focalLength the focal length {@code f}, greater than 0
     * @param sensorSize the sensor width, height or diagonal {@code s}, in the unit of {@code focalLength}
     * @return the field of view in radians
     */
    public static float fieldOfView(float focalLength, float sensorSize) {
        return (float) (2.0 * AccurateMath.atan(0.5 * sensorSize / focalLength));
    }

    /**
     * Returns the focal length that gives field of view {@code fov} across {@code sensorSize}: the inverse of
     * {@link #fieldOfView}.
     *
     * @param fov the field of view in radians, in (0, π)
     * @param sensorSize the sensor width, height or diagonal
     * @return the focal length, in the unit of {@code sensorSize}
     */
    public static float focalLength(float fov, float sensorSize) {
        return (float) (0.5 * sensorSize / AccurateMath.tan(0.5 * fov));
    }

    /**
     * Diameter, on the sensor, of the circle of confusion of a point at {@code objectDistance} with the lens focused
     * at {@code focusDistance} (both greater than {@code focalLength}):
     * {@code c = (f / N) · |S2 − S1| / S2 · f / (S1 − f)}.
     *
     * @param focalLength the focal length {@code f}
     * @param fNumber the f-stop {@code N}, greater than 0
     * @param focusDistance the focus distance {@code S1}, greater than {@code focalLength}
     * @param objectDistance the distance {@code S2} of the point, greater than {@code focalLength}
     * @return the circle-of-confusion diameter on the sensor, in the unit of the inputs
     */
    public static float circleOfConfusion(float focalLength, float fNumber, float focusDistance,
            float objectDistance) {
        double f = focalLength, s1 = focusDistance, s2 = objectDistance;
        return (float) (f / fNumber * java.lang.Math.abs(s2 - s1) / s2 * f / (s1 - f));
    }

    /**
     * Converts a circle-of-confusion diameter on the sensor to pixels: {@code coc / sensorSize · imageSize}.
     *
     * @param coc the diameter on the sensor
     * @param sensorSize the sensor size along the image dimension, in the unit of {@code coc}
     * @param imageSize the image size in pixels along the same dimension
     * @return the diameter in pixels
     */
    public static float cocToPixels(float coc, float sensorSize, float imageSize) {
        return coc / sensorSize * imageSize;
    }

    /**
     * Returns the hyperfocal distance {@code H = f² / (N c) + f}: focused there, everything from {@code H / 2} to
     * infinity is acceptably sharp.
     *
     * @param focalLength the focal length {@code f}
     * @param fNumber the f-stop {@code N}, greater than 0
     * @param coc the largest acceptable circle of confusion {@code c} on the sensor, greater than 0
     * @return the hyperfocal distance, in the unit of the inputs
     */
    public static float hyperfocalDistance(float focalLength, float fNumber, float coc) {
        double f = focalLength;
        return (float) (f * f / (fNumber * (double) coc) + f);
    }

    /**
     * Returns the near limit of acceptable sharpness when focused at {@code s}: {@code s (H − f) / (H + s − 2f)},
     * where {@code H} is the {@link #hyperfocalDistance hyperfocal distance}.
     *
     * @param focalLength the focal length {@code f}
     * @param fNumber the f-stop, greater than 0
     * @param coc the largest acceptable circle of confusion on the sensor, greater than 0
     * @param s the focus distance, greater than {@code focalLength}
     * @return the nearest sharp distance
     */
    public static float depthOfFieldNear(float focalLength, float fNumber, float coc, float s) {
        double f = focalLength, h = hyperfocalDistanceD(focalLength, fNumber, coc);
        return (float) (s * (h - f) / (h + s - 2.0 * f));
    }

    /**
     * Returns the far limit of acceptable sharpness when focused at {@code s}: {@code s (H − f) / (H − s)}, where
     * {@code H} is the {@link #hyperfocalDistance hyperfocal distance}.
     *
     * @param focalLength the focal length {@code f}
     * @param fNumber the f-stop, greater than 0
     * @param coc the largest acceptable circle of confusion on the sensor, greater than 0
     * @param s the focus distance, greater than {@code focalLength}
     * @return the farthest sharp distance, or {@code +∞} if {@code s} is at or beyond {@code H}
     */
    public static float depthOfFieldFar(float focalLength, float fNumber, float coc, float s) {
        double f = focalLength, h = hyperfocalDistanceD(focalLength, fNumber, coc);
        return s >= h ? Float.POSITIVE_INFINITY : (float) (s * (h - f) / (h - s));
    }

    private static double hyperfocalDistanceD(double f, double n, double c) {
        return f * f / (n * c) + f;
    }
}
