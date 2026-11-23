/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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
package org.dynamisengine.vectrix.affine;

import org.dynamisengine.vectrix.soa.TransformSoA;

/**
 * Batch kernels for transform processing.
 *
 * @deprecated moved to {@link org.dynamisengine.vectrix.soa.TransformKernels}, next to the {@link TransformSoA} it
 *             works on ({@code affine} must not depend on {@code soa})
 */
@Deprecated(since = "2.6.0", forRemoval = true)
public final class TransformKernels {

    private TransformKernels() {
    }

    /**
     * Forwards to {@link org.dynamisengine.vectrix.soa.TransformKernels#composeBatch}.
     *
     * @param parents the parent transforms
     * @param locals the local transforms
     * @param out receives {@code parents[i] * locals[i]}; may be {@code parents} or {@code locals}
     * @param count the number of transforms
     */
    public static void composeBatch(TransformSoA parents, TransformSoA locals, TransformSoA out, int count) {
        org.dynamisengine.vectrix.soa.TransformKernels.composeBatch(parents, locals, out, count);
    }
}
