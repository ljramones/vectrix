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
 * Numerical linear algebra for small fixed-size problems: symmetric eigen-decomposition, signed SVD, polar and QR
 * decompositions, Cholesky/LDLᵀ/LU solvers and condition numbers, SO(3)/SE(3) exponential and logarithm maps, and
 * least-squares fitting. All kernels compute in double precision; the float classes convert at the boundary.
 * Matrices are the column-major {@code core} types, except the row-major array of {@code LeastSquares*.solve}.
 * <p>
 * <b>Contract.</b> Threading: the classes are stateless static functions that use a per-thread workspace, so they
 * are safe to call from any thread. Allocation: no method allocates after a thread's first call, except the
 * {@code LeastSquares*.solve} overloads without a scratch argument, which allocate {@code O(rows · cols)} scratch per
 * call; the overloads that take scratch do not allocate. Every input is read before an output is written, so outputs may alias inputs. Determinism: the
 * kernels sum in a fixed order; the SO(3) and SE(3) maps use {@code AccurateMath}, so they give the same bits across
 * platforms under {@code -Dvectrix.strictMath}. Errors: {@code LeastSquares*} throws
 * {@link java.lang.IllegalArgumentException} for bad sizes or counts; the factorizations and solves report a
 * singular or indefinite matrix by returning {@code false} (the {@code Solvers*} methods then leave their outputs
 * unchanged; so does {@code LeastSquares*.solve} with {@code x}); other methods do not validate, and NaN
 * inputs give NaN results. Precision and naming: {@code ...d} classes take and return double
 * types; their {@code ...f} twins take float types, compute in double and round each result once.
 */
package org.dynamisengine.vectrix.linalg;
