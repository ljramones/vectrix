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
package org.dynamisengine.vectrix.core;

/**
 * Execution mode for math kernels, selected process-wide with {@link CoreConfig#setMathMode} or
 * {@code -Dvectrix.math.mode}.
 * <p>
 * {@link #STRICT} is one contract, applied the same way by every operation that consults the mode:
 * <ol>
 * <li><b>Validation.</b> An operation with a documented input domain (unit quaternion, unit axis, pure
 * quaternion) throws {@link IllegalArgumentException} for inputs outside it, NaN included. A quaternion or
 * axis counts as unit when {@code |x·x − 1| <= }{@link CoreConfig#STRICT_UNIT_TOLERANCE_F} (float) or
 * {@link CoreConfig#STRICT_UNIT_TOLERANCE_D} (double); see {@link CoreConfig#withinStrictUnitTolerance(float)}.</li>
 * <li><b>No repair.</b> STRICT never renormalises, clamps or substitutes values. For valid inputs, element-wise
 * results are bitwise identical to the scalar {@link #FAST} path.</li>
 * <li><b>Fixed dispatch.</b> Batch kernels always take their scalar path, whatever SIMD support or
 * SIMD flags are present, so results do not depend on the host's vector hardware.</li>
 * </ol>
 * The {@code blas}, {@code nn}, {@code fft} and {@code dsp} kernels do not consult the mode: they sum in a fixed
 * order in both modes.
 * STRICT does <em>not</em> yet make {@code java.lang.Math} transcendentals ({@code sin}, {@code cos}, ...)
 * reproducible across CPU architectures, nor override {@code -Djoml.fastmath} / {@code -Djoml.useMathFma}. Those
 * are covered by the determinism contract (review finding M-12).
 */
public enum MathMode {
    /**
     * Prioritize throughput: batch kernels may take SIMD paths, reductions use multiple independent partial
     * sums, and out-of-domain inputs are not checked.
     */
    FAST,
    /**
     * Validate inputs, never repair them, use fixed scalar dispatch and accurate fixed-order reductions. See the
     * class documentation for the full contract.
     */
    STRICT
}
