/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix Contributors
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
package org.vectrix.physics;

import org.vectrix.core.Math;
import org.vectrix.core.Matrix3f;
import org.vectrix.core.Vector3f;

/**
 * Position-based dynamics constraint helpers.
 *
 * @since 1.10.12
 */
public final class PbdConstraintsf {
    private static final float EPS = 1E-6f;

    private PbdConstraintsf() {
    }

    public static Vector3f distanceConstraint(
            Vector3f posA, float invMassA,
            Vector3f posB, float invMassB,
            float restLength, float stiffness,
            Vector3f dest) {
        float dx = posA.x - posB.x;
        float dy = posA.y - posB.y;
        float dz = posA.z - posB.z;
        float len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float w = invMassA + invMassB;
        if (len < EPS || w <= 0.0f) {
            return dest.set(0.0f, 0.0f, 0.0f);
        }
        float c = len - restLength;
        float scale = -stiffness * invMassA * c / (w * len);
        return dest.set(dx * scale, dy * scale, dz * scale);
    }

    public static void bendConstraint(
            Vector3f p0, Vector3f p1, Vector3f p2,
            float restAngle, float stiffness,
            Vector3f d0, Vector3f d1, Vector3f d2) {
        float v0x = p0.x - p1.x, v0y = p0.y - p1.y, v0z = p0.z - p1.z;
        float v1x = p2.x - p1.x, v1y = p2.y - p1.y, v1z = p2.z - p1.z;
        float l0 = Math.sqrt(v0x * v0x + v0y * v0y + v0z * v0z);
        float l1 = Math.sqrt(v1x * v1x + v1y * v1y + v1z * v1z);
        if (l0 < EPS || l1 < EPS) {
            d0.set(0.0f, 0.0f, 0.0f);
            d1.set(0.0f, 0.0f, 0.0f);
            d2.set(0.0f, 0.0f, 0.0f);
            return;
        }
        float n0x = v0x / l0, n0y = v0y / l0, n0z = v0z / l0;
        float n1x = v1x / l1, n1y = v1y / l1, n1z = v1z / l1;
        float dot = Math.max(-1.0f, Math.min(1.0f, n0x * n1x + n0y * n1y + n0z * n1z));
        float angle = Math.acos(dot);
        float error = angle - restAngle;
        float ax = n0y * n1z - n0z * n1y;
        float ay = n0z * n1x - n0x * n1z;
        float az = n0x * n1y - n0y * n1x;
        float al = Math.sqrt(ax * ax + ay * ay + az * az);
        if (al < EPS) {
            d0.set(0.0f, 0.0f, 0.0f);
            d1.set(0.0f, 0.0f, 0.0f);
            d2.set(0.0f, 0.0f, 0.0f);
            return;
        }
        ax /= al;
        ay /= al;
        az /= al;
        float g0x = (ay * n0z - az * n0y) / l0;
        float g0y = (az * n0x - ax * n0z) / l0;
        float g0z = (ax * n0y - ay * n0x) / l0;
        float g2x = (n1y * az - n1z * ay) / l1;
        float g2y = (n1z * ax - n1x * az) / l1;
        float g2z = (n1x * ay - n1y * ax) / l1;
        float scale = -stiffness * error;
        d0.set(g0x * scale, g0y * scale, g0z * scale);
        d2.set(g2x * scale, g2y * scale, g2z * scale);
        d1.set(-(d0.x + d2.x), -(d0.y + d2.y), -(d0.z + d2.z));
    }

