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
package org.dynamisengine.vectrix.compute;

import java.util.Map;

import org.dynamisengine.vectrix.compute.GpuActivations.Op;
import org.dynamisengine.vectrix.nn.Activations;
import org.dynamisengine.vectrix.nn.Optim;

/**
 * Reference models of the element-wise kernels ({@link GpuActivations}, {@link GpuOptim}): each runs the Vectrix
 * method on the dispatch's buffers, aliased as the kernel's bindings are.
 */
final class ElementwiseModels {

    private ElementwiseModels() {
    }

    /** A forward activation, {@code y = f(x)}. */
    interface Forward {
        void apply(float[] x, int xOff, float[] y, int yOff, int n);
    }

    /** A backward activation, {@code dx = dy·f'(a)}. */
    interface Backward {
        void apply(float[] a, int aOff, float[] dy, int dyOff, float[] dx, int dxOff, int n);
    }

    /** The Vectrix method of a forward op. */
    static Forward forward(Op op) {
        return switch (op) {
            case RELU -> Activations::relu;
            case SIGMOID -> Activations::sigmoid;
            case SILU -> Activations::silu;
            case TANH -> Activations::tanh;
            case GELU_TANH -> Activations::geluTanh;
            case SOFTPLUS -> Activations::softplus;
            default -> throw new IllegalArgumentException(op + " is a backward op");
        };
    }

    /** The Vectrix method of a backward op. */
    static Backward backward(Op op) {
        return switch (op) {
            case RELU_BACKWARD -> Activations::reluBackward;
            case SIGMOID_BACKWARD -> Activations::sigmoidBackward;
            case SILU_BACKWARD -> Activations::siluBackward;
            case TANH_BACKWARD -> Activations::tanhBackward;
            case GELU_TANH_BACKWARD -> Activations::geluTanhBackward;
            case SOFTPLUS_BACKWARD -> Activations::softplusBackward;
            default -> throw new IllegalArgumentException(op + " is a forward op");
        };
    }

    static void register(Map<String, ReferenceModels.ReferenceModel> m) {
        m.put("activation", ElementwiseModels::activation);
        m.put("activation_inplace", ElementwiseModels::activationInPlace);
        m.put("activation_self", ElementwiseModels::activationSelf);
        m.put("dropout", c -> dropout(c, false));
        m.put("dropout_inplace", c -> dropout(c, true));
        m.put("adamw", ElementwiseModels::adamW);
        m.put("sgd", ElementwiseModels::sgd);
    }

    private static Op op(ReferenceModels.Call c) {
        return Op.values()[c.i("op")];
    }

    private static void requireElements(ReferenceModels.Call c, int n) {
        c.requireLinearGrid((n + 255L) / 256);
    }

    private static void activation(ReferenceModels.Call c) {
        Op op = op(c);
        int n = c.i("n");
        requireElements(c, n);
        float[] a = c.floats(0);
        float[] b = c.floats(1);
        float[] dest = c.floats(2);
        if (op.backward()) {
            backward(op).apply(a, c.i("aOff"), b, c.i("bOff"), dest, c.i("destOff"), n);
        } else {
            forward(op).apply(a, c.i("aOff"), dest, c.i("destOff"), n);
        }
        c.put(2, dest);
    }

    private static void activationInPlace(ReferenceModels.Call c) {
        Op op = op(c);
        int n = c.i("n");
        requireElements(c, n);
        c.require(op.backward(), "the in-place activation kernel runs backward ops only");
        float[] dest = c.floats(0);
        float[] other = c.floats(1);
        int destOff = c.i("destOff");
        int otherOff = c.i("otherOff");
        if (c.u("destIsDy") != 0) {
            backward(op).apply(other, otherOff, dest, destOff, dest, destOff, n);
        } else {
            backward(op).apply(dest, destOff, other, otherOff, dest, destOff, n);
        }
        c.put(0, dest);
    }

    private static void activationSelf(ReferenceModels.Call c) {
        Op op = op(c);
        int n = c.i("n");
        requireElements(c, n);
        float[] x = c.floats(0);
        int off = c.i("off");
        if (op.backward()) {
            backward(op).apply(x, off, x, off, x, off, n);
        } else {
            forward(op).apply(x, off, x, off, n);
        }
        c.put(0, x);
    }

    private static void dropout(ReferenceModels.Call c, boolean inPlace) {
        long seed = c.u("seedLo") | c.u("seedHi") << 32;
        long start = c.u("startLo") | c.u("startHi") << 32;
        int n = c.i("n");
        float p = c.f("prob");
        c.require(Float.floatToRawIntBits(c.f("scale")) == Float.floatToRawIntBits(1f / (1f - p)),
                "dropout scale is not 1 / (1 - p)");
        c.requireLinearGrid((((start & 3) + n + 3) / 4 + 63) / 64);
        float[] x = c.floats(0);
        if (inPlace) {
            Activations.dropout(seed, start, p, x, c.i("xOff"), x, c.i("xOff"), n);
            c.put(0, x);
        } else {
            float[] y = c.floats(1);
            Activations.dropout(seed, start, p, x, c.i("xOff"), y, c.i("yOff"), n);
            c.put(1, y);
        }
    }

    /**
     * The loop of {@code Optim.adamW}, given the scalars the wrapper computed (the kernel does not receive the step
     * number or the weight decay). The parity tests compare the result with {@code Optim.adamW} itself, so they also
     * check the wrapper's scalars.
     */
    private static void adamW(ReferenceModels.Call c) {
        int n = c.i("n");
        requireElements(c, n);
        float[] p = c.floats(0);
        float[] g = c.floats(1);
        float[] m = c.floats(2);
        float[] v = c.floats(3);
        int pOff = c.i("pOff");
        int gOff = c.i("gOff");
        int mOff = c.i("mOff");
        int vOff = c.i("vOff");
        float negLr = c.f("negLr");
        float beta1 = c.f("beta1");
        float beta2 = c.f("beta2");
        float eps = c.f("eps");
        float bc1 = c.f("bc1");
        float bc2 = c.f("bc2");
        float a1 = c.f("a1");
        float a2 = c.f("a2");
        float decay = c.f("decay");
        for (int i = 0; i < n; i++) {
            float gi = g[gOff + i];
            float mi = Math.fma(beta1, m[mOff + i], a1 * gi);
            float vi = Math.fma(beta2, v[vOff + i], a2 * gi * gi);
            m[mOff + i] = mi;
            v[vOff + i] = vi;
            float update = (mi / bc1) / ((float) Math.sqrt(vi / bc2) + eps);
            p[pOff + i] = Math.fma(negLr, update, p[pOff + i] * decay);
        }
        c.put(0, p);
        c.put(2, m);
        c.put(3, v);
    }

    private static void sgd(ReferenceModels.Call c) {
        int n = c.i("n");
        requireElements(c, n);
        float[] p = c.floats(0);
        float[] g = c.floats(1);
        float[] buf = c.floats(2);
        Optim.sgd(p, c.i("pOff"), g, c.i("gOff"), buf, c.i("bOff"), n, -c.f("negLr"), c.f("momentum"),
                c.f("weightDecay"), c.u("nesterov") != 0);
        c.put(0, p);
        c.put(2, buf);
    }
}
