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
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Vector3fc;
import org.dynamisengine.vectrix.core.Vector4f;

/**
 * Cascaded shadow maps (single precision): split distances, a rotation-invariant bounding sphere per cascade, and a
 * stable, texel-snapped light projection.
 * <p>
 * Stability: the sphere is computed in closed form in view space from the projection's field of view, so its radius
 * is bit-identical however the camera turns; the light projection is sized from the radius alone and its origin is
 * snapped to whole shadow-map texels in light space, so shadow texels stay fixed in the world as the camera moves
 * and turns (no shimmering), at the cost of some unused map area compared with a tight AABB fit.
 *
 * @since 2.3.0
 */
public final class ShadowCascadesf {
    private ShadowCascadesf() {
    }

    /**
     * The practical split scheme (Zhang et al. 2006): {@code split_i = λ · n (f/n)^(i/N) + (1 − λ)(n + (f − n) i/N)},
     * blending logarithmic ({@code λ = 1}) and uniform ({@code λ = 0}) splits. Writes {@code count + 1} distances to
     * {@code out}, with {@code out[0] = near} and {@code out[count] = far} exactly.
     *
     * @param near the camera near distance {@code n}, greater than 0
     * @param far the far distance {@code f} of the last cascade, greater than {@code near}
     * @param count the number of cascades {@code N}, at least 1
     * @param lambda the blend {@code λ}, in [0, 1]
     * @param out receives the split distances; at least {@code count + 1} long, written from index 0
     * @return {@code out}
     */
    public static float[] practicalSplits(float near, float far, int count, float lambda, float[] out) {
        double n = near, f = far, ratio = f / n;
        out[0] = near;
        for (int i = 1; i < count; i++) {
            double t = (double) i / count;
            out[i] = (float) (lambda * n * AccurateMath.pow(ratio, t) + (1.0 - lambda) * (n + (f - n) * t));
        }
        out[count] = far;
        return out;
    }

    /**
     * Minimal bounding sphere of the view-frustum slice between distances {@code sliceNear} and {@code sliceFar}
     * of a symmetric perspective with half-angle tangents {@code tanHalfX} (horizontal) and {@code tanHalfY}, in
     * view space: writes {@code (0, 0, −centerDistance, radius)}. Transform the center to world space with the
     * camera's inverse view matrix.
     *
     * @param tanHalfX the tangent of half the horizontal field of view
     * @param tanHalfY the tangent of half the vertical field of view
     * @param sliceNear the view distance where the slice begins, at least 0
     * @param sliceFar the view distance where the slice ends, greater than {@code sliceNear}
     * @param dest receives {@code (0, 0, −centerDistance, radius)}
     * @return {@code dest}
     */
    public static Vector4f sliceSphere(float tanHalfX, float tanHalfY, float sliceNear, float sliceFar,
            Vector4f dest) {
        double k2 = (double) tanHalfX * tanHalfX + (double) tanHalfY * tanHalfY;
        double n = sliceNear, f = sliceFar;
        // the center on the axis equidistant from the near and far corners, unless that lies beyond the far plane,
        // where the far rectangle's circumcircle already contains the near corners
        double c = java.lang.Math.min(0.5 * (f + n) * (1.0 + k2), f);
        double r = java.lang.Math.sqrt((f - c) * (f - c) + f * f * k2);
        return dest.set(0.0f, 0.0f, (float) -c, (float) r);
    }

