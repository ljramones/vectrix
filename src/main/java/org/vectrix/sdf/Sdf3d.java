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

import org.vectrix.core.Vector3dc;

public final class Sdf3d {
    private Sdf3d() {
    }

    public static double sphere(Vector3dc p, double radius) { return p.length() - radius; }

    public static double box(Vector3dc p, Vector3dc halfExtents) {
        double qx = java.lang.Math.abs(p.x()) - halfExtents.x();
        double qy = java.lang.Math.abs(p.y()) - halfExtents.y();
        double qz = java.lang.Math.abs(p.z()) - halfExtents.z();
        double ox = java.lang.Math.max(qx, 0.0);
        double oy = java.lang.Math.max(qy, 0.0);
        double oz = java.lang.Math.max(qz, 0.0);
        double outside = java.lang.Math.sqrt(ox * ox + oy * oy + oz * oz);
        double inside = java.lang.Math.min(java.lang.Math.max(qx, java.lang.Math.max(qy, qz)), 0.0);
        return outside + inside;
    }

    public static double capsule(Vector3dc p, Vector3dc a, Vector3dc b, double radius) {
        double pax = p.x() - a.x(), pay = p.y() - a.y(), paz = p.z() - a.z();
        double bax = b.x() - a.x(), bay = b.y() - a.y(), baz = b.z() - a.z();
        double h = java.lang.Math.max(0.0, java.lang.Math.min(1.0, (pax * bax + pay * bay + paz * baz) / (bax * bax + bay * bay + baz * baz)));
        double dx = pax - bax * h, dy = pay - bay * h, dz = paz - baz * h;
        return java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz) - radius;
    }

    public static double cylinderY(Vector3dc p, double radius, double halfHeight) {
        double dxz = java.lang.Math.sqrt(p.x() * p.x() + p.z() * p.z()) - radius;
        double dy = java.lang.Math.abs(p.y()) - halfHeight;
        double ox = java.lang.Math.max(dxz, 0.0);
        double oy = java.lang.Math.max(dy, 0.0);
        return java.lang.Math.sqrt(ox * ox + oy * oy) + java.lang.Math.min(java.lang.Math.max(dxz, dy), 0.0);
    }

    public static double torusY(Vector3dc p, double majorRadius, double minorRadius) {
        double qx = java.lang.Math.sqrt(p.x() * p.x() + p.z() * p.z()) - majorRadius;
        return java.lang.Math.sqrt(qx * qx + p.y() * p.y()) - minorRadius;
    }
}
