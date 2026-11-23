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
 * Renderer-oriented math kernels (LUT generation, interpolation, and visibility approximations)
 * that remain renderer-agnostic and allocation-conscious. {@code AtmosphereParams} and
 * {@code TransmittanceLutBuilder} are deprecated in favour of the {@code atmosphere} package.
 * <p>
 * <b>Contract.</b> Threading: the functions are static and keep no shared state, and {@code SssProfile} and
 * {@code AtmosphereParams} are immutable, so all are safe to use from any thread. Allocation: the interpolation and
 * cone functions and {@code SssProfile.evaluate} do not allocate; the {@code SssProfile} constructor copies its
 * arrays and {@code SssLutBuilder.build} allocates temporary arrays, so run the builders at load time.
 * Determinism: transcendentals use {@code AccurateMath}, so results match across platforms under
 * {@code -Dvectrix.strictMath}. Errors: null or short arrays and out-of-range sizes or parameters throw
 * {@code IllegalArgumentException}; interpolation weights are not clamped. Precision and naming: {@code ...d}
 * classes are double, {@code ...f} twins are float; the unsuffixed classes are float.
 */
package org.dynamisengine.vectrix.renderingmath;
