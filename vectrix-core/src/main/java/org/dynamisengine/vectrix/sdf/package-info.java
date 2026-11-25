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
 * Exact signed distance functions of basic shapes (sphere, box, capsule, cylinder, torus): negative inside, zero on
 * the surface, positive outside.
 * <p>
 * <b>Contract.</b> Threading: the classes are stateless and safe to call from any thread. Allocation: no method
 * allocates. Determinism: the functions use only arithmetic, comparisons and square roots, so they give the same
 * bits on every platform for the same configuration. Errors: nothing is validated; NaN inputs give NaN results, and
 * negative sizes give meaningless distances. Precision and naming: {@code Sdf3f} works in float and {@code Sdf3d}
 * in double.
 */
package org.dynamisengine.vectrix.sdf;
