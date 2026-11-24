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
import java.util.Set;

import org.dynamisengine.gpu.api.compute.Access;
import org.dynamisengine.gpu.api.compute.Dimensions;
import org.dynamisengine.gpu.api.compute.GpuBuffer;
import org.dynamisengine.gpu.api.compute.GpuCommandEncoder;
import org.dynamisengine.gpu.api.compute.GpuComputePipeline;
import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.gpu.api.compute.KernelBundle;

/**
 * The GPU twin of Vectrix {@code nn.Optim}'s parameter updates: AdamW and SGD with momentum, element-wise over
 * float buffers.
 * <p>
 * Each element is updated with the operations of the Vectrix loop body in the same order. The step's scalars are
 * computed on the host exactly as {@code Optim} computes them, including AdamW's bias corrections
 * {@code (float) (1 - β^t)}, which Vectrix evaluates in double with {@code ReproducibleMath.pow}; this class
 * carries a copy of that function, so it does not depend on Vectrix at run time. On an IEEE device the updated
 * parameters and moments are the bits {@code Optim.adamW} and {@code Optim.sgd} compute (see the package comment for
 * subnormals). {@code Optim.clipFactor} has no twin: it is a scalar function of a norm computed in double.
 * <p>
 * The buffers written (parameters and optimizer state) must all be different buffers, and different from the
 * gradient buffer. Two pipelines, compiled when the instance is created; each thread updates one element.
 *
 * @since 3.2.0
 */
public final class GpuOptim implements AutoCloseable {

    /** The parameter block of the AdamW kernel (checked against the compiled kernel). */
    static final String[] PARAMS_ADAMW = {"n", "pOff", "gOff", "mOff", "vOff", "negLr", "beta1", "beta2", "eps",
        "bc1", "bc2", "a1", "a2", "decay", "gridX"};

    /** The parameter block of the SGD kernel (checked against the compiled kernel). */
    static final String[] PARAMS_SGD = {"n", "pOff", "gOff", "bOff", "negLr", "momentum", "weightDecay", "nesterov",
        "gridX"};

    private static final int GROUP = 256;

    private final GpuDevice device;

    private final GpuComputePipeline adamW;

    private final GpuComputePipeline sgd;

    /**
     * Compiles the kernels on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipelines
     */
    public GpuOptim(GpuDevice device) {
        Dimensions local = new Dimensions(GROUP, 1, 1);
        adamW = Kernels.pipeline(device, "adamw", local, PARAMS_ADAMW.length, Set.of("FP32"), Access.READ_WRITE,
                Access.READ, Access.READ_WRITE, Access.READ_WRITE);
        GpuComputePipeline second;
        try {
            second = Kernels.pipeline(device, "sgd", local, PARAMS_SGD.length, Set.of("FP32"), Access.READ_WRITE,
                    Access.READ, Access.READ_WRITE);
        } catch (RuntimeException e) {
            adamW.close();
            throw e;
        }
        sgd = second;
        this.device = device;
    }

    /**
     * The compiled AdamW kernel, for applications that manage pipelines themselves. Bindings: slot 0 the
     * parameters, 1 the gradients, 2 the first moments, 3 the second moments; the parameter block holds the 32-bit
     * fields {@code n, pOff, gOff, mOff, vOff} and the floats {@code -lr, β1, β2, eps, bc1, bc2, 1 - β1, 1 - β2,
     * 1 - lr·wd}, then {@code gridX}.
     *
     * @return the kernel bundle
     */
    public static KernelBundle kernel() {
        return Kernels.bundle("adamw");
    }

