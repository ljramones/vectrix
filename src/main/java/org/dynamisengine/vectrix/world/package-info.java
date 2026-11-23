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
 * Large-world precision: {@link org.dynamisengine.vectrix.world.WorldPos} (an integer cell plus a local offset, exact
 * at any distance from the origin) and camera-relative conversion of world transforms to single precision for GPU
 * upload, rounding only once.
 * <p>
 * <b>Contract.</b> Threading: {@code WorldPos} is mutable and not thread-safe; the {@code CameraRelative} functions
 * are static and stateless, safe from any thread on separate outputs. Allocation: nothing allocates except the
 * {@code WorldPos} constructors. Determinism: only exact integer arithmetic and basic floating-point operations are
 * used, with no transcendental functions, so results are the same bits on every platform. Errors: positions with
 * different cell sizes throw {@code IllegalArgumentException}; cell overflow and offsets that cannot be rebased
 * throw {@code ArithmeticException}; the {@code CameraRelative} array functions take a count, start at index 0 and
 * rely on the array bounds checks. Precision and naming: positions are a {@code long} cell plus a double offset; the
 * camera-relative outputs are float, in the row-major packed 3×4 layout.
 */
package org.dynamisengine.vectrix.world;
