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
 * Camera math: exact projections for every depth range (including reverse-Z and infinite far planes), depth
 * conversions, clip-space corrections, oblique near-plane clipping and TAA jitter; stable cascaded-shadow fitting;
 * clustered / froxel lighting grids; and the thin-lens physical camera. Conventions: right-handed view space looking
 * down {@code −z}, column-major matrices as in {@code core}, and an explicit
 * {@link org.dynamisengine.vectrix.core.DepthRange} wherever depth values are produced or read.
 * <p>
 * <b>Contract.</b> Threading: the classes are stateless static utilities and safe to call from any thread.
 * Allocation: no method allocates, except {@code obliqueNearPlane}, which allocates a per-thread scratch matrix on
 * its first call in each thread. Determinism: transcendentals go through
 * {@link org.dynamisengine.vectrix.core.AccurateMath}, so results match across platforms under
 * {@code -Dvectrix.strictMath}. Errors: values are not validated, and out-of-domain inputs (a zero near plane, a
 * degenerate projection) give infinite or NaN results; {@code practicalSplits} relies on Java's array bounds checks,
 * and {@code haltonJitter} throws {@link IllegalArgumentException} for a negative sample index. Precision and naming:
 * classes ending in {@code f} take and return {@code float} and mostly compute in {@code double} internally, rounding
 * once; classes ending in {@code d} are their {@code double} twins.
 */
package org.dynamisengine.vectrix.camera;
