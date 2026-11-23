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

/**
 * Scalar cubic curve kernels: Bézier, Hermite and uniform B-spline basis evaluation with first and second
 * derivatives, and the cardinal-spline tangent. The curve classes in {@code curve.scalar}, {@code curve.vec2},
 * {@code curve.vec3} and {@code curve.vec4} apply these per component.
 * <p>
 * Every function has a float and a double overload with the same formula. The parameter {@code t} is not clamped:
 * values outside {@code [0, 1]} extrapolate the cubic. Derivatives are with respect to {@code t}. The functions are
 * stateless and do not allocate.
 *
 * @since 1.0.0
 */
public final class CubicCurveMath {
    private CubicCurveMath() {
    }

    /**
     * Evaluates the cubic Bézier {@code (1-t)³·p0 + 3(1-t)²t·p1 + 3(1-t)t²·p2 + t³·p3}.
     *
     * @param p0 the start value
     * @param p1 the first control value
     * @param p2 the second control value
     * @param p3 the end value
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the value at {@code t}
     */
    public static float bezier(float p0, float p1, float p2, float p3, float t) {
        float u = 1.0f - t;
        float tt = t * t;
        float uu = u * u;
        return uu * u * p0 + 3.0f * uu * t * p1 + 3.0f * u * tt * p2 + tt * t * p3;
    }

    /**
     * Returns the first derivative of the cubic Bézier with respect to {@code t}.
     *
     * @param p0 the start value
     * @param p1 the first control value
     * @param p2 the second control value
     * @param p3 the end value
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the derivative at {@code t}
     */
    public static float bezierDerivative(float p0, float p1, float p2, float p3, float t) {
        float u = 1.0f - t;
        return 3.0f * u * u * (p1 - p0) + 6.0f * u * t * (p2 - p1) + 3.0f * t * t * (p3 - p2);
    }

    /**
     * Returns the second derivative of the cubic Bézier with respect to {@code t}.
     *
     * @param p0 the start value
     * @param p1 the first control value
     * @param p2 the second control value
     * @param p3 the end value
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the second derivative at {@code t}
     */
    public static float bezierSecondDerivative(float p0, float p1, float p2, float p3, float t) {
        float u = 1.0f - t;
        return 6.0f * u * (p2 - 2.0f * p1 + p0) + 6.0f * t * (p3 - 2.0f * p2 + p1);
    }

    /**
     * Evaluates the cubic Bézier {@code (1-t)³·p0 + 3(1-t)²t·p1 + 3(1-t)t²·p2 + t³·p3}.
     *
     * @param p0 the start value
     * @param p1 the first control value
     * @param p2 the second control value
     * @param p3 the end value
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the value at {@code t}
     */
    public static double bezier(double p0, double p1, double p2, double p3, double t) {
        double u = 1.0 - t;
        double tt = t * t;
        double uu = u * u;
        return uu * u * p0 + 3.0 * uu * t * p1 + 3.0 * u * tt * p2 + tt * t * p3;
    }

    /**
     * Returns the first derivative of the cubic Bézier with respect to {@code t}.
     *
     * @param p0 the start value
     * @param p1 the first control value
     * @param p2 the second control value
     * @param p3 the end value
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the derivative at {@code t}
     */
    public static double bezierDerivative(double p0, double p1, double p2, double p3, double t) {
        double u = 1.0 - t;
        return 3.0 * u * u * (p1 - p0) + 6.0 * u * t * (p2 - p1) + 3.0 * t * t * (p3 - p2);
    }

    /**
     * Returns the second derivative of the cubic Bézier with respect to {@code t}.
     *
     * @param p0 the start value
     * @param p1 the first control value
     * @param p2 the second control value
     * @param p3 the end value
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the second derivative at {@code t}
     */
    public static double bezierSecondDerivative(double p0, double p1, double p2, double p3, double t) {
        double u = 1.0 - t;
        return 6.0 * u * (p2 - 2.0 * p1 + p0) + 6.0 * t * (p3 - 2.0 * p2 + p1);
    }

    /**
     * Evaluates the cubic Hermite segment from {@code p0} with tangent {@code m0} to {@code p1} with tangent
     * {@code m1}.
     *
     * @param p0 the start value
     * @param m0 the tangent at the start
     * @param p1 the end value
     * @param m1 the tangent at the end
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the value at {@code t}
     */
    public static float hermite(float p0, float m0, float p1, float m1, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return (2.0f * t3 - 3.0f * t2 + 1.0f) * p0
                + (t3 - 2.0f * t2 + t) * m0
                + (-2.0f * t3 + 3.0f * t2) * p1
                + (t3 - t2) * m1;
    }

