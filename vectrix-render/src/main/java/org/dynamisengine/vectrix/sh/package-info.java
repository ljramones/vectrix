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
/**
 * Real spherical harmonics (SH) for irradiance and light probes: the basis ({@link ShBasis}), RGB coefficient
 * storage up to order 2 or 3 ({@link ShCoeffs9f}, {@link ShCoeffs16f}), projection of radiance samples
 * ({@link ShProjection}), Lambert convolution and evaluation ({@link ShConvolution}), cheap L1 irradiance
 * ({@link ShL1}), rotation ({@link ShRotation}), windowing ({@link ShWindowing}) and products ({@link ShProduct}).
 * Coefficient {@code i} of basis function {@code Y(l, m)} is at {@code i = l * (l + 1) + m}, channel {@code ch} at
 * {@code c[3 * i + ch]}.
 * <p>
 * <b>Contract.</b> Threading: the functions are static and keep no shared mutable state ({@code ShRotation} and
 * {@code ShProduct} use per-thread scratch), so they are safe from any number of threads; a coefficient object is
 * mutable and must not be written by two threads at once. Allocation: no function allocates, apart from the
 * per-thread scratch created on the first {@code ShRotation} or {@code ShProduct} call in a thread; {@code ShBasis},
 * {@code ShProjection} and {@code ShConvolution.evaluateL2} take caller-owned scratch and output arrays.
 * Determinism: results are plain arithmetic on fixed constants, except the {@code ShWindowing} weights and
 * {@code ShL1.irradianceNonLinear}, which use {@code AccurateMath}, so all results match across platforms under
 * {@code -Dvectrix.strictMath}; the {@code ShProduct} and {@code ShRotation} tables are computed with arithmetic
 * and square roots only. Errors: short scratch or output arrays throw {@code IllegalArgumentException} in
 * {@code ShBasis}, {@code ShProjection} and {@code ShConvolution} before anything is written; other functions rely
 * on array bounds checks; directions are not normalized or validated, and the math mode is not consulted.
 * Precision and naming: {@code f} classes and overloads are float, {@code d} are double; {@code 9} and {@code 16}
 * name the coefficients per channel (orders 2 and 3), and methods suffixed {@code L2}/{@code L3} work on those
 * orders.
 */
package org.dynamisengine.vectrix.sh;
