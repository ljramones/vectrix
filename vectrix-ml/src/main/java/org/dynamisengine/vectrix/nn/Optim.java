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

import org.dynamisengine.vectrix.core.ReproducibleMath;
/**
 * Parameter updates: AdamW and SGD with momentum, element-wise over a parameter range, and gradient-norm clipping.
 * <p>
 * Each element is updated independently with a fixed sequence of float operations, so an update is reproducible and
 * may be split across threads by element ranges. Bias corrections are computed in double with
 * {@link org.dynamisengine.vectrix.core.ReproducibleMath}'s reproducible functions. Nothing allocates.
 *
 * @since 2.5.0
 */
public final class Optim {

    private Optim() {
    }

    /**
     * One AdamW step (Loshchilov and Hutter: decoupled weight decay):
     * {@code m = β1·m + (1 - β1)·g}, {@code v = β2·v + (1 - β2)·g²},
     * {@code p = p·(1 - lr·wd) - lr·(m/(1 - β1^t)) / (sqrt(v/(1 - β2^t)) + eps)}.
     *
     *
     * @param p the parameters, updated in place
     * @param pOff the offset in {@code p}
     * @param g the gradients
     * @param gOff the offset in {@code g}
     * @param m the first-moment estimates, updated in place (start at 0)
     * @param mOff the offset in {@code m}
     * @param v the second-moment estimates, updated in place (start at 0)
     * @param vOff the offset in {@code v}
     * @param n the number of parameters
     * @param lr the learning rate
     * @param beta1 the first-moment decay {@code β1}, typically 0.9
     * @param beta2 the second-moment decay {@code β2}, typically 0.999
     * @param eps the term added to the denominator, typically 1e-8
     * @param weightDecay the decoupled weight decay {@code wd}, applied as {@code p·(1 - lr·wd)}
     * @param step the 1-based step number {@code t}
     * @throws IllegalArgumentException if {@code step < 1}, {@code n < 0} or an array is too short for its offset;
     *         nothing is written then
     */
    public static void adamW(float[] p, int pOff, float[] g, int gOff, float[] m, int mOff, float[] v, int vOff, int n,
            float lr, float beta1, float beta2, float eps, float weightDecay, long step) {
        if (step < 1) {
            throw new IllegalArgumentException("step must be >= 1: " + step);
        }
        range(p, pOff, n, "p");
        range(g, gOff, n, "g");
        range(m, mOff, n, "m");
        range(v, vOff, n, "v");
        float bc1 = (float) (1.0 - ReproducibleMath.pow(beta1, step));
        float bc2 = (float) (1.0 - ReproducibleMath.pow(beta2, step));
        float a1 = 1f - beta1;
        float a2 = 1f - beta2;
        float decay = 1f - lr * weightDecay;
        for (int i = 0; i < n; i++) {
            float gi = g[gOff + i];
            float mi = Math.fma(beta1, m[mOff + i], a1 * gi);
            float vi = Math.fma(beta2, v[vOff + i], a2 * gi * gi);
            m[mOff + i] = mi;
            v[vOff + i] = vi;
            float update = (mi / bc1) / ((float) Math.sqrt(vi / bc2) + eps);
            p[pOff + i] = Math.fma(-lr, update, p[pOff + i] * decay);
        }
    }

    /**
     * One SGD step with momentum and (coupled) weight decay:
     * {@code d = g + wd·p}, {@code b = μ·b + d}, {@code p -= lr·(nesterov ? d + μ·b : b)}.
     *
     * @param p the parameters, updated in place
     * @param pOff the offset in {@code p}
     * @param g the gradients
     * @param gOff the offset in {@code g}
     * @param buf the momentum buffer {@code b}, updated in place (start at 0)
     * @param bOff the offset in {@code buf}
     * @param n the number of parameters
     * @param lr the learning rate
     * @param momentum the momentum {@code μ}; 0 gives plain SGD
     * @param weightDecay the coupled weight decay {@code wd}, added to the gradient
     * @param nesterov whether to use Nesterov momentum
     * @throws IllegalArgumentException if {@code n < 0} or an array is too short for its offset; nothing is written
     *         then
     */
    public static void sgd(float[] p, int pOff, float[] g, int gOff, float[] buf, int bOff, int n, float lr,
            float momentum, float weightDecay, boolean nesterov) {
        range(p, pOff, n, "p");
        range(g, gOff, n, "g");
        range(buf, bOff, n, "buf");
        for (int i = 0; i < n; i++) {
            float d = Math.fma(weightDecay, p[pOff + i], g[gOff + i]);
            float b = Math.fma(momentum, buf[bOff + i], d);
            buf[bOff + i] = b;
            float step = nesterov ? Math.fma(momentum, b, d) : b;
            p[pOff + i] = Math.fma(-lr, step, p[pOff + i]);
        }
    }

    /**
     * The factor that clips gradients to a maximum global norm: {@code min(1, maxNorm / norm)}, where
     * {@code norm} is the square root of the sum of every tensor's {@code Blasf.dot(g, g)} (sum them in a fixed
     * order for reproducibility). Multiply each gradient by it with {@code Blasf.scal}.
     *
     * @param norm the global gradient norm
     * @param maxNorm the largest norm allowed
     * @return {@code maxNorm / norm} if {@code norm > maxNorm}, otherwise 1 (also when {@code norm} is NaN)
     */
    public static float clipFactor(double norm, double maxNorm) {
        if (!(norm > maxNorm)) {
            return 1f;
        }
        return (float) (maxNorm / norm);
    }

    private static void range(float[] a, int off, int n, String name) {
        if (a == null || n < 0 || off < 0 || off > a.length - n) {
            throw new IllegalArgumentException(name + " must hold " + n + " values from offset " + off);
        }
    }
}
