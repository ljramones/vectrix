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
 * Inverse-kinematics kernels: analytic two-bone, FABRIK and CCD passes over flat position chains, a damped
 * least-squares step, swing-twist / cone / hinge joint limits, and aim rotations. They are pure functions of vectors,
 * quaternions and arrays; iteration counts, tolerances and limits are always parameters. Rigs, poses, chain
 * definitions and solve policy belong to Animis (see {@code docs/architecture/math-gpu-ownership.md} in the Dynamis
 * repository).
 * <p>
 * <b>Contract.</b> Threading: the classes are stateless static utilities and safe to call from any thread on separate
 * output objects and arrays. Allocation: the solvers use per-thread scratch arrays that are allocated on the first
 * call in each thread ({@code Fabrik} also reallocates its length array for chains of more than 32 bones); otherwise
 * nothing is allocated. Determinism: transcendentals go through {@link org.dynamisengine.vectrix.core.AccurateMath},
 * so results match across platforms under {@code -Dvectrix.strictMath}. Errors: no method validates its inputs or
 * throws; degenerate geometry (zero-length bones, parallel axes) takes the documented fallback, and the position
 * arrays take a joint count but no offsets, start at index 0 and rely on Java's array bounds checks. Precision and
 * naming: classes ending in {@code f} take and return {@code float} and evaluate in {@code double}; classes ending in
 * {@code d} are their {@code double} twins and share the same double-precision helpers.
 */
package org.dynamisengine.vectrix.ik;
