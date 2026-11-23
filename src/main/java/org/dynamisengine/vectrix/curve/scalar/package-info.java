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
/**
 * Bézier, Hermite, Catmull–Rom and uniform B-spline curves over scalar (one-dimensional) values, for animation
 * channels and easing curves: evaluation and derivatives. Each curve has a float and a double twin
 * ({@code ...f}/{@code ...d}) with the same methods.
 * <p>
 * <b>Contract.</b> Threading: the Bézier, Hermite and Catmull–Rom classes are static and stateless; a
 * {@code UniformBSpline...} does not change itself but keeps the caller's array of control values, so it is safe to
 * share between threads while nobody writes that array. Allocation: none (the B-spline constructors allocate only
 * the object). Determinism: plain float or double arithmetic in a fixed order, no transcendental functions, so
 * results are the same bits on every platform. Errors: {@code t} is not clamped (values outside {@code [0, 1]}
 * extrapolate); a bad B-spline segment index or fewer than 4 control values throw {@code IllegalArgumentException};
 * the batch methods do not check array sizes up front. Precision and naming: {@code ...f} classes are float,
 * {@code ...d} double.
 */
package org.dynamisengine.vectrix.curve.scalar;
