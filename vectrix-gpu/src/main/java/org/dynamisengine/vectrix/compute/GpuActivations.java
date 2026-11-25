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

import java.util.ArrayList;
import java.util.List;
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
 * The GPU twin of Vectrix {@code nn.Activations}: element-wise activation functions over float buffers, forward and
 * backward, and inverted dropout.
 * <p>
 * Every element is computed with the operations of the Vectrix loop body in the same order: Java's
 * {@code Math.max} and {@code Math.abs}, fused multiply-adds where Vectrix uses {@code Math.fma}, and ports of
 * {@code ReproducibleMath.exp}, {@code tanh}, {@code sigmoid} and {@code log1p}. On an IEEE device each output is the
 * bits Vectrix computes, except where a subnormal value is involved on a GPU that flushes subnormals to zero (see
 * the package comment): for example SiLU of an {@code x} between about -88.7 and -87.3, where the sigmoid is
 * subnormal, is 0 there. Dropout draws {@code Philox.uniform} on the GPU, so its mask is the CPU's on every device.
 * <p>
 * Forward methods write {@code y[i] = f(x[i])}; backward methods write {@code dx[i] = dy[i]·f'(x[i])} from the forward
 * input, except {@link #sigmoidBackward} and {@link #tanhBackward}, which take the forward output. As in Vectrix, an
 * output may be one of the inputs at the same offset (in place). An output in the same buffer as an input at a
 * different offset is rejected; inputs may share a buffer freely. The exact GELU ({@link #gelu},
 * {@link #geluBackward}, since 4.1) computes its normal distribution function and density in double, as Vectrix
 * does, with the binary64 arithmetic on 64-bit integers of {@code f64.slang} (GPUs such as Apple's have no FP64): it
 * matches Vectrix on every device with the {@code INT64} capability, and is much slower than the other activations.
 * <p>
 * Five pipelines, compiled when the instance is created: one for each aliasing pattern of the activations, and two
 * for dropout; and on a device with {@code INT64}, three more for the exact GELU. Each activation thread computes one element; each dropout thread one Philox block (four elements).
 *
 * @since 3.2.0
 */
public final class GpuActivations implements AutoCloseable {

    /** The parameter block of the kernel with the output in its own buffer (checked against the compiled kernel). */
    static final String[] PARAMS = {"op", "n", "aOff", "bOff", "destOff", "gridX"};

    /** The parameter block of the backward kernel whose output is one of its inputs. */
    static final String[] PARAMS_IN_PLACE = {"op", "n", "destOff", "otherOff", "destIsDy", "gridX"};

    /** The parameter block of the kernel whose inputs and output are all the same elements. */
    static final String[] PARAMS_SELF = {"op", "n", "off", "gridX"};

    /** The parameter block of the dropout kernel with the output in its own buffer. */
    static final String[] PARAMS_DROPOUT = {"seedLo", "seedHi", "startLo", "startHi", "prob", "scale", "xOff",
        "yOff", "n", "gridX"};

    /** The parameter block of the in-place dropout kernel. */
    static final String[] PARAMS_DROPOUT_IN_PLACE = {"seedLo", "seedHi", "startLo", "startHi", "prob", "scale",
        "xOff", "n", "gridX"};

    /** The operations of the activation kernels; the ordinal is the kernel's op code (activation_ops.slang). */
    enum Op {
        RELU, SIGMOID, SILU, TANH, GELU_TANH, SOFTPLUS,
        RELU_BACKWARD, SIGMOID_BACKWARD, SILU_BACKWARD, TANH_BACKWARD, GELU_TANH_BACKWARD, SOFTPLUS_BACKWARD;

        boolean backward() {
            return ordinal() >= RELU_BACKWARD.ordinal();
        }
    }

    private static final int GROUP = 256;

    private static final int DROPOUT_GROUP = 64;

    private final GpuDevice device;

    private final GpuComputePipeline twoBuffers;

    private final GpuComputePipeline inPlace;

    private final GpuComputePipeline self;

    private final GpuComputePipeline dropout;

    private final GpuComputePipeline dropoutInPlace;
    private final Pipelines activations;
    /** The exact GELU's pipelines; null on a device without INT64. */
    private final Pipelines gelu;

    /** The three aliasing patterns of one element-wise kernel family. */
    private record Pipelines(GpuComputePipeline twoBuffers, GpuComputePipeline inPlace, GpuComputePipeline self) {
    }

    /** The exact GELU kernels' op codes (gelu_ops.slang). */
    private static final int GELU = 0;
    private static final int GELU_BACKWARD = 1;

    /**
     * Compiles the kernels on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipelines
     */
    public GpuActivations(GpuDevice device) {
        Set<String> fp32 = Set.of("FP32");
        Set<String> intFp32 = Set.of("INT32", "FP32");
        List<GpuComputePipeline> made = new ArrayList<>();
        try {
            made.add(Kernels.pipeline(device, "activation", new Dimensions(GROUP, 1, 1), PARAMS, fp32,
                    Access.READ, Access.READ, Access.WRITE));
            made.add(Kernels.pipeline(device, "activation_inplace", new Dimensions(GROUP, 1, 1),
                    PARAMS_IN_PLACE, fp32, Access.READ_WRITE, Access.READ));
            made.add(Kernels.pipeline(device, "activation_self", new Dimensions(GROUP, 1, 1), PARAMS_SELF,
                    fp32, Access.READ_WRITE));
            made.add(Kernels.pipeline(device, "dropout", new Dimensions(DROPOUT_GROUP, 1, 1), PARAMS_DROPOUT,
                    intFp32, Access.READ, Access.WRITE));
            made.add(Kernels.pipeline(device, "dropout_inplace", new Dimensions(DROPOUT_GROUP, 1, 1),
                    PARAMS_DROPOUT_IN_PLACE, intFp32, Access.READ_WRITE));
            if (device.capabilities().dataTypes().contains("INT64")) {
                Set<String> int64 = Set.of("FP32", "INT64");
                made.add(Kernels.pipeline(device, "gelu", new Dimensions(GROUP, 1, 1), PARAMS, int64,
                        Access.READ, Access.READ, Access.WRITE));
                made.add(Kernels.pipeline(device, "gelu_inplace", new Dimensions(GROUP, 1, 1), PARAMS_IN_PLACE,
                        int64, Access.READ_WRITE, Access.READ));
                made.add(Kernels.pipeline(device, "gelu_self", new Dimensions(GROUP, 1, 1), PARAMS_SELF, int64,
                        Access.READ_WRITE));
            }
        } catch (RuntimeException e) {
            for (GpuComputePipeline p : made) {
                p.close();
            }
            throw e;
        }
        twoBuffers = made.get(0);
        inPlace = made.get(1);
        self = made.get(2);
        dropout = made.get(3);
        dropoutInPlace = made.get(4);
        activations = new Pipelines(twoBuffers, inPlace, self);
        gelu = made.size() > 5 ? new Pipelines(made.get(5), made.get(6), made.get(7)) : null;
        this.device = device;
    }

    /**
     * The compiled activation kernel with the output in its own buffer, for applications that manage pipelines
     * themselves. Bindings: slot 0 {@code a} (the forward input, or output for the sigmoid and tanh gradients),
     * 1 {@code b} (the upstream gradient; bind {@code a} again for a forward op), 2 {@code dest}; the parameter
     * block holds the 32-bit fields {@code op, n, aOff, bOff, destOff, gridX}, and {@code op} numbers the
     * operations in the order relu, sigmoid, silu, tanh, geluTanh, softplus, then their backward functions in the
     * same order.
     *
     * @return the kernel bundle
     */
    public static KernelBundle kernel() {
        return Kernels.bundle("activation");
    }

    /**
     * Records ReLU, {@code y[i] = max(x[i], 0)} with Java's {@code Math.max} (NaN stays NaN, {@code -0} gives
     * {@code +0}), as {@code Activations.relu}.
     *
     * @param encoder the encoder to record into
     * @param x the input
     * @param xOff the element index in {@code x} of the first input
     * @param y receives the output; may be {@code x} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code y} is
     *         {@code x} at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder relu(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff, int n) {
        return forward(Op.RELU, encoder, x, xOff, y, yOff, n);
    }

    /**
     * Records the logistic function {@code y[i] = 1 / (1 + e^-x[i])}, as {@code Activations.sigmoid}.
     *
     * @param encoder the encoder to record into
     * @param x the input
     * @param xOff the element index in {@code x} of the first input
     * @param y receives the output; may be {@code x} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code y} is
     *         {@code x} at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder sigmoid(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff,
            int n) {
        return forward(Op.SIGMOID, encoder, x, xOff, y, yOff, n);
    }

    /**
     * Records SiLU (swish), {@code y[i] = x[i]·sigmoid(x[i])}, as {@code Activations.silu}.
     *
     * @param encoder the encoder to record into
     * @param x the input
     * @param xOff the element index in {@code x} of the first input
     * @param y receives the output; may be {@code x} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code y} is
     *         {@code x} at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder silu(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff, int n) {
        return forward(Op.SILU, encoder, x, xOff, y, yOff, n);
    }

    /**
     * Records the hyperbolic tangent {@code y[i] = tanh x[i]}, as {@code Activations.tanh}.
     *
     * @param encoder the encoder to record into
     * @param x the input
     * @param xOff the element index in {@code x} of the first input
     * @param y receives the output; may be {@code x} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code y} is
     *         {@code x} at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder tanh(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff, int n) {
        return forward(Op.TANH, encoder, x, xOff, y, yOff, n);
    }

    /**
     * Records the tanh-approximated GELU, {@code y = x·(1 + tanh(√(2/π)·(x + 0.044715·x³)))/2}, as
     * {@code Activations.geluTanh}.
     *
     * @param encoder the encoder to record into
     * @param x the input
     * @param xOff the element index in {@code x} of the first input
     * @param y receives the output; may be {@code x} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code y} is
     *         {@code x} at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder geluTanh(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff,
            int n) {
        return forward(Op.GELU_TANH, encoder, x, xOff, y, yOff, n);
    }

    /**
     * Records softplus, {@code y = max(x, 0) + log1p(e^-|x|)}, as {@code Activations.softplus}.
     *
     * @param encoder the encoder to record into
     * @param x the input
     * @param xOff the element index in {@code x} of the first input
     * @param y receives the output; may be {@code x} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code y} is
     *         {@code x} at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder softplus(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff,
            int n) {
        return forward(Op.SOFTPLUS, encoder, x, xOff, y, yOff, n);
    }

    /**
     * Records the ReLU gradient, {@code dx[i] = x[i] > 0 ? dy[i] : 0}, as {@code Activations.reluBackward}.
     *
     * @param encoder the encoder to record into
     * @param x the forward input
     * @param xOff the element index in {@code x} of the first input
     * @param dy the gradient of the output
     * @param dyOff the element index in {@code dy} of the first gradient
     * @param dx receives the gradient of the input; may be {@code x} or {@code dy} at the same offset
     * @param dxOff the element index in {@code dx} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code dx} is
     *         an input at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder reluBackward(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer dy, long dyOff,
            GpuBuffer dx, long dxOff, int n) {
        return backward(Op.RELU_BACKWARD, encoder, x, xOff, "x", dy, dyOff, dx, dxOff, n);
    }

    /**
     * Records the sigmoid gradient from the forward <i>output</i> {@code y}, {@code dx = dy·y·(1 - y)}, as
     * {@code Activations.sigmoidBackward}.
     *
     * @param encoder the encoder to record into
     * @param y the forward output
     * @param yOff the element index in {@code y} of the first value
     * @param dy the gradient of the output
     * @param dyOff the element index in {@code dy} of the first gradient
     * @param dx receives the gradient of the input; may be {@code y} or {@code dy} at the same offset
     * @param dxOff the element index in {@code dx} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code dx} is
     *         an input at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder sigmoidBackward(GpuCommandEncoder encoder, GpuBuffer y, long yOff, GpuBuffer dy,
            long dyOff, GpuBuffer dx, long dxOff, int n) {
        return backward(Op.SIGMOID_BACKWARD, encoder, y, yOff, "y", dy, dyOff, dx, dxOff, n);
    }

    /**
     * Records the SiLU gradient from its input, {@code dx = dy·(s + x·s·(1 - s))} with {@code s = sigmoid(x)}, as
     * {@code Activations.siluBackward}.
     *
     * @param encoder the encoder to record into
     * @param x the forward input
     * @param xOff the element index in {@code x} of the first input
     * @param dy the gradient of the output
     * @param dyOff the element index in {@code dy} of the first gradient
     * @param dx receives the gradient of the input; may be {@code x} or {@code dy} at the same offset
     * @param dxOff the element index in {@code dx} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code dx} is
     *         an input at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder siluBackward(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer dy, long dyOff,
            GpuBuffer dx, long dxOff, int n) {
        return backward(Op.SILU_BACKWARD, encoder, x, xOff, "x", dy, dyOff, dx, dxOff, n);
    }

    /**
     * Records the tanh gradient from the forward <i>output</i> {@code y}, {@code dx = dy·(1 - y)(1 + y)}, as
     * {@code Activations.tanhBackward}.
     *
     * @param encoder the encoder to record into
     * @param y the forward output
     * @param yOff the element index in {@code y} of the first value
     * @param dy the gradient of the output
     * @param dyOff the element index in {@code dy} of the first gradient
     * @param dx receives the gradient of the input; may be {@code y} or {@code dy} at the same offset
     * @param dxOff the element index in {@code dx} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code dx} is
     *         an input at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder tanhBackward(GpuCommandEncoder encoder, GpuBuffer y, long yOff, GpuBuffer dy, long dyOff,
            GpuBuffer dx, long dxOff, int n) {
        return backward(Op.TANH_BACKWARD, encoder, y, yOff, "y", dy, dyOff, dx, dxOff, n);
    }

    /**
     * Records the gradient of the tanh-approximated GELU from its input, as {@code Activations.geluTanhBackward}.
     *
     * @param encoder the encoder to record into
     * @param x the forward input
     * @param xOff the element index in {@code x} of the first input
     * @param dy the gradient of the output
     * @param dyOff the element index in {@code dy} of the first gradient
     * @param dx receives the gradient of the input; may be {@code x} or {@code dy} at the same offset
     * @param dxOff the element index in {@code dx} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code dx} is
     *         an input at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder geluTanhBackward(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer dy,
            long dyOff, GpuBuffer dx, long dxOff, int n) {
        return backward(Op.GELU_TANH_BACKWARD, encoder, x, xOff, "x", dy, dyOff, dx, dxOff, n);
    }

    /**
     * Records the exact GELU, {@code y[i] = x[i]·Φ(x[i])} with {@code Φ} the standard normal distribution function,
     * as {@code Activations.gelu}. {@code Φ} is computed as {@code ReproducibleMath.normalCdf} does, partly in
     * double, with the binary64 arithmetic of {@code f64.slang}; the result is the bits Vectrix computes on every
     * device with {@code INT64}, subnormal results aside on GPUs that flush them. Much slower than {@link #geluTanh}.
     *
     * @param encoder the encoder to record into
     * @param x the input
     * @param xOff the element index in {@code x} of the first input
     * @param y receives the output; may be {@code x} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code y} is
     *         {@code x} at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device has no {@code INT64}
     *         ({@code UNSUPPORTED}) or the dispatch exceeds the device's limits
     * @since 4.1.0
     */
    public GpuCommandEncoder gelu(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff, int n) {
        return forward(exactGelu(), GELU, encoder, x, xOff, y, yOff, n);
    }

    /**
     * Records the exact GELU's gradient from its input, {@code dx = dy·(Φ(x) + x·φ(x))} with {@code φ} the standard
     * normal density, as {@code Activations.geluBackward}; see {@link #gelu}.
     *
     * @param encoder the encoder to record into
     * @param x the forward input
     * @param xOff the element index in {@code x} of the first input
     * @param dy the gradient of the output
     * @param dyOff the element index in {@code dy} of the first gradient
     * @param dx receives the gradient of the input; may be {@code x} or {@code dy} at the same offset
     * @param dxOff the element index in {@code dx} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code dx} is
     *         an input at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device has no {@code INT64}
     *         ({@code UNSUPPORTED}) or the dispatch exceeds the device's limits
     * @since 4.1.0
     */
    public GpuCommandEncoder geluBackward(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer dy,
            long dyOff, GpuBuffer dx, long dxOff, int n) {
        return backward(exactGelu(), GELU_BACKWARD, encoder, x, xOff, "x", dy, dyOff, dx, dxOff, n);
    }

    /**
     * The compiled exact GELU kernel with the output in its own buffer, for applications that manage pipelines
     * themselves. The same bindings and parameter block as {@link #kernel()}; {@code op} is 0 for GELU and 1 for its
     * gradient. Needs {@code INT64}.
     *
     * @return the kernel bundle
     * @since 4.1.0
     */
    public static KernelBundle geluKernel() {
        return Kernels.bundle("gelu");
    }

    private Pipelines exactGelu() {
        if (gelu == null) {
            throw new org.dynamisengine.gpu.api.compute.ComputeException(
                    org.dynamisengine.gpu.api.compute.ComputeException.Code.UNSUPPORTED,
                    "the exact GELU needs the device's INT64 capability");
        }
        return gelu;
    }

    /**
     * Records the softplus gradient from its input, {@code dx = dy·sigmoid(x)}, as
     * {@code Activations.softplusBackward}.
     *
     * @param encoder the encoder to record into
     * @param x the forward input
     * @param xOff the element index in {@code x} of the first input
     * @param dy the gradient of the output
     * @param dyOff the element index in {@code dy} of the first gradient
     * @param dx receives the gradient of the input; may be {@code x} or {@code dy} at the same offset
     * @param dxOff the element index in {@code dx} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code dx} is
     *         an input at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder softplusBackward(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer dy,
            long dyOff, GpuBuffer dx, long dxOff, int n) {
        return backward(Op.SOFTPLUS_BACKWARD, encoder, x, xOff, "x", dy, dyOff, dx, dxOff, n);
    }

    /**
     * Records inverted dropout, as {@code Activations.dropout}: element {@code i} is kept when
     * {@code Philox.uniform(seed, start + i) >= p} and scaled by {@code 1/(1 - p)}, otherwise it becomes 0. The same
     * call on the upstream gradient is the backward pass. The mask is the one Vectrix draws, on every device.
     *
     * @param encoder the encoder to record into
     * @param seed the random stream
     * @param start the stream position of element 0, read as unsigned; use disjoint ranges for different tensors
     * @param p the drop probability, in [0, 1)
     * @param x the input
     * @param xOff the element index in {@code x} of the first input
     * @param y receives the output; may be {@code x} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code p} is outside [0, 1) or NaN, {@code n} or an offset is negative, a
     *         buffer is too small, {@code y} is {@code x} at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder dropout(GpuCommandEncoder encoder, long seed, long start, float p, GpuBuffer x,
            long xOff, GpuBuffer y, long yOff, int n) {
        Kernels.notNull(encoder, "encoder");
        if (!(p >= 0f && p < 1f)) {
            throw new IllegalArgumentException("p must be in [0, 1): " + p);
        }
        Kernels.range(x, xOff, n, "x");
        Kernels.range(y, yOff, n, "y");
        if (x == y && xOff != yOff) {
            throw new IllegalArgumentException("y may be x only at the same offset");
        }
        if (n == 0) {
            return encoder;
        }
        float scale = 1f / (1f - p);
        long counters = ((start & 3) + n + 3) / 4;
        Dimensions groups = Kernels.grid(device.capabilities(), (counters + DROPOUT_GROUP - 1) / DROPOUT_GROUP);
        if (x == y) {
            byte[] params = new Kernels.Params(PARAMS_DROPOUT_IN_PLACE)
                    .u32(seed).u32(seed >>> 32).u32(start).u32(start >>> 32).f32(p).f32(scale).u32(xOff).u32(n)
                    .u32(groups.x()).bytes();
            return Kernels.dispatch(encoder, dropoutInPlace, Map.of(0, x), params, groups);
        }
        byte[] params = new Kernels.Params(PARAMS_DROPOUT)
                .u32(seed).u32(seed >>> 32).u32(start).u32(start >>> 32).f32(p).f32(scale).u32(xOff).u32(yOff).u32(n)
                .u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, dropout, Map.of(0, x, 1, y), params, groups);
    }

    private GpuCommandEncoder forward(Op op, GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y,
            long yOff, int n) {
        return forward(activations, op.ordinal(), encoder, x, xOff, y, yOff, n);
    }

    private GpuCommandEncoder forward(Pipelines k, int code, GpuCommandEncoder encoder, GpuBuffer x, long xOff,
            GpuBuffer y, long yOff, int n) {
        Kernels.notNull(encoder, "encoder");
        Kernels.range(x, xOff, n, "x");
        Kernels.range(y, yOff, n, "y");
        if (x == y && xOff != yOff) {
            throw new IllegalArgumentException("y may be x only at the same offset");
        }
        if (n == 0) {
            return encoder;
        }
        Dimensions groups = groups(n);
        if (x == y) {
            return dispatchSelf(k, code, encoder, x, xOff, n, groups);
        }
        // a forward op reads only slot 0; the input is bound at slot 1 too, read-only
        byte[] params = new Kernels.Params(PARAMS)
                .u32(code).u32(n).u32(xOff).u32(xOff).u32(yOff).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, k.twoBuffers(), Map.of(0, x, 1, x, 2, y), params, groups);
    }

    private GpuCommandEncoder backward(Op op, GpuCommandEncoder encoder, GpuBuffer a, long aOff, String aName,
            GpuBuffer dy, long dyOff, GpuBuffer dx, long dxOff, int n) {
        return backward(activations, op.ordinal(), encoder, a, aOff, aName, dy, dyOff, dx, dxOff, n);
    }

    private GpuCommandEncoder backward(Pipelines k, int code, GpuCommandEncoder encoder, GpuBuffer a, long aOff,
            String aName, GpuBuffer dy, long dyOff, GpuBuffer dx, long dxOff, int n) {
        Kernels.notNull(encoder, "encoder");
        Kernels.range(a, aOff, n, aName);
        Kernels.range(dy, dyOff, n, "dy");
        Kernels.range(dx, dxOff, n, "dx");
        boolean onA = dx == a;
        boolean onDy = dx == dy;
        if (onA && dxOff != aOff) {
            throw new IllegalArgumentException("dx may be " + aName + " only at the same offset");
        }
        if (onDy && dxOff != dyOff) {
            throw new IllegalArgumentException("dx may be dy only at the same offset");
        }
        if (n == 0) {
            return encoder;
        }
        Dimensions groups = groups(n);
        if (onA && onDy) {
            return dispatchSelf(k, code, encoder, dx, dxOff, n, groups);
        }
        if (onA || onDy) {
            GpuBuffer other = onA ? dy : a;
            long otherOff = onA ? dyOff : aOff;
            byte[] params = new Kernels.Params(PARAMS_IN_PLACE)
                    .u32(code).u32(n).u32(dxOff).u32(otherOff).u32(onDy ? 1 : 0).u32(groups.x()).bytes();
            return Kernels.dispatch(encoder, k.inPlace(), Map.of(0, dx, 1, other), params, groups);
        }
        byte[] params = new Kernels.Params(PARAMS)
                .u32(code).u32(n).u32(aOff).u32(dyOff).u32(dxOff).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, k.twoBuffers(), Map.of(0, a, 1, dy, 2, dx), params, groups);
    }

    private GpuCommandEncoder dispatchSelf(Pipelines k, int code, GpuCommandEncoder encoder, GpuBuffer x, long off,
            int n, Dimensions groups) {
        byte[] params = new Kernels.Params(PARAMS_SELF)
                .u32(code).u32(n).u32(off).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, k.self(), Map.of(0, x), params, groups);
    }

    private Dimensions groups(int n) {
        return Kernels.grid(device.capabilities(), (n + (long) GROUP - 1) / GROUP);
    }

    /** Releases the pipelines; work already recorded keeps them alive until it completes. Idempotent. */
    @Override
    public void close() {
        try {
            twoBuffers.close();
        } finally {
            try {
                inPlace.close();
            } finally {
                try {
                    self.close();
                } finally {
                    try {
                        dropout.close();
                    } finally {
                        try {
                            dropoutInPlace.close();
                        } finally {
                            if (gelu != null) {
                                try {
                                    gelu.twoBuffers().close();
                                } finally {
                                    try {
                                        gelu.inPlace().close();
                                    } finally {
                                        gelu.self().close();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
