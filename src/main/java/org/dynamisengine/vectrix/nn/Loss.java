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
package org.dynamisengine.vectrix.nn;

/**
 * Cross-entropy loss over one row of logits.
 * <p>
 * The loss is {@code logSumExp(x) - x[target]}, so it never forms a probability that could underflow; the gradient
 * is {@code dLoss·(softmax(x) - onehot(target))}. Both use {@link Softmax}'s kernels, so results are the same bits
 * everywhere. Nothing allocates.
 *
 * @since 2.5.0
 */
public final class Loss {

    private Loss() {
    }

    /**
     * The cross-entropy {@code -log softmax(x)[target]}.
     *
     * @param scratch {@code n} floats of working memory
     * @return the loss
     */
    public static float crossEntropy(float[] logits, int off, int n, int target, float[] scratch, int scratchOff) {
        if (target < 0 || target >= n) {
            throw new IllegalArgumentException("target " + target + " outside [0, " + n + ")");
        }
        float lse = Softmax.logSumExp(logits, off, n, scratch, scratchOff);
        return lse - logits[off + target];
    }

    /** The gradient {@code dx = dLoss·(softmax(x) - onehot(target))}; {@code dx} may be {@code logits}. */
    public static void crossEntropyBackward(float[] logits, int off, int n, int target, float dLoss, float[] dx,
            int dxOff) {
        if (target < 0 || target >= n) {
            throw new IllegalArgumentException("target " + target + " outside [0, " + n + ")");
        }
        Softmax.softmax(logits, off, dx, dxOff, n);
        dx[dxOff + target] -= 1f;
        for (int i = 0; i < n; i++) {
            dx[dxOff + i] *= dLoss;
        }
    }
}
