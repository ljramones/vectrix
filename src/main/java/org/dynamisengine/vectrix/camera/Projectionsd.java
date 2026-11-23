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

import org.dynamisengine.vectrix.core.DepthRange;

import org.dynamisengine.vectrix.core.AccurateMath;
import org.dynamisengine.vectrix.core.Matrix4d;
import org.dynamisengine.vectrix.core.Matrix4dc;
import org.dynamisengine.vectrix.core.Vector2d;
import org.dynamisengine.vectrix.core.Vector4dc;
import org.dynamisengine.vectrix.sampling.HaltonSequence;

/**
 * Projection matrices and depth conversions (double precision), exact for every {@link DepthRange}.
 * <p>
 * Infinite far planes ({@code far = +∞}) use the exact limits: no epsilon is added. With
 * {@link DepthRange#REVERSED_ZERO_TO_ONE} this is the recommended setup (depth {@code near / d} never reaches the
 * clip boundary). With the other ranges, points at infinity land exactly on depth 1 and rounding can clip them; use a
 * finite far plane there if that matters. Matrices are computed in double precision and rounded once.
 *
 * @since 2.3.0
 */
public final class Projectionsd {
    private Projectionsd() {
    }

    private static Matrix4d set(Matrix4d dest, double m00, double m01, double m02, double m03, double m10,
            double m11, double m12, double m13, double m20, double m21, double m22, double m23, double m30,
            double m31, double m32, double m33) {
        return dest.set(m00, m01, m02, m03, m10, m11, m12,
                m13, m20, m21, m22, m23, m30, m31, m32,
                m33);
    }

    /**
     * Builds a symmetric right-handed perspective projection for {@code range} into {@code dest}. The camera looks
     * down {@code −z}; the projection maps view distance {@code near} to {@code range.nearNdc()} and {@code far} to
     * {@code range.farNdc()}. Inputs are not validated.
     *
     * @param fovy the vertical field of view in radians, in (0, π)
     * @param aspect the viewport width divided by its height
     * @param near the near-plane distance, greater than 0
     * @param far the far-plane distance, greater than {@code near}, or {@code +∞} for an exact infinite projection
     * @param range the NDC depth convention to build for
     * @param dest receives the projection
     * @return {@code dest}
     */
    public static Matrix4d perspective(double fovy, double aspect, double near, double far, DepthRange range,
            Matrix4d dest) {
        double h = AccurateMath.tan(0.5 * fovy);
        double n = near, f = far;
        boolean inf = Double.isInfinite(f);
        double m22, m32;
        switch (range) {
            case NEGATIVE_ONE_TO_ONE:
                m22 = inf ? -1.0 : (f + n) / (n - f);
                m32 = inf ? -2.0 * n : 2.0 * f * n / (n - f);
                break;
            case ZERO_TO_ONE:
                m22 = inf ? -1.0 : f / (n - f);
                m32 = inf ? -n : f * n / (n - f);
                break;
            default:
                m22 = inf ? 0.0 : n / (f - n);
                m32 = inf ? n : f * n / (f - n);
                break;
        }
        return set(dest, 1.0 / (h * aspect), 0, 0, 0, 0, 1.0 / h, 0, 0, 0, 0, m22, -1, 0, 0, m32, 0);
    }

    /**
     * Builds a reverse-Z perspective projection ({@link DepthRange#REVERSED_ZERO_TO_ONE}) with an exact infinite far
     * plane: view distance {@code d} maps to NDC depth {@code near / d}.
     *
     * @param fovy the vertical field of view in radians, in (0, π)
     * @param aspect the viewport width divided by its height
     * @param near the near-plane distance, greater than 0
     * @param dest receives the projection
     * @return {@code dest}
     */
    public static Matrix4d reverseZInfinite(double fovy, double aspect, double near, Matrix4d dest) {
        return perspective(fovy, aspect, near, Double.POSITIVE_INFINITY, DepthRange.REVERSED_ZERO_TO_ONE, dest);
    }

    /**
     * Returns the near-plane distance of a perspective projection, read from its {@code m22} and {@code m32}.
     *
     * @param proj a perspective projection built for {@code range}, finite or infinite
     * @param range the depth convention {@code proj} was built for
     * @return the near-plane distance
     */
    public static double nearPlane(Matrix4dc proj, DepthRange range) {
        double m22 = proj.m22(), m32 = proj.m32();
        switch (range) {
            case NEGATIVE_ONE_TO_ONE:
                return (m32 / (m22 - 1.0));
            case ZERO_TO_ONE:
                return (m32 / m22);
            default:
                return (m32 / (m22 + 1.0));
        }
    }