    public static float volumeConstraint(
            Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3,
            float restVolume, float stiffness,
            Vector3f d0, Vector3f d1, Vector3f d2, Vector3f d3) {
        float x10 = p1.x - p0.x, y10 = p1.y - p0.y, z10 = p1.z - p0.z;
        float x20 = p2.x - p0.x, y20 = p2.y - p0.y, z20 = p2.z - p0.z;
        float x30 = p3.x - p0.x, y30 = p3.y - p0.y, z30 = p3.z - p0.z;
        float cx = y20 * z30 - z20 * y30;
        float cy = z20 * x30 - x20 * z30;
        float cz = x20 * y30 - y20 * x30;
        float volume = (x10 * cx + y10 * cy + z10 * cz) / 6.0f;
        float c = volume - restVolume;

        gradientVolume(p1, p2, p3, d0);
        gradientVolume(p2, p3, p0, d1);
        gradientVolume(p3, p0, p1, d2);
        gradientVolume(p0, p1, p2, d3);

        float denom = d0.lengthSquared() + d1.lengthSquared() + d2.lengthSquared() + d3.lengthSquared();
        if (denom < EPS) {
            d0.set(0.0f, 0.0f, 0.0f);
            d1.set(0.0f, 0.0f, 0.0f);
            d2.set(0.0f, 0.0f, 0.0f);
            d3.set(0.0f, 0.0f, 0.0f);
            return volume;
        }
        float lambda = -stiffness * c / denom;
        d0.mul(lambda);
        d1.mul(lambda);
        d2.mul(lambda);
        d3.mul(lambda);
        return volume;
    }

    public static void shapeMatchingGoal(
            Vector3f[] currentPositions,
            Vector3f[] restPositions,
            float[] masses,
            Matrix3f rotationOut,
            Vector3f[] goalPositions) {
        int n = currentPositions.length;
        float massSum = 0.0f;
        Vector3f currentCom = new Vector3f();
        Vector3f restCom = new Vector3f();
        for (int i = 0; i < n; i++) {
            float m = masses[i];
            massSum += m;
            currentCom.fma(m, currentPositions[i]);
            restCom.fma(m, restPositions[i]);
        }
        if (massSum <= EPS) {
            rotationOut.identity();
            for (int i = 0; i < n; i++) {
                goalPositions[i].set(currentPositions[i]);
            }
            return;
        }
        currentCom.div(massSum);
        restCom.div(massSum);

        float sxx = 0.0f, sxy = 0.0f, sxz = 0.0f;
        float syx = 0.0f, syy = 0.0f, syz = 0.0f;
        float szx = 0.0f, szy = 0.0f, szz = 0.0f;
        for (int i = 0; i < n; i++) {
            float m = masses[i];
            float qx = restPositions[i].x - restCom.x;
            float qy = restPositions[i].y - restCom.y;
            float qz = restPositions[i].z - restCom.z;
            float px = currentPositions[i].x - currentCom.x;
            float py = currentPositions[i].y - currentCom.y;
            float pz = currentPositions[i].z - currentCom.z;
            sxx += m * px * qx;
            sxy += m * px * qy;
            sxz += m * px * qz;
            syx += m * py * qx;
            syy += m * py * qy;
            syz += m * py * qz;
            szx += m * pz * qx;
            szy += m * pz * qy;
            szz += m * pz * qz;
        }

        Vector3f c0 = new Vector3f(sxx, syx, szx);
        Vector3f c1 = new Vector3f(sxy, syy, szy);
        if (c0.lengthSquared() < EPS || c1.lengthSquared() < EPS) {
            rotationOut.identity();
        } else {
            c0.normalize();
            c1.fma(-c0.dot(c1), c0).normalize();
            Vector3f c2 = c0.cross(c1, new Vector3f());
            rotationOut.set(
                    c0.x, c1.x, c2.x,
                    c0.y, c1.y, c2.y,
                    c0.z, c1.z, c2.z);
        }

        for (int i = 0; i < n; i++) {
            Vector3f q = restPositions[i];
            float qx = q.x - restCom.x;
            float qy = q.y - restCom.y;
            float qz = q.z - restCom.z;
            goalPositions[i].set(
                    rotationOut.m00 * qx + rotationOut.m10 * qy + rotationOut.m20 * qz + currentCom.x,
                    rotationOut.m01 * qx + rotationOut.m11 * qy + rotationOut.m21 * qz + currentCom.y,
                    rotationOut.m02 * qx + rotationOut.m12 * qy + rotationOut.m22 * qz + currentCom.z);
        }
    }

    private static void gradientVolume(Vector3f a, Vector3f b, Vector3f c, Vector3f out) {
        out.set(
                (b.y - a.y) * (c.z - a.z) - (b.z - a.z) * (c.y - a.y),
                (b.z - a.z) * (c.x - a.x) - (b.x - a.x) * (c.z - a.z),
                (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x))
                .mul(1.0f / 6.0f);
    }
}
