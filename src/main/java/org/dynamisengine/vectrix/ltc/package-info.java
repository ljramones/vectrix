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
 * Linearly Transformed Cosine (LTC) table sampling and area-light form-factor evaluation helpers. You supply the
 * fitted table; {@code LtcTable*} samples it (corner-aligned bilinear, layout in its class documentation) and
 * {@code LtcEval*} integrates the transformed light polygon in the shading frame ({@code +z} is the normal).
 * <p>
 * <b>Contract.</b> Threading: the classes are stateless static functions, safe to call from any thread.
 * Allocation: no method allocates, except {@code formFactorRectClipped} and {@code formFactorPolygonClipped}, which
 * create a small clipping object per call. Determinism: the edge integrals and cap formulas use
 * {@code AccurateMath}, so results match across platforms under {@code -Dvectrix.strictMath}. Errors: null or short
 * arrays, table sizes below 2, NaN table coordinates and polygon counts below 3 throw
 * {@code IllegalArgumentException} before any output is written; form factors are clamped to {@code [0, 1]}.
 * Precision and naming: {@code ...d} classes are double, {@code ...f} twins are float with the same methods
 * ({@code LtcEvalf.formFactorDisc} computes in double).
 */
package org.dynamisengine.vectrix.ltc;
