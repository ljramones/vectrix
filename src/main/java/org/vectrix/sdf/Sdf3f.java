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
package org.vectrix.sdf;

import org.vectrix.core.Vector2f;
import org.vectrix.core.Vector3fc;

public final class Sdf3f {
    private Sdf3f() {
    }

    public static float sphere(Vector3fc p, float radius) {
        return p.length() - radius;
    }

    public static float box(Vector3fc p, Vector3fc halfExtents) {
        float qx = java.lang.Math.abs(p.x()) - halfExtents.x();
        float qy = java.lang.Math.abs(p.y()) - halfExtents.y();
        float qz = java.lang.Math.abs(p.z()) - halfExtents.z();
        float ox = java.lang.Math.max(qx, 0.0f);
        float oy = java.lang.Math.max(qy, 0.0f);
        float oz = java.lang.Math.max(qz, 0.0f);
        float outside = (float) java.lang.Math.sqrt(ox * ox + oy * oy + oz * oz);
        float inside = java.lang.Math.min(java.lang.Math.max(qx, java.lang.Math.max(qy, qz)), 0.0f);
        return outside + inside;
    }

    public static float capsule(Vector3fc p, Vector3fc a, Vector3fc b, float radius) {
        float pax = p.x() - a.x(), pay = p.y() - a.y(), paz = p.z() - a.z();
        float bax = b.x() - a.x(), bay = b.y() - a.y(), baz = b.z() - a.z();
        float h = java.lang.Math.max(0.0f, java.lang.Math.min(1.0f, (pax * bax + pay * bay + paz * baz) / (bax * bax + bay * bay + baz * baz)));
        float dx = pax - bax * h, dy = pay - bay * h, dz = paz - baz * h;
        return (float) java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz) - radius;
    }

    public static float cylinderY(Vector3fc p, float radius, float halfHeight) {
        float dxz = (float) java.lang.Math.sqrt(p.x() * p.x() + p.z() * p.z()) - radius;
        float dy = java.lang.Math.abs(p.y()) - halfHeight;
        float ox = java.lang.Math.max(dxz, 0.0f);
        float oy = java.lang.Math.max(dy, 0.0f);
        return (float) java.lang.Math.sqrt(ox * ox + oy * oy) + java.lang.Math.min(java.lang.Math.max(dxz, dy), 0.0f);
    }

    public static float torusY(Vector3fc p, float majorRadius, float minorRadius) {
        float qx = (float) java.lang.Math.sqrt(p.x() * p.x() + p.z() * p.z()) - majorRadius;
        return (float) java.lang.Math.sqrt(qx * qx + p.y() * p.y()) - minorRadius;
    }
}
