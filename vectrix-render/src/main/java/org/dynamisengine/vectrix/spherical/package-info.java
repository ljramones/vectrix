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
 * Spherical functions other than SH: spherical Gaussians (isotropic and anisotropic) for lighting lobes, and
 * direction mappings (octahedral, hemi-octahedral, cubemap) with cubemap texel solid angles for probe filtering.
 * <p>
 * <b>Contract.</b> Threading: the classes are stateless static functions, safe to call from any thread.
 * Allocation: no method allocates; results go into the caller's {@code dest} objects. Determinism: transcendentals
 * use {@code AccurateMath}, so results match across platforms under {@code -Dvectrix.strictMath}. Errors:
 * {@code DirectionMappings*.cubeDirection} throws {@code IllegalArgumentException} for a face outside 0..5;
 * {@code SphericalGaussians*.fit} reads its arrays from index 0 and relies on Java's bounds checks; other inputs
 * are not validated, so a zero direction or NaN gives NaN. Precision and naming: {@code ...d} classes are double,
 * {@code ...f} twins are float with the same methods ({@code SphericalGaussiansf.fit} sums in double).
 */
package org.dynamisengine.vectrix.spherical;
