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
 * Affine and rigid transforms: the column-major 3x4 matrices {@link org.dynamisengine.vectrix.affine.Affine4f} and
 * {@link org.dynamisengine.vectrix.affine.Affine4d}, the translation-rotation-scale
 * {@link org.dynamisengine.vectrix.affine.Transformf}, the rigid
 * {@link org.dynamisengine.vectrix.affine.RigidTransformf}, the dual quaternion
 * {@link org.dynamisengine.vectrix.affine.DualQuatTransformf} (with screw interpolation and skinning blends), and
 * the row-major packed 3x4 arrays {@link org.dynamisengine.vectrix.affine.PackedAffineArray} with their batch
 * kernels {@link org.dynamisengine.vectrix.affine.PackedAffineKernels} for GPU instance data.
 * <p>
 * <b>Contract.</b> Threading: the transform classes are mutable and not thread-safe; the static kernels keep no
 * state and are safe to call from any thread on disjoint outputs. Allocation: no method allocates, except the
 * constructors. Determinism:
 * there are no SIMD paths; the {@code Affine4} products round as {@code -Dvectrix.useMathFma} selects, and the
 * transcendentals of {@code DualQuatTransform.sclerp} use {@code AccurateMath}, so they match across platforms
 * under {@code -Dvectrix.strictMath}. Errors: values are not validated in either math mode (rotations are expected
 * to be unit quaternions and are used as given). Arguments are: the {@code PackedAffineArray} constructors and the
 * {@code PackedAffineKernels} check sizes, indices and offsets up front, {@code invertRigidFast} rejects a transform
 * with scale, and the {@code DualQuatTransform} blends reject an empty or zero-weight blend, all with
 * {@code IllegalArgumentException} before writing anything; {@code PackedAffineArray.set} throws
 * {@code IndexOutOfBoundsException} for an index out of range. Precision and naming: classes
 * ending in {@code f} are float and their twins ending in {@code d} are double ({@code DualQuatTransformf} stores
 * floats but computes products, interpolation and blends in double); the packed arrays and kernels are float.
 */
package org.dynamisengine.vectrix.affine;
