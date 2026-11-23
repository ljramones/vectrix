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

/**
 * Normalized-device depth conventions. Every function that produces or reads NDC depth takes one of these, so a
 * matrix built for one range is never read back with another's formulas.
 *
 * @since 2.3.0
 *
 * @deprecated moved to {@link org.dynamisengine.vectrix.core.DepthRange} (core must not depend on camera)
 */
@Deprecated(since = "2.6.0", forRemoval = true)
public enum DepthRange {
    /** OpenGL: near plane at −1, far plane at +1. */
    NEGATIVE_ONE_TO_ONE(-1.0, 1.0),
    /** Vulkan / Direct3D / Metal: near at 0, far at 1. */
    ZERO_TO_ONE(0.0, 1.0),
    /** Reverse-Z: near at 1, far (or infinity) at 0. Best depth precision with a floating-point depth buffer. */
    REVERSED_ZERO_TO_ONE(1.0, 0.0);

    private final double near;
    private final double far;

    DepthRange(double near, double far) {
        this.near = near;
        this.far = far;
    }

    /**
     * Returns the NDC depth of the near plane.
     *
     * @return the near-plane NDC depth
     */
    public double nearNdc() {
        return near;
    }

    /**
     * Returns the NDC depth of the far plane (of infinity, for an infinite projection).
     *
     * @return the far-plane NDC depth
     */
    public double farNdc() {
        return far;
    }

    /**
     * Converts this constant to the core enum.
     *
     * @return the same convention as a {@link org.dynamisengine.vectrix.core.DepthRange}
     */
    public org.dynamisengine.vectrix.core.DepthRange toCore() {
        // a switch, not values()[ordinal()]: values() copies its array on every call
        switch (this) {
            case NEGATIVE_ONE_TO_ONE:
                return org.dynamisengine.vectrix.core.DepthRange.NEGATIVE_ONE_TO_ONE;
            case ZERO_TO_ONE:
                return org.dynamisengine.vectrix.core.DepthRange.ZERO_TO_ONE;
            default:
                return org.dynamisengine.vectrix.core.DepthRange.REVERSED_ZERO_TO_ONE;
        }
    }
}
