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
 * Curve evaluation and reparameterization utilities for animation, camera paths, and simulation tracks: the scalar
 * cubic kernels ({@link org.dynamisengine.vectrix.curve.CubicCurveMath}) behind the curve classes in the
 * {@code scalar}, {@code vec2}, {@code vec3} and {@code vec4} subpackages, and arc-length tables for constant-speed
 * motion along 3D segments ({@link org.dynamisengine.vectrix.curve.CurveReparameterizer3f}).
 * <p>
 * <b>Contract.</b> Threading: the classes are static and stateless, safe from any thread. Allocation: the kernels
 * and table lookups do not allocate; each arc-length table build allocates two temporary vectors. Determinism: plain
 * float or double arithmetic and {@code sqrt}, no transcendental functions, so results are the same bits on every
 * platform. Errors: {@code t} is not clamped; table builders and lookups throw {@code IllegalArgumentException}
 * for a sample count below 1 or a table that is too short. Precision and naming: {@code CubicCurveMath} has float
 * and double overloads; the {@code ...3f}/{@code ...3d} reparameterizers work on float and double 3D curves.
 */
package org.dynamisengine.vectrix.curve;
