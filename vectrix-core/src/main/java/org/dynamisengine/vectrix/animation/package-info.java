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
 * Animation math: skeletal pose kernels over flat per-joint arrays (forward kinematics and its inverse, rotation and
 * vector blending, additive layers); quaternion key unrolling, SQUAD, spherical Bezier and Kim-Kim-Shin cumulative
 * B-splines over key arrays; the weighted geodesic (Karcher) mean of rotations; and an exact critically damped
 * rotation spring. Rig and IK policy belong to Animis. Quaternions are flat {@code x, y, z, w} arrays.
 * <p>
 * <b>Contract.</b> Threading: the classes are stateless static utilities and safe to call from any thread on separate
 * output arrays. Allocation: {@code PoseKernels} never allocates; {@code QuaternionCurves} and
 * {@code QuaternionSpring} use a per-thread scratch array that is allocated on the first call in each thread.
 * Determinism: transcendentals go through {@link org.dynamisengine.vectrix.core.AccurateMath}, so results match
 * across platforms under {@code -Dvectrix.strictMath}. Errors: the array methods take a count but no offsets, start
 * at index 0 and rely on Java's array bounds checks; the hierarchy methods ({@code localToModel},
 * {@code modelToLocal}) instead check their array sizes and every parent index up front and throw
 * {@link IllegalArgumentException} before writing anything; other values are not validated. Precision and naming: classes ending in
 * {@code f} store {@code float} and compute in {@code double}, rounding once per output; classes ending in {@code d}
 * are their {@code double} twins and use the same formulas.
 */
package org.dynamisengine.vectrix.animation;