    /**
     * Returns the first derivative of the cubic Hermite segment with respect to {@code t}.
     *
     * @param p0 the start value
     * @param m0 the tangent at the start
     * @param p1 the end value
     * @param m1 the tangent at the end
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the derivative at {@code t}
     */
    public static float hermiteDerivative(float p0, float m0, float p1, float m1, float t) {
        float t2 = t * t;
        return (6.0f * t2 - 6.0f * t) * p0
                + (3.0f * t2 - 4.0f * t + 1.0f) * m0
                + (-6.0f * t2 + 6.0f * t) * p1
                + (3.0f * t2 - 2.0f * t) * m1;
    }

    /**
     * Returns the second derivative of the cubic Hermite segment with respect to {@code t}.
     *
     * @param p0 the start value
     * @param m0 the tangent at the start
     * @param p1 the end value
     * @param m1 the tangent at the end
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the second derivative at {@code t}
     */
    public static float hermiteSecondDerivative(float p0, float m0, float p1, float m1, float t) {
        return (12.0f * t - 6.0f) * p0
                + (6.0f * t - 4.0f) * m0
                + (-12.0f * t + 6.0f) * p1
                + (6.0f * t - 2.0f) * m1;
    }

    /**
     * Evaluates the cubic Hermite segment from {@code p0} with tangent {@code m0} to {@code p1} with tangent
     * {@code m1}.
     *
     * @param p0 the start value
     * @param m0 the tangent at the start
     * @param p1 the end value
     * @param m1 the tangent at the end
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the value at {@code t}
     */
    public static double hermite(double p0, double m0, double p1, double m1, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return (2.0 * t3 - 3.0 * t2 + 1.0) * p0
                + (t3 - 2.0 * t2 + t) * m0
                + (-2.0 * t3 + 3.0 * t2) * p1
                + (t3 - t2) * m1;
    }

    /**
     * Returns the first derivative of the cubic Hermite segment with respect to {@code t}.
     *
     * @param p0 the start value
     * @param m0 the tangent at the start
     * @param p1 the end value
     * @param m1 the tangent at the end
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the derivative at {@code t}
     */
    public static double hermiteDerivative(double p0, double m0, double p1, double m1, double t) {
        double t2 = t * t;
        return (6.0 * t2 - 6.0 * t) * p0
                + (3.0 * t2 - 4.0 * t + 1.0) * m0
                + (-6.0 * t2 + 6.0 * t) * p1
                + (3.0 * t2 - 2.0 * t) * m1;
    }

    /**
     * Returns the second derivative of the cubic Hermite segment with respect to {@code t}.
     *
     * @param p0 the start value
     * @param m0 the tangent at the start
     * @param p1 the end value
     * @param m1 the tangent at the end
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the second derivative at {@code t}
     */
    public static double hermiteSecondDerivative(double p0, double m0, double p1, double m1, double t) {
        return (12.0 * t - 6.0) * p0
                + (6.0 * t - 4.0) * m0
                + (-12.0 * t + 6.0) * p1
                + (6.0 * t - 2.0) * m1;
    }

    /**
     * Returns the cardinal-spline tangent at a point from its neighbours: {@code (1 - tension)·(next - prev)/2}.
     *
     * @param prev the value before the point
     * @param next the value after the point
     * @param tension 0 for the Catmull–Rom tangent, 1 for a zero tangent
     * @return the tangent, with respect to the segment parameter {@code t}
     */
    public static float cardinalTangent(float prev, float next, float tension) {
        return 0.5f * (1.0f - tension) * (next - prev);
    }

    /**
     * Returns the cardinal-spline tangent at a point from its neighbours: {@code (1 - tension)·(next - prev)/2}.
     *
     * @param prev the value before the point
     * @param next the value after the point
     * @param tension 0 for the Catmull–Rom tangent, 1 for a zero tangent
     * @return the tangent, with respect to the segment parameter {@code t}
     */
    public static double cardinalTangent(double prev, double next, double tension) {
        return 0.5 * (1.0 - tension) * (next - prev);
    }