    /**
     * Returns the far-plane distance of a perspective projection, read from its {@code m22} and {@code m32}.
     *
     * @param proj a perspective projection built for {@code range}
     * @param range the depth convention {@code proj} was built for
     * @return the far-plane distance, or {@code +∞} for an infinite projection
     */
    public static double farPlane(Matrix4dc proj, DepthRange range) {
        double m22 = proj.m22(), m32 = proj.m32();
        double den = range == DepthRange.REVERSED_ZERO_TO_ONE ? m22 : m22 + 1.0;
        // an infinite projection has den = 0 exactly; its m32 sign differs by range, so return +inf directly
        return den == 0.0 ? Double.POSITIVE_INFINITY : (m32 / den);
    }

    /**
     * Returns the NDC depth of the view-space point {@code (0, 0, −viewDistance)}. The result ignores the x and y
     * columns of {@code proj}, so it is the depth of every point at that distance only for projections whose depth
     * does not depend on x and y (all except {@link #obliqueNearPlane oblique} ones).
     *
     * @param proj the projection matrix
     * @param viewDistance the distance in front of the camera, along {@code −z}
     * @return the NDC depth, in the convention {@code proj} was built for
     */
    public static double ndcDepth(Matrix4dc proj, double viewDistance) {
        double z = -(double) viewDistance;
        return ((proj.m22() * z + proj.m32()) / (proj.m23() * z + proj.m33()));
    }

    /**
     * Returns the view distance of an NDC depth for a perspective projection with planes {@code near} and
     * {@code far}: the inverse of {@link #ndcDepth}. Use it to linearize a depth-buffer value.
     *
     * @param ndcDepth the NDC depth, in the convention {@code range}
     * @param near the near-plane distance, greater than 0
     * @param far the far-plane distance, or {@code +∞} for an infinite projection
     * @param range the depth convention of {@code ndcDepth}
     * @return the distance in front of the camera, along {@code −z}
     */
    public static double linearDepth(double ndcDepth, double near, double far, DepthRange range) {
        double z = ndcDepth, n = near, f = far;
        boolean inf = Double.isInfinite(f);
        switch (range) {
            case NEGATIVE_ONE_TO_ONE:
                return (inf ? 2.0 * n / (1.0 - z) : 2.0 * f * n / ((f + n) - z * (f - n)));
            case ZERO_TO_ONE:
                return (inf ? n / (1.0 - z) : f * n / (f - z * (f - n)));
            default:
                return (inf ? n / z : f * n / (n + z * (f - n)));
        }
    }

    /**
     * The clip-space correction from an OpenGL-convention projection ([−1, 1] depth, y up) to Vulkan's ([0, 1]
     * depth, y down): {@code dest = C}, to be applied as {@code C · P}. {@code C} negates y and maps depth
     * {@code z} to {@code (z + w) / 2}.
     *
     * @param dest receives the correction matrix
     * @return {@code dest}
     */
    public static Matrix4d vulkanClipCorrection(Matrix4d dest) {
        return dest.set(1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0.5, 0, 0, 0, 0.5, 1);
    }

    /**
     * Negates clip-space y (the whole second row), for APIs whose framebuffer y points down. Unlike negating
     * {@code m11} alone, this is also correct for off-center, jittered and oblique projections.
     *
     * @param proj the projection matrix
     * @param dest receives the flipped matrix; may be {@code proj}
     * @return {@code dest}
     */
    public static Matrix4d flipY(Matrix4dc proj, Matrix4d dest) {
        return dest.set(proj.m00(), -proj.m01(), proj.m02(), proj.m03(), proj.m10(), -proj.m11(), proj.m12(),
                proj.m13(), proj.m20(), -proj.m21(), proj.m22(), proj.m23(), proj.m30(), -proj.m31(), proj.m32(),
                proj.m33());
    }

    /**
     * Shifts the projected image by exactly {@code (ndcX, ndcY)} in NDC ({@code clip.xy += offset · clip.w}), for
     * any projection. A shift of {@code s} pixels is {@code 2 s / width} in NDC.
     *
     * @param proj the projection matrix
     * @param ndcX the horizontal shift in NDC units
     * @param ndcY the vertical shift in NDC units
     * @param dest receives the jittered matrix; may be {@code proj}
     * @return {@code dest}
     */
    public static Matrix4d applyJitter(Matrix4dc proj, double ndcX, double ndcY, Matrix4d dest) {
        double x = ndcX, y = ndcY;
        return set(dest, proj.m00() + x * proj.m03(), proj.m01() + y * proj.m03(), proj.m02(), proj.m03(),
                proj.m10() + x * proj.m13(), proj.m11() + y * proj.m13(), proj.m12(), proj.m13(),
                proj.m20() + x * proj.m23(), proj.m21() + y * proj.m23(), proj.m22(), proj.m23(),
                proj.m30() + x * proj.m33(), proj.m31() + y * proj.m33(), proj.m32(), proj.m33());
    }

