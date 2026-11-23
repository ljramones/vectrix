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
package org.dynamisengine.vectrix.core;

/**
 * Helpers for the matrix {@code PROPERTY_*} flags. A matrix built from a quaternion is a pure rotation only when
 * the quaternion has unit length: a quaternion of length {@code |q|} gives {@code |q|² R}. The orthonormal flag,
 * which makes {@code invert()} a transpose, is set only for unit quaternions, to within the precision of their
 * components.
 */
final class Flags {
    private Flags() {
    }

    static boolean isUnit(float x, float y, float z, float w) {
        double n = (double) x * x + (double) y * y + (double) z * z + (double) w * w;
        return java.lang.Math.abs(n - 1.0) <= 1e-6;
    }

    static boolean isUnit(double x, double y, double z, double w) {
        return java.lang.Math.abs(x * x + y * y + z * z + w * w - 1.0) <= 1e-12;
    }

    static boolean isUnit(Quaternionfc q) {
        return isUnit(q.x(), q.y(), q.z(), q.w());
    }

    static boolean isUnit(Quaterniondc q) {
        return isUnit(q.x(), q.y(), q.z(), q.w());
    }
}
