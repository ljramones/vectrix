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

/**
 * FABRIK (Aristidou and Lasenby 2011) on a chain of joint positions ({@code positions}, xyz per joint, updated in
 * place; joint 0 is the fixed root), double precision. Bone lengths are taken from
 * {@code lengths} ({@code count − 1} entries), or from the initial positions when {@code lengths} is null. An optional
 * bend limit {@code maxBendCos[i]} (the cosine of the largest angle between bone {@code i − 1} and bone {@code i}; null
 * for none) is enforced in the forward pass. Unreachable targets straighten the chain toward them.
 *
 * @since 2.4.0
 */
public final class Fabrikd {
    private Fabrikd() {
    }

    private static double len(double[] p, int i, int j) {
        double dx = p[3 * j] - (double) p[3 * i], dy = p[3 * j + 1] - (double) p[3 * i + 1];
        double dz = p[3 * j + 2] - (double) p[3 * i + 2];
        return java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Places joint {@code to} at distance {@code l} from joint {@code from}, along from→to (kept if degenerate). */
    private static void place(double[] p, int from, int to, double l, double fallbackX, double fallbackY,
            double fallbackZ) {
        double dx = p[3 * to] - (double) p[3 * from], dy = p[3 * to + 1] - (double) p[3 * from + 1];
        double dz = p[3 * to + 2] - (double) p[3 * from + 2];
        double n = java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (n == 0.0) {
            dx = fallbackX;
            dy = fallbackY;
            dz = fallbackZ;
            n = java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        double k = l / n;
        p[3 * to] = (p[3 * from] + dx * k);
        p[3 * to + 1] = (p[3 * from + 1] + dy * k);
        p[3 * to + 2] = (p[3 * from + 2] + dz * k);
    }

    /**
     * Solves; returns the final distance from the end joint to the target. Stops when it is at most
     * {@code tolerance} or after {@code maxIterations} forward-backward passes.
     */
    public static double solve(double[] positions, int count, double[] lengths, double targetX, double targetY,
            double targetZ, double tolerance, int maxIterations, double[] maxBendCos) {
        double[] p = positions;
        int n = count;
        int last = n - 1;
        double total = 0.0;
        for (int i = 0; i < last; i++) {
            total += lengths != null ? lengths[i] : len(p, i, i + 1);
        }
        // bone lengths are needed throughout; read them before moving anything
        double rootX = p[0], rootY = p[1], rootZ = p[2];
        double dx = targetX - rootX, dy = targetY - rootY, dz = targetZ - rootZ;
        double dist = java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
        double[] l = LENGTHS.get();
        if (l.length < last) {
            l = new double[last];
            LENGTHS.set(l);
        }
        for (int i = 0; i < last; i++) {
            l[i] = lengths != null ? lengths[i] : len(p, i, i + 1);
        }
        if (dist >= total) {
            for (int i = 0; i < last; i++) {
                p[3 * (i + 1)] = (p[3 * i] + dx / dist * l[i]);
                p[3 * (i + 1) + 1] = (p[3 * i + 1] + dy / dist * l[i]);
                p[3 * (i + 1) + 2] = (p[3 * i + 2] + dz / dist * l[i]);
            }
            return error(p, last, targetX, targetY, targetZ);
        }
        for (int it = 0; it < maxIterations; it++) {
            if (error(p, last, targetX, targetY, targetZ) <= tolerance) {
                break;
            }
            // backward: end on the target, then walk to the root
            p[3 * last] = targetX;
            p[3 * last + 1] = targetY;
            p[3 * last + 2] = targetZ;
            for (int i = last - 1; i >= 0; i--) {
                place(p, i + 1, i, l[i], -dx, -dy, -dz);
            }
            // forward: root back in place, then walk to the end, applying bend limits
            p[0] = rootX;
            p[1] = rootY;
            p[2] = rootZ;
            for (int i = 0; i < last; i++) {
                if (maxBendCos != null && i > 0) {
                    limitBend(p, i, maxBendCos[i]);
                }
                place(p, i, i + 1, l[i], dx, dy, dz);
            }
        }
        return error(p, last, targetX, targetY, targetZ);
    }

    private static final ThreadLocal<double[]> LENGTHS = ThreadLocal.withInitial(() -> new double[32]);

    private static double error(double[] p, int last, double tx, double ty, double tz) {
        double ex = p[3 * last] - (double) tx, ey = p[3 * last + 1] - (double) ty, ez = p[3 * last + 2] - (double) tz;
        return java.lang.Math.sqrt(ex * ex + ey * ey + ez * ez);
    }

    /**
     * Turns the direction of bone {@code i} (joint i → i + 1, not yet placed) so that its angle to bone {@code i − 1}
     * is at most {@code acos(maxCos)}, rotating within their common plane.
     */
    private static void limitBend(double[] p, int i, double maxCos) {
        double px = p[3 * i] - (double) p[3 * (i - 1)], py = p[3 * i + 1] - (double) p[3 * (i - 1) + 1];
        double pz = p[3 * i + 2] - (double) p[3 * (i - 1) + 2];
        double qx = p[3 * (i + 1)] - (double) p[3 * i], qy = p[3 * (i + 1) + 1] - (double) p[3 * i + 1];
        double qz = p[3 * (i + 1) + 2] - (double) p[3 * i + 2];
        double pl = java.lang.Math.sqrt(px * px + py * py + pz * pz), ql = java.lang.Math.sqrt(qx * qx + qy * qy + qz * qz);
        if (pl == 0.0 || ql == 0.0) {
            return;
        }
        px /= pl;
        py /= pl;
        pz /= pl;
        double c = (px * qx + py * qy + pz * qz) / ql;
        if (c >= maxCos) {
            return;
        }
        // perpendicular part of the bone direction within the plane; rotate to the limit angle
        double wx = qx / ql - c * px, wy = qy / ql - c * py, wz = qz / ql - c * pz;
        double wl = java.lang.Math.sqrt(wx * wx + wy * wy + wz * wz);
        if (wl == 0.0) { // pointing straight back: any perpendicular
            double ax = java.lang.Math.abs(px) < 0.9 ? 1.0 : 0.0, ay = ax == 1.0 ? 0.0 : 1.0;
            wx = py * 0.0 - pz * ay;
            wy = pz * ax - px * 0.0;
            wz = px * ay - py * ax;
            wl = java.lang.Math.sqrt(wx * wx + wy * wy + wz * wz);
        }
        double s = java.lang.Math.sqrt(java.lang.Math.max(0.0, 1.0 - maxCos * maxCos));
        double nx = maxCos * px + s * wx / wl, ny = maxCos * py + s * wy / wl, nz = maxCos * pz + s * wz / wl;
        p[3 * (i + 1)] = (p[3 * i] + nx * ql);
        p[3 * (i + 1) + 1] = (p[3 * i + 1] + ny * ql);
        p[3 * (i + 1) + 2] = (p[3 * i + 2] + nz * ql);
    }
}
