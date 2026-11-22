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
package org.dynamisengine.vectrix.ik;

import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * Analytic two-bone IK (single-precision interface, double-precision evaluation): places the middle and end joints of
 * a root-mid-end chain so the end reaches {@code target}, bending in the plane through the root, the target and the
 * {@code pole} (on the pole's side). The triangle angle uses Kahan's stable area formula, so straight and fully folded
 * chains are as accurate as bent ones.
 * <p>
 * Results: the new joint positions, and the world-space delta rotations to apply to the root bone and then to the
 * middle bone (pre-multiplied onto their world rotations, root first). Targets beyond reach straighten the chain
 * toward the target; with {@code maxStretch > 1} the bones may lengthen by up to that factor first (the positions then
 * include the stretch; the caller scales the bones). Targets closer than {@code |a − b|} fold the chain completely.
 *
 * @since 2.4.0
 */
public final class TwoBoneIkf {
    private TwoBoneIkf() {
    }

    /**
     * Solves the chain; returns the remaining distance from the new end position to the target (0 when reached).
     * If the pole is in line with the target, the current bend direction is kept.
     */
    public static float solve(Vector3fc root, Vector3fc mid, Vector3fc end, Vector3fc target, Vector3fc pole,
            float maxStretch, Vector3f midOut, Vector3f endOut, Quaternionf rootDelta, Quaternionf midDelta) {
        double rx = root.x(), ry = root.y(), rz = root.z();
        double mx = mid.x(), my = mid.y(), mz = mid.z(), ex = end.x(), ey = end.y(), ez = end.z();
        double a = java.lang.Math.sqrt(sq(mx - rx) + sq(my - ry) + sq(mz - rz));
        double b = java.lang.Math.sqrt(sq(ex - mx) + sq(ey - my) + sq(ez - mz));
        double tx = target.x() - rx, ty = target.y() - ry, tz = target.z() - rz;
        double d = java.lang.Math.sqrt(tx * tx + ty * ty + tz * tz);
        if (d == 0.0 || a == 0.0 || b == 0.0) {
            midOut.set(mid);
            endOut.set(end);
            rootDelta.identity();
            midDelta.identity();
            return (float) java.lang.Math.sqrt(sq(ex - target.x()) + sq(ey - target.y()) + sq(ez - target.z()));
        }
        double ux = tx / d, uy = ty / d, uz = tz / d;
        // bend direction: the pole's component perpendicular to the target direction, else the current bend
        double hx = pole.x() - rx, hy = pole.y() - ry, hz = pole.z() - rz;
        double k = hx * ux + hy * uy + hz * uz;
        hx -= k * ux;
        hy -= k * uy;
        hz -= k * uz;
        double hl = java.lang.Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (hl < 1e-9 * d) {
            hx = mx - rx;
            hy = my - ry;
            hz = mz - rz;
            k = hx * ux + hy * uy + hz * uz;
            hx -= k * ux;
            hy -= k * uy;
            hz -= k * uz;
            hl = java.lang.Math.sqrt(hx * hx + hy * hy + hz * hz);
            if (hl < 1e-12 * a) { // straight chain along the target: any perpendicular
                double px = java.lang.Math.abs(ux) < 0.9 ? 1.0 : 0.0, py = px == 1.0 ? 0.0 : 1.0;
                hx = py * uz;
                hy = -px * uz;
                hz = px * uy - py * ux;
                hl = java.lang.Math.sqrt(hx * hx + hy * hy + hz * hz);
            }
        }
        hx /= hl;
        hy /= hl;
        hz /= hl;
        double reach = a + b;
        double s = d > reach ? java.lang.Math.min(d / reach, java.lang.Math.max(maxStretch, 1.0)) : 1.0;
        double as = a * s, bs = b * s;
        double dd = java.lang.Math.max(java.lang.Math.abs(as - bs), java.lang.Math.min(d, as + bs));
        double[][] scratch = IkMath.SCRATCH.get();
        double[] cs = scratch[0];
        IkMath.angleOpposite(as, dd, bs, cs); // angle at the root, between the target direction and the first bone
        double nmx = rx + as * (cs[0] * ux + cs[1] * hx), nmy = ry + as * (cs[0] * uy + cs[1] * hy);
        double nmz = rz + as * (cs[0] * uz + cs[1] * hz);
        double nex = rx + dd * ux, ney = ry + dd * uy, nez = rz + dd * uz;
        double[] q1 = scratch[1], q2 = scratch[2], e1 = scratch[4];
        IkMath.rotationTo(mx - rx, my - ry, mz - rz, nmx - rx, nmy - ry, nmz - rz, q1);
        IkMath.rotate(q1, ex - mx, ey - my, ez - mz, e1, 0);
        IkMath.rotationTo(e1[0], e1[1], e1[2], nex - nmx, ney - nmy, nez - nmz, q2);
        midOut.set((float) nmx, (float) nmy, (float) nmz);
        endOut.set((float) nex, (float) ney, (float) nez);
        rootDelta.set((float) q1[0], (float) q1[1], (float) q1[2], (float) q1[3]);
        midDelta.set((float) q2[0], (float) q2[1], (float) q2[2], (float) q2[3]);
        return (float) java.lang.Math.sqrt(sq(nex - target.x()) + sq(ney - target.y()) + sq(nez - target.z()));
    }

    private static double sq(double v) {
        return v * v;
    }
}
