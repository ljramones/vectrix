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
 * Core vector, matrix and quaternion types (JOML-derived), the scalar math they use ({@link
 * org.dynamisengine.vectrix.core.Math}, {@link org.dynamisengine.vectrix.core.AccurateMath},
 * {@link org.dynamisengine.vectrix.core.ReproducibleMath}), and the library-wide configuration
 * ({@link org.dynamisengine.vectrix.core.CoreConfig}, {@link org.dynamisengine.vectrix.core.DepthRange}).
 * <p>
 * Conventions:
 * <ul>
 * <li>right-handed coordinates; angles in radians</li>
 * <li>column-major matrices: {@code mCR} is column C, row R</li>
 * <li>fluent calls post-multiply: {@code a.mul(b)} is {@code a * b}, applied to a vector as {@code a * (b * v)}</li>
 * <li>each mutable type has a read-only view interface ending in {@code c} (a view, not a copy) and, for matrices,
 * a stack class with {@code pushMatrix}/{@code popMatrix}</li>
 * </ul>
 * <p>
 * <b>Contract.</b> Threading: mutable math objects are not thread-safe; concurrent reads of an object that no thread
 * is writing are safe. The static utility classes keep no shared mutable state and are safe from any thread. The math
 * mode set with {@code CoreConfig.runWithMathMode} applies to the calling task only. Allocation: arithmetic,
 * transform and query methods write into {@code this} or a caller-supplied {@code dest} and do not allocate;
 * constructors, {@code toString}, {@code clone}, serialization and the stack classes do. Determinism: every
 * platform transcendental goes through {@code AccurateMath}, so results match bitwise across platforms under
 * {@code -Dvectrix.strictMath=true} when the settings recorded by {@code CoreConfig.determinismFingerprint()} are
 * equal; {@code ReproducibleMath} matches in any configuration; {@code -Dvectrix.fastmath} replaces {@code sin},
 * {@code cos} and {@code atan2} with approximations. Errors: in {@code MathMode.FAST} (the default) values are not
 * validated and NaN propagates; in {@code MathMode.STRICT}, operations with a documented input domain (unit or pure
 * quaternion, unit axis) throw {@code IllegalArgumentException} outside it. Matrix row and column indices out of
 * range throw {@code IndexOutOfBoundsException} and vector component indices {@code IllegalArgumentException}; the
 * {@code ...Exact} methods of the long vectors throw
 * {@code ArithmeticException} on overflow, and the raw-address methods throw {@code UnsupportedOperationException}
 * when neither native access nor {@code sun.misc.Unsafe} is available. Precision and naming: the last letter names
 * the element type ({@code f} float, {@code d} double, {@code i} int, {@code L} long); methods with a {@code dest}
 * parameter leave {@code this} and the other arguments unchanged, and {@code dest} may alias any of them.
 */
package org.dynamisengine.vectrix.core;