    /**
     * Builds the light view and orthographic projection for a cascade whose bounding sphere (world space) is
     * {@code center}, {@code radius}, lit along {@code lightDir} (from the light into the scene). The map covers the
     * sphere, extended by {@code casterDistance} toward the light for occluders outside it; the light-space origin
     * is snapped to whole texels of a {@code resolution}² map, whose half-width {@code radius · res / (res − 2)}
     * leaves room for the snap so the sphere is always inside. {@code up} fixes the light's rotation about its
     * direction (any direction not parallel to {@code lightDir}); keep it constant between frames. The depth range
     * of the projection runs from the light plane, {@code casterDistance + radius} in front of the center, to the far
     * side of the sphere.
     *
     * @param center the center of the cascade's bounding sphere, in world space
     * @param radius the radius of the bounding sphere, greater than 0
     * @param lightDir the direction the light travels; normalized internally, so it need not be unit length
     * @param up a reference up direction, not parallel to {@code lightDir}
     * @param casterDistance how far beyond the sphere, toward the light, occluders are still captured, at least 0
     * @param resolution the shadow-map size in texels (square), greater than 2
     * @param range the depth convention of the projection
     * @param viewDest receives the light view matrix (world to light space)
     * @param projDest receives the orthographic light projection
     */
    public static void stableLightMatrices(Vector3fc center, float radius, Vector3fc lightDir, Vector3fc up,
            float casterDistance, int resolution, DepthRange range, Matrix4f viewDest, Matrix4f projDest) {
        double dx = lightDir.x(), dy = lightDir.y(), dz = lightDir.z();
        double dl = java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= dl;
        dy /= dl;
        dz /= dl;
        // light basis: zAxis = -dir (right-handed view looks down -z), xAxis = up x zAxis, yAxis = zAxis x xAxis
        double zx = -dx, zy = -dy, zz = -dz;
        double xx = up.y() * zz - up.z() * zy, xy = up.z() * zx - up.x() * zz, xz = up.x() * zy - up.y() * zx;
        double xl = java.lang.Math.sqrt(xx * xx + xy * xy + xz * xz);
        xx /= xl;
        xy /= xl;
        xz /= xl;
        double yx = zy * xz - zz * xy, yy = zz * xx - zx * xz, yz = zx * xy - zy * xx;
        // half-extent with room for the snap: the center moves by less than one texel, so r + texel <= h
        double h = (double) radius * resolution / (resolution - 2.0);
        // the sphere center in the rotated (origin-anchored) light frame, snapped to texels
        double texel = 2.0 * h / resolution;
        double cx = xx * center.x() + xy * center.y() + xz * center.z();
        double cy = yx * center.x() + yy * center.y() + yz * center.z();
        double cz = zx * center.x() + zy * center.y() + zz * center.z();
        cx = java.lang.Math.floor(cx / texel) * texel;
        cy = java.lang.Math.floor(cy / texel) * texel;
        double depth = casterDistance + radius; // the center sits this far in front of the light plane
        double tx = -cx, ty = -cy, tz = -cz - depth;
        viewDest.set((float) xx, (float) yx, (float) zx, 0.0f, (float) xy, (float) yy, (float) zy, 0.0f, (float) xz,
                (float) yz, (float) zz, 0.0f, (float) tx, (float) ty, (float) tz, 1.0f);
        double r = h, n = 0.0, f = casterDistance + 2.0 * radius;
        // orthographic, right-handed, [-h, h]^2, view z in [-f, -n]
        double m22, m32;
        switch (range) {
            case NEGATIVE_ONE_TO_ONE:
                m22 = -2.0 / (f - n);
                m32 = -(f + n) / (f - n);
                break;
            case ZERO_TO_ONE:
                m22 = -1.0 / (f - n);
                m32 = -n / (f - n);
                break;
            default:
                m22 = 1.0 / (f - n);
                m32 = f / (f - n);
                break;
        }
        projDest.set((float) (1.0 / r), 0.0f, 0.0f, 0.0f, 0.0f, (float) (1.0 / r), 0.0f, 0.0f, 0.0f, 0.0f,
                (float) m22, 0.0f, 0.0f, 0.0f, (float) m32, 1.0f);
    }

    /**
     * Forwards to the {@link org.dynamisengine.vectrix.core.DepthRange} overload.
     *
     * @param center the center of the cascade's bounding sphere, in world space
     * @param radius the radius of the bounding sphere
     * @param lightDir the direction the light travels
     * @param up a reference up direction, not parallel to {@code lightDir}
     * @param casterDistance how far beyond the sphere, toward the light, occluders are still captured
     * @param resolution the shadow-map size in texels, greater than 2
     * @param range the depth convention of the projection
     * @param viewDest receives the light view matrix
     * @param projDest receives the orthographic light projection
     * @deprecated use the overload taking {@link org.dynamisengine.vectrix.core.DepthRange}
     */
    @Deprecated(since = "2.6.0", forRemoval = true)
    public static void stableLightMatrices(Vector3fc center, float radius, Vector3fc lightDir, Vector3fc up,
            float casterDistance, int resolution, org.dynamisengine.vectrix.camera.DepthRange range, Matrix4f viewDest, Matrix4f projDest) {
        stableLightMatrices(center, radius, lightDir, up, casterDistance, resolution, range.toCore(), viewDest, projDest);
    }
}
