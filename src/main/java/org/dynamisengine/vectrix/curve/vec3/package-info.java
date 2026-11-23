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
 * Bézier, Hermite, Catmull–Rom and uniform B-spline curves over 3D points ({@code Vector3f}/{@code Vector3d}), for
 * camera paths, splines in space and root motion: evaluation and derivatives. The arc-length tables for these
 * curves are in {@link org.dynamisengine.vectrix.curve.CurveReparameterizer3f} and
 * {@link org.dynamisengine.vectrix.curve.CurveReparameterizer3d}.
 * Each curve has a float and a double twin ({@code ...f}/{@code ...d}) with the same methods.
 * <p>
 * <b>Contract.</b> Threading: the Bézier, Hermite and Catmull–Rom classes are static and stateless; a
 * {@code UniformBSpline...} is immutable after construction; all are safe from any thread. Allocation: only the
 * B-spline constructors allocate. Determinism: plain float or double arithmetic in a fixed order, no transcendental
 * functions, so results are the same bits on every platform. Errors: {@code t} is not clamped (values outside
 * {@code [0, 1]} extrapolate); a bad B-spline segment index or fewer than 4 control points throw
 * {@code IllegalArgumentException}; the batch methods check their arrays, offsets and count up front and throw
 * {@code IllegalArgumentException} before writing anything. Precision and naming: {@code ...f} classes are float,
 * {@code ...d} double.
 */
package org.dynamisengine.vectrix.curve.vec3;
