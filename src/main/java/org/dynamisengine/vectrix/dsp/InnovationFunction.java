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
package org.dynamisengine.vectrix.dsp;

/**
 * Forms the innovation (the measurement residual) of {@link ExtendedKalman}: the difference between a measurement
 * and the measurement predicted from the state. The default is the plain difference {@code z - ẑ}; supply this
 * function when some measured values live on a circle or another manifold, for example to wrap a bearing difference
 * into {@code [-π, π]}:
 * <pre>{@code
 * (z, predicted, y) -> {
 *     y[0] = z[0] - predicted[0];                                       // range
 *     y[1] = Math.IEEEremainder(z[1] - predicted[1], 2 * Math.PI);      // bearing, wrapped
 * }
 * }</pre>
 * It must not keep the arrays.
 *
 * @since 3.0.0
 */
@FunctionalInterface
public interface InnovationFunction {

    /**
     * Writes the innovation.
     *
     * @param z the {@code m} measured values, not to be modified
     * @param predicted the {@code m} predicted measurement values {@code h(x)}, not to be modified
     * @param innovation receives the {@code m} innovation values; a different array from the other two
     */
    void apply(double[] z, double[] predicted, double[] innovation);
}