    /**
     * TAA jitter for frame {@code frameIndex}: the Halton (2, 3) point with index {@code frameIndex mod sampleCount
     * + 1} ({@code sampleCount ≤ 0}: no wrap), as a sub-pixel offset in [−0.5, 0.5) pixels, returned in NDC units
     * ({@code 2 · offset / size}) for {@link #applyJitter}.
     *
     * @param frameIndex the frame number, at least 0
     * @param sampleCount the length of the jitter cycle, or a value {@code ≤ 0} for no cycle
     * @param width the render-target width in pixels
     * @param height the render-target height in pixels
     * @param dest receives the offset in NDC units
     * @return {@code dest}
     * @throws IllegalArgumentException if {@code sampleCount ≤ 0} and {@code frameIndex + 1} is negative (including
     *         overflow at {@link Integer#MAX_VALUE})
     */
    public static Vector2d haltonJitter(int frameIndex, int sampleCount, double width, double height, Vector2d dest) {
        int i = (sampleCount > 0 ? java.lang.Math.floorMod(frameIndex, sampleCount) : frameIndex) + 1;
        double px = HaltonSequence.haltonDouble(i, 2) - 0.5, py = HaltonSequence.haltonDouble(i, 3) - 0.5;
        return dest.set((2.0 * px / width), (2.0 * py / height));
    }

    private static final ThreadLocal<Matrix4d> INVERSE = ThreadLocal.withInitial(Matrix4d::new);

    /**
     * Oblique near-plane clipping (Lengyel 2005): replaces the near plane of perspective {@code proj} with the
     * view-space plane {@code (a, b, c, d)} (points with {@code a x + b y + c z + d ≥ 0} are kept; the camera must be
     * on the clipped side, {@code d < 0}), keeping the frustum's far corner opposite the plane. Used for mirror and
     * portal rendering. The other planes are unchanged; the far plane tilts. The plane must be expressed in the view
     * space of {@code proj}. Inverts {@code proj} into a per-thread scratch matrix, which is allocated on the first
     * call in each thread.
     *
     * @param proj a perspective projection built for {@code range}
     * @param plane the clip plane {@code (a, b, c, d)} in view space, with {@code d < 0}
     * @param range the depth convention {@code proj} was built for
     * @param dest receives the oblique projection; may be {@code proj}
     * @return {@code dest}
     */
    public static Matrix4d obliqueNearPlane(Matrix4dc proj, Vector4dc plane, DepthRange range, Matrix4d dest) {
        Matrix4d inv = proj.invert(INVERSE.get());
        double pa = plane.x(), pb = plane.y(), pc = plane.z(), pd = plane.w();
        // the plane in clip space: dot with the columns of P^-1
        double cx = pa * inv.m00() + pb * inv.m01() + pc * inv.m02() + pd * inv.m03();
        double cy = pa * inv.m10() + pb * inv.m11() + pc * inv.m12() + pd * inv.m13();
        double sx = cx >= 0.0 ? 1.0 : -1.0, sy = cy >= 0.0 ? 1.0 : -1.0, sz = range.farNdc();
        // q = P^-1 (sx, sy, farNdc, 1): the far corner opposite the plane, in view space
        double qx = inv.m00() * sx + inv.m10() * sy + inv.m20() * sz + inv.m30();
        double qy = inv.m01() * sx + inv.m11() * sy + inv.m21() * sz + inv.m31();
        double qz = inv.m02() * sx + inv.m12() * sy + inv.m22() * sz + inv.m32();
        double qw = inv.m03() * sx + inv.m13() * sy + inv.m23() * sz + inv.m33();
        double k = 1.0 / (pa * qx + pb * qy + pc * qz + pd * qw);
        double r0, r1, r2, r3; // new third row
        switch (range) {
            case NEGATIVE_ONE_TO_ONE:
                r0 = 2.0 * k * pa - proj.m03();
                r1 = 2.0 * k * pb - proj.m13();
                r2 = 2.0 * k * pc - proj.m23();
                r3 = 2.0 * k * pd - proj.m33();
                break;
            case ZERO_TO_ONE:
                r0 = k * pa;
                r1 = k * pb;
                r2 = k * pc;
                r3 = k * pd;
                break;
            default:
                r0 = proj.m03() - k * pa;
                r1 = proj.m13() - k * pb;
                r2 = proj.m23() - k * pc;
                r3 = proj.m33() - k * pd;
                break;
        }
        return set(dest, proj.m00(), proj.m01(), r0, proj.m03(), proj.m10(), proj.m11(), r1, proj.m13(), proj.m20(),
                proj.m21(), r2, proj.m23(), proj.m30(), proj.m31(), r3, proj.m33());
    }

}
