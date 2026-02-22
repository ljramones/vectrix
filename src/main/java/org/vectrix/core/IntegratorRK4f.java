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
package org.vectrix.core;

/**
 * Allocation-free fourth-order Runge-Kutta ODE integration for single-precision state arrays.
 */
public final class IntegratorRK4f {
    private IntegratorRK4f() {
    }

    /**
     * Integrate one RK4 step.
     * <p>
     * Scratch layout is contiguous and fixed:
     * <ul>
     *   <li>{@code scratch[scratchOffset + 0*stateSize .. +1*stateSize)} = k1</li>
     *   <li>{@code scratch[scratchOffset + 1*stateSize .. +2*stateSize)} = k2</li>
     *   <li>{@code scratch[scratchOffset + 2*stateSize .. +3*stateSize)} = k3</li>
     *   <li>{@code scratch[scratchOffset + 3*stateSize .. +4*stateSize)} = k4</li>
     *   <li>{@code scratch[scratchOffset + 4*stateSize .. +5*stateSize)} = temporary state</li>
     * </ul>
     * <p>
     * {@code dest} may alias {@code state}, including identical array+offset, for in-place integration.
     */
    public static void step(OdeDerivativef derivative, float t, float dt, float[] state, int stateOffset, int stateSize,
        float[] scratch, int scratchOffset, float[] dest, int destOffset) {
        if (derivative == null || state == null || scratch == null || dest == null) {
            throw new IllegalArgumentException("null");
        }
        if (stateSize <= 0) {
            throw new IllegalArgumentException("stateSize");
        }
        checkBounds(state.length, stateOffset, stateSize, "state");
        checkBounds(dest.length, destOffset, stateSize, "dest");
        checkBounds(scratch.length, scratchOffset, stateSize * 5, "scratch");

        int k1 = scratchOffset;
        int k2 = k1 + stateSize;
        int k3 = k2 + stateSize;
        int k4 = k3 + stateSize;
        int tmp = k4 + stateSize;

        float h2 = dt * 0.5f;
        float sixth = dt / 6.0f;

        derivative.compute(t, state, stateOffset, scratch, k1);
        for (int i = 0; i < stateSize; i++) {
            scratch[tmp + i] = state[stateOffset + i] + h2 * scratch[k1 + i];
        }

        derivative.compute(t + h2, scratch, tmp, scratch, k2);
        for (int i = 0; i < stateSize; i++) {
            scratch[tmp + i] = state[stateOffset + i] + h2 * scratch[k2 + i];
        }

        derivative.compute(t + h2, scratch, tmp, scratch, k3);
        for (int i = 0; i < stateSize; i++) {
            scratch[tmp + i] = state[stateOffset + i] + dt * scratch[k3 + i];
        }

        derivative.compute(t + dt, scratch, tmp, scratch, k4);
        for (int i = 0; i < stateSize; i++) {
            float k1v = scratch[k1 + i];
            float k2v = scratch[k2 + i];
            float k3v = scratch[k3 + i];
            float k4v = scratch[k4 + i];
            dest[destOffset + i] = state[stateOffset + i] + sixth * (k1v + 2.0f * k2v + 2.0f * k3v + k4v);
        }
    }

    private static void checkBounds(int length, int offset, int size, String name) {
        if (offset < 0 || size < 0 || offset + size > length) {
            throw new IllegalArgumentException(name);
        }
    }
}