    /**
     * Records one AdamW step, as {@code Optim.adamW}: {@code m = β1·m + (1 - β1)·g}, {@code v = β2·v + (1 - β2)·g²},
     * {@code p = p·(1 - lr·wd) - lr·(m/(1 - β1^t)) / (sqrt(v/(1 - β2^t)) + eps)}.
     *
     * @param encoder the encoder to record into
     * @param p the parameters, updated in place
     * @param pOff the element index in {@code p} of the first parameter
     * @param g the gradients
     * @param gOff the element index in {@code g} of the first gradient
     * @param m the first-moment estimates, updated in place (start at 0); a different buffer from {@code p}
     * @param mOff the element index in {@code m} of the first estimate
     * @param v the second-moment estimates, updated in place (start at 0); a different buffer from {@code p} and
     *        {@code m}
     * @param vOff the element index in {@code v} of the first estimate
     * @param n the number of parameters, {@code >= 0}; nothing is recorded for 0
     * @param lr the learning rate
     * @param beta1 the first-moment decay {@code β1}, typically 0.9
     * @param beta2 the second-moment decay {@code β2}, typically 0.999
     * @param eps the term added to the denominator, typically 1e-8
     * @param weightDecay the decoupled weight decay {@code wd}, applied as {@code p·(1 - lr·wd)}
     * @param step the 1-based step number {@code t}
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code step < 1}, {@code n} or an offset is negative, a buffer is too
     *         small, two of {@code p}, {@code m}, {@code v} and {@code g} are the same buffer, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder adamW(GpuCommandEncoder encoder, GpuBuffer p, long pOff, GpuBuffer g, long gOff,
            GpuBuffer m, long mOff, GpuBuffer v, long vOff, int n, float lr, float beta1, float beta2, float eps,
            float weightDecay, long step) {
        Kernels.notNull(encoder, "encoder");
        if (step < 1) {
            throw new IllegalArgumentException("step must be >= 1: " + step);
        }
        Kernels.range(p, pOff, n, "p");
        Kernels.range(g, gOff, n, "g");
        Kernels.range(m, mOff, n, "m");
        Kernels.range(v, vOff, n, "v");
        if (p == g || p == m || p == v || m == g || m == v || v == g) {
            throw new IllegalArgumentException("p, g, m and v must be different buffers");
        }
        if (n == 0) {
            return encoder;
        }
        // Optim.adamW's scalars, in the same arithmetic
        float bc1 = (float) (1.0 - HostMath.pow(beta1, step));
        float bc2 = (float) (1.0 - HostMath.pow(beta2, step));
        float a1 = 1f - beta1;
        float a2 = 1f - beta2;
        float decay = 1f - lr * weightDecay;
        Dimensions groups = groups(n);
        byte[] params = new Kernels.Params(PARAMS_ADAMW.length)
                .u32(n).u32(pOff).u32(gOff).u32(mOff).u32(vOff).f32(-lr).f32(beta1).f32(beta2).f32(eps).f32(bc1)
                .f32(bc2).f32(a1).f32(a2).f32(decay).u32(groups.x()).bytes();
        return encoder.dispatch(adamW, Map.of(0, p, 1, g, 2, m, 3, v), params, groups);
    }

    /**
     * Records one SGD step with momentum and coupled weight decay, as {@code Optim.sgd}: {@code d = g + wd·p},
     * {@code b = μ·b + d}, {@code p -= lr·(nesterov ? d + μ·b : b)}.
     *
     * @param encoder the encoder to record into
     * @param p the parameters, updated in place
     * @param pOff the element index in {@code p} of the first parameter
     * @param g the gradients
     * @param gOff the element index in {@code g} of the first gradient
     * @param buf the momentum buffer {@code b}, updated in place (start at 0); a different buffer from {@code p}
     * @param bOff the element index in {@code buf} of the first value
     * @param n the number of parameters, {@code >= 0}; nothing is recorded for 0
     * @param lr the learning rate
     * @param momentum the momentum {@code μ}; 0 gives plain SGD
     * @param weightDecay the coupled weight decay {@code wd}, added to the gradient
     * @param nesterov whether to use Nesterov momentum
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, two of
     *         {@code p}, {@code buf} and {@code g} are the same buffer, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder sgd(GpuCommandEncoder encoder, GpuBuffer p, long pOff, GpuBuffer g, long gOff,
            GpuBuffer buf, long bOff, int n, float lr, float momentum, float weightDecay, boolean nesterov) {
        Kernels.notNull(encoder, "encoder");
        Kernels.range(p, pOff, n, "p");
        Kernels.range(g, gOff, n, "g");
        Kernels.range(buf, bOff, n, "buf");
        if (p == g || p == buf || buf == g) {
            throw new IllegalArgumentException("p, g and buf must be different buffers");
        }
        if (n == 0) {
            return encoder;
        }
        Dimensions groups = groups(n);
        byte[] params = new Kernels.Params(PARAMS_SGD.length)
                .u32(n).u32(pOff).u32(gOff).u32(bOff).f32(-lr).f32(momentum).f32(weightDecay).u32(nesterov ? 1 : 0)
                .u32(groups.x()).bytes();
        return encoder.dispatch(sgd, Map.of(0, p, 1, g, 2, buf), params, groups);
    }

    private Dimensions groups(int n) {
        return Kernels.grid(device.capabilities(), (n + (long) GROUP - 1) / GROUP);
    }

    /** Releases the pipelines; work already recorded keeps them alive until it completes. Idempotent. */
    @Override
    public void close() {
        try {
            adamW.close();
        } finally {
            sgd.close();
        }
    }
}
