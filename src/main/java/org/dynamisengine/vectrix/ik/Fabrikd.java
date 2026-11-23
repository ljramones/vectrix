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
 * {@code lengths} ({@code count − 1} entries), or from the initial positions when {@code lengths} is null. Optional bend
 * limits {@code bendLimits[2i], bendLimits[2i + 1]} (radians) bound the bend at joint {@code i}, the angle between bone
 * {@code i − 1} and bone {@code i} (0 = straight), to {@code [min, max]}; they are enforced in the forward pass (joint 0
 * has no bend; pass null for no limits). Unreachable targets straighten the chain toward them (within the limits, if
 * given).
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
     * Solves the chain. Stops when the end joint is within {@code tolerance} of the target or after
     * {@code maxIterations} backward-forward passes; an unreachable target without bend limits is handled in one
     * straightening step. Uses a per-thread array for the bone lengths, which is allocated on the first call in each
     * thread and reallocated when a chain has more than 32 bones.
     *
     * @param positions the joint positions, 3 doubles per joint from index 0; updated in place
     * @param count the number of joints
     * @param lengths the bone lengths, {@code count − 1} entries, or null to measure them from {@code positions}
     * @param targetX the x coordinate of the target
     * @param targetY the y coordinate of the target
     * @param targetZ the z coordinate of the target
     * @param tolerance the end-joint distance at which to stop
     * @param maxIterations the maximum number of backward-forward passes
     * @param bendLimits the {@code [min, max]} bend of each joint in radians, at indices {@code 2i} and {@code 2i + 1}
     *        for joint {@code i} (entries for joint 0 are ignored), or null for no limits
     * @return the final distance from the end joint to the target
     */
    public static double solve(double[] positions, int count, double[] lengths, double targetX, double targetY,
            double targetZ, double tolerance, int maxIterations, double[] bendLimits) {
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
        if (dist >= total && bendLimits == null) { // straighten; with limits the passes below enforce them instead
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
                if (bendLimits != null && i > 0) {
                    limitBend(p, i, bendLimits[2 * i], bendLimits[2 * i + 1]);
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
     * Turns bone {@code i} (joint i → i + 1, not yet placed) within its plane with bone {@code i − 1} so that the bend
     * angle between them lies in {@code [minBend, maxBend]}.
     */
    private static void limitBend(double[] p, int i, double minBend, double maxBend) {
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
        // the bone's direction perpendicular to the previous bone, within their plane
        double wx = qx / ql - c * px, wy = qy / ql - c * py, wz = qz / ql - c * pz;
        double wl = java.lang.Math.sqrt(wx * wx + wy * wy + wz * wz);
        double bend = org.dynamisengine.vectrix.core.AccurateMath.atan2(wl, c);
        double clamped = java.lang.Math.max(minBend, java.lang.Math.min(maxBend, bend));
        if (clamped == bend) {
            return;
        }
        if (wl < 1e-12) { // straight or folded back: the plane is undefined, so pick a fixed perpendicular
            double ax = java.lang.Math.abs(px) < 0.9 ? 1.0 : 0.0, ay = ax == 1.0 ? 0.0 : 1.0;
            wx = -pz * ay;
            wy = pz * ax;
            wz = px * ay - py * ax;
            wl = java.lang.Math.sqrt(wx * wx + wy * wy + wz * wz);
        }
        double cs = org.dynamisengine.vectrix.core.AccurateMath.cos(clamped);
        double sn = org.dynamisengine.vectrix.core.AccurateMath.sin(clamped);
        double nx = cs * px + sn * wx / wl, ny = cs * py + sn * wy / wl, nz = cs * pz + sn * wz / wl;
        p[3 * (i + 1)] = (p[3 * i] + nx * ql);
        p[3 * (i + 1) + 1] = (p[3 * i + 1] + ny * ql);
        p[3 * (i + 1) + 2] = (p[3 * i + 2] + nz * ql);
    }
}