    /**
     * Evaluates one segment of a uniform cubic B-spline from its four control values. The result is a weighted average
     * of the control values (the weights sum to 1 and, for {@code t} in {@code [0, 1]}, are non-negative), so the curve
     * does not, in general, pass through them.
     *
     * @param p0 the first control value of the segment
     * @param p1 the second control value
     * @param p2 the third control value
     * @param p3 the fourth control value
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the value at {@code t}
     */
    public static float bspline(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        float b0 = (-t3 + 3.0f * t2 - 3.0f * t + 1.0f) / 6.0f;
        float b1 = (3.0f * t3 - 6.0f * t2 + 4.0f) / 6.0f;
        float b2 = (-3.0f * t3 + 3.0f * t2 + 3.0f * t + 1.0f) / 6.0f;
        float b3 = t3 / 6.0f;
        return b0 * p0 + b1 * p1 + b2 * p2 + b3 * p3;
    }

    /**
     * Returns the first derivative of the uniform cubic B-spline segment with respect to {@code t}.
     *
     * @param p0 the first control value of the segment
     * @param p1 the second control value
     * @param p2 the third control value
     * @param p3 the fourth control value
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the derivative at {@code t}
     */
    public static float bsplineDerivative(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float b0 = (-3.0f * t2 + 6.0f * t - 3.0f) / 6.0f;
        float b1 = (9.0f * t2 - 12.0f * t) / 6.0f;
        float b2 = (-9.0f * t2 + 6.0f * t + 3.0f) / 6.0f;
        float b3 = (3.0f * t2) / 6.0f;
        return b0 * p0 + b1 * p1 + b2 * p2 + b3 * p3;
    }

    /**
     * Returns the second derivative of the uniform cubic B-spline segment with respect to {@code t}.
     *
     * @param p0 the first control value of the segment
     * @param p1 the second control value
     * @param p2 the third control value
     * @param p3 the fourth control value
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the second derivative at {@code t}
     */
    public static float bsplineSecondDerivative(float p0, float p1, float p2, float p3, float t) {
        float b0 = (-6.0f * t + 6.0f) / 6.0f;
        float b1 = (18.0f * t - 12.0f) / 6.0f;
        float b2 = (-18.0f * t + 6.0f) / 6.0f;
        float b3 = (6.0f * t) / 6.0f;
        return b0 * p0 + b1 * p1 + b2 * p2 + b3 * p3;
    }

    /**
     * Evaluates one segment of a uniform cubic B-spline from its four control values. The result is a weighted average
     * of the control values (the weights sum to 1 and, for {@code t} in {@code [0, 1]}, are non-negative), so the curve
     * does not, in general, pass through them.
     *
     * @param p0 the first control value of the segment
     * @param p1 the second control value
     * @param p2 the third control value
     * @param p3 the fourth control value
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the value at {@code t}
     */
    public static double bspline(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        double b0 = (-t3 + 3.0 * t2 - 3.0 * t + 1.0) / 6.0;
        double b1 = (3.0 * t3 - 6.0 * t2 + 4.0) / 6.0;
        double b2 = (-3.0 * t3 + 3.0 * t2 + 3.0 * t + 1.0) / 6.0;
        double b3 = t3 / 6.0;
        return b0 * p0 + b1 * p1 + b2 * p2 + b3 * p3;
    }

    /**
     * Returns the first derivative of the uniform cubic B-spline segment with respect to {@code t}.
     *
     * @param p0 the first control value of the segment
     * @param p1 the second control value
     * @param p2 the third control value
     * @param p3 the fourth control value
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the derivative at {@code t}
     */
    public static double bsplineDerivative(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t;
        double b0 = (-3.0 * t2 + 6.0 * t - 3.0) / 6.0;
        double b1 = (9.0 * t2 - 12.0 * t) / 6.0;
        double b2 = (-9.0 * t2 + 6.0 * t + 3.0) / 6.0;
        double b3 = (3.0 * t2) / 6.0;
        return b0 * p0 + b1 * p1 + b2 * p2 + b3 * p3;
    }

    /**
     * Returns the second derivative of the uniform cubic B-spline segment with respect to {@code t}.
     *
     * @param p0 the first control value of the segment
     * @param p1 the second control value
     * @param p2 the third control value
     * @param p3 the fourth control value
     * @param t the curve parameter, usually in {@code [0, 1]}
     * @return the second derivative at {@code t}
     */
    public static double bsplineSecondDerivative(double p0, double p1, double p2, double p3, double t) {
        double b0 = (-6.0 * t + 6.0) / 6.0;
        double b1 = (18.0 * t - 12.0) / 6.0;
        double b2 = (-18.0 * t + 6.0) / 6.0;
        double b3 = (6.0 * t) / 6.0;
        return b0 * p0 + b1 * p1 + b2 * p2 + b3 * p3;
    }
}
