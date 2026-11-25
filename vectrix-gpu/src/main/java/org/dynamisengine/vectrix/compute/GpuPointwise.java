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
 * The GPU twin of Vectrix {@code nn.Pointwise}: element-wise arithmetic over float buffers.
 * <p>
 * Each element is the one Java operation of the Vectrix method: {@code +}, {@code -}, {@code *}, {@code /} and
 * {@code sqrt} as the device computes them (correctly rounded on IEEE devices), Java's {@code Math.max}/{@code min},
 * and ports of {@code ReproducibleMath.exp} and {@code log} (which use fused multiply-adds). On an IEEE device that
 * fuses {@code fma} each output is the bits Vectrix computes, subnormals aside on GPUs that flush them. An output
 * may be an input at the same offset (in place); an output in an input's buffer at a different offset is rejected.
 * Three pipelines, one per aliasing pattern, compiled when the instance is created; one thread per element.
 *
 * @since 4.1.0
 */
public final class GpuPointwise implements AutoCloseable {
    /** The parameter block of the kernel with the output in its own buffer (checked against the compiled kernel). */
    static final String[] PARAMS = {"op", "n", "aOff", "bOff", "destOff", "gridX"};
    /** The parameter block of the binary kernel whose output is one of its inputs. */
    static final String[] PARAMS_IN_PLACE = {"op", "n", "destOff", "otherOff", "destIsDy", "gridX"};
    /** The parameter block of the kernel whose inputs and output are all the same elements. */
    static final String[] PARAMS_SELF = {"op", "n", "off", "gridX"};

    /** The operations; the ordinal is the kernel's op code (pointwise_ops.slang). */
    enum Op {
        EXP, LOG, SQRT, NEG, SQUARE, ADD, SUB, MUL, DIV, MAX, MIN;

        boolean binary() {
            return ordinal() >= ADD.ordinal();
        }
    }

    private static final int GROUP = 256;
    private final GpuDevice device;
    private final GpuComputePipeline twoBuffers;
    private final GpuComputePipeline inPlace;
    private final GpuComputePipeline self;

    /**
     * Compiles the kernels on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipelines
     */
    public GpuPointwise(GpuDevice device) {
        Set<String> fp32 = Set.of("FP32");
        List<GpuComputePipeline> made = new ArrayList<>();
        try {
            made.add(Kernels.pipeline(device, "pointwise", new Dimensions(GROUP, 1, 1), PARAMS, fp32,
                    Access.READ, Access.READ, Access.WRITE));
            made.add(Kernels.pipeline(device, "pointwise_inplace", new Dimensions(GROUP, 1, 1), PARAMS_IN_PLACE,
                    fp32, Access.READ_WRITE, Access.READ));
            made.add(Kernels.pipeline(device, "pointwise_self", new Dimensions(GROUP, 1, 1), PARAMS_SELF, fp32,
                    Access.READ_WRITE));
        } catch (RuntimeException e) {
            for (GpuComputePipeline p : made) {
                p.close();
            }
            throw e;
        }
        twoBuffers = made.get(0);
        inPlace = made.get(1);
        self = made.get(2);
        this.device = device;
    }

    /**
     * The compiled kernel with the output in its own buffer, for applications that manage pipelines themselves.
     * Bindings: slot 0 {@code a}, 1 {@code b} (bind {@code a} again for a unary op), 2 {@code dest}; the parameter
     * block holds the 32-bit fields {@code op, n, aOff, bOff, destOff, gridX}, with {@code op} numbering exp, log,
     * sqrt, neg, square, add, sub, mul, div, max, min.
     *
     * @return the kernel bundle
     */
    public static KernelBundle kernel() {
        return Kernels.bundle("pointwise");
    }

    /**
     * Records {@code y[i] = e^x[i]}, as {@code Pointwise.exp} ({@code ReproducibleMath.exp}).
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
    public GpuCommandEncoder exp(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff, int n) {
        return unary(Op.EXP, encoder, x, xOff, y, yOff, n);
    }

    /**
     * Records {@code y[i] = ln x[i]}, as {@code Pointwise.log} ({@code ReproducibleMath.log}).
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
    public GpuCommandEncoder log(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff, int n) {
        return unary(Op.LOG, encoder, x, xOff, y, yOff, n);
    }

    /**
     * Records {@code y[i] = √x[i]}, correctly rounded on IEEE devices, as {@code Pointwise.sqrt}.
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
    public GpuCommandEncoder sqrt(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff, int n) {
        return unary(Op.SQRT, encoder, x, xOff, y, yOff, n);
    }

    /**
     * Records {@code y[i] = -x[i]} (the sign bit flipped), as {@code Pointwise.neg}.
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
    public GpuCommandEncoder neg(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff, int n) {
        return unary(Op.NEG, encoder, x, xOff, y, yOff, n);
    }

    /**
     * Records {@code y[i] = x[i]·x[i]}, as {@code Pointwise.square}.
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
    public GpuCommandEncoder square(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff, int n) {
        return unary(Op.SQUARE, encoder, x, xOff, y, yOff, n);
    }

    /**
     * Records {@code y[i] = a[i] + b[i]}, as {@code Pointwise.add}.
     *
     * @param encoder the encoder to record into
     * @param a the first input
     * @param aOff the element index in {@code a} of the first element
     * @param b the second input; may share a buffer with {@code a}
     * @param bOff the element index in {@code b} of the first element
     * @param y receives the output; may be {@code a} or {@code b} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code y} is
     *         in an input's buffer at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder add(GpuCommandEncoder encoder, GpuBuffer a, long aOff, GpuBuffer b, long bOff, GpuBuffer y,
            long yOff, int n) {
        return binary(Op.ADD, encoder, a, aOff, b, bOff, y, yOff, n);
    }

    /**
     * Records {@code y[i] = a[i] - b[i]}, as {@code Pointwise.sub}.
     *
     * @param encoder the encoder to record into
     * @param a the first input
     * @param aOff the element index in {@code a} of the first element
     * @param b the second input; may share a buffer with {@code a}
     * @param bOff the element index in {@code b} of the first element
     * @param y receives the output; may be {@code a} or {@code b} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code y} is
     *         in an input's buffer at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder sub(GpuCommandEncoder encoder, GpuBuffer a, long aOff, GpuBuffer b, long bOff, GpuBuffer y,
            long yOff, int n) {
        return binary(Op.SUB, encoder, a, aOff, b, bOff, y, yOff, n);
    }

    /**
     * Records {@code y[i] = a[i]·b[i]}, as {@code Pointwise.mul}.
     *
     * @param encoder the encoder to record into
     * @param a the first input
     * @param aOff the element index in {@code a} of the first element
     * @param b the second input; may share a buffer with {@code a}
     * @param bOff the element index in {@code b} of the first element
     * @param y receives the output; may be {@code a} or {@code b} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code y} is
     *         in an input's buffer at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder mul(GpuCommandEncoder encoder, GpuBuffer a, long aOff, GpuBuffer b, long bOff, GpuBuffer y,
            long yOff, int n) {
        return binary(Op.MUL, encoder, a, aOff, b, bOff, y, yOff, n);
    }

    /**
     * Records {@code y[i] = a[i] / b[i]}, as {@code Pointwise.div}.
     *
     * @param encoder the encoder to record into
     * @param a the first input
     * @param aOff the element index in {@code a} of the first element
     * @param b the second input; may share a buffer with {@code a}
     * @param bOff the element index in {@code b} of the first element
     * @param y receives the output; may be {@code a} or {@code b} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code y} is
     *         in an input's buffer at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder div(GpuCommandEncoder encoder, GpuBuffer a, long aOff, GpuBuffer b, long bOff, GpuBuffer y,
            long yOff, int n) {
        return binary(Op.DIV, encoder, a, aOff, b, bOff, y, yOff, n);
    }

    /**
     * Records {@code y[i] = Math.max(a[i], b[i])} (NaN if either is NaN, {@code +0} above {@code -0}), as {@code Pointwise.max}.
     *
     * @param encoder the encoder to record into
     * @param a the first input
     * @param aOff the element index in {@code a} of the first element
     * @param b the second input; may share a buffer with {@code a}
     * @param bOff the element index in {@code b} of the first element
     * @param y receives the output; may be {@code a} or {@code b} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code y} is
     *         in an input's buffer at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder max(GpuCommandEncoder encoder, GpuBuffer a, long aOff, GpuBuffer b, long bOff, GpuBuffer y,
            long yOff, int n) {
        return binary(Op.MAX, encoder, a, aOff, b, bOff, y, yOff, n);
    }

    /**
     * Records {@code y[i] = Math.min(a[i], b[i])} (NaN if either is NaN, {@code -0} below {@code +0}), as {@code Pointwise.min}.
     *
     * @param encoder the encoder to record into
     * @param a the first input
     * @param aOff the element index in {@code a} of the first element
     * @param b the second input; may share a buffer with {@code a}
     * @param bOff the element index in {@code b} of the first element
     * @param y receives the output; may be {@code a} or {@code b} at the same offset
     * @param yOff the element index in {@code y} of the first output
     * @param n the number of elements, {@code >= 0}; nothing is recorded for 0
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code y} is
     *         in an input's buffer at a different offset, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder min(GpuCommandEncoder encoder, GpuBuffer a, long aOff, GpuBuffer b, long bOff, GpuBuffer y,
            long yOff, int n) {
        return binary(Op.MIN, encoder, a, aOff, b, bOff, y, yOff, n);
    }

    private GpuCommandEncoder unary(Op op, GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer y, long yOff,
            int n) {
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
            return dispatchSelf(op, encoder, x, xOff, n, groups);
        }
        byte[] params = new Kernels.Params(PARAMS).u32(op.ordinal()).u32(n).u32(xOff).u32(xOff).u32(yOff)
                .u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, twoBuffers, Map.of(0, x, 1, x, 2, y), params, groups);
    }

    private GpuCommandEncoder binary(Op op, GpuCommandEncoder encoder, GpuBuffer a, long aOff, GpuBuffer b, long bOff,
            GpuBuffer y, long yOff, int n) {
        Kernels.notNull(encoder, "encoder");
        Kernels.range(a, aOff, n, "a");
        Kernels.range(b, bOff, n, "b");
        Kernels.range(y, yOff, n, "y");
        boolean onA = y == a;
        boolean onB = y == b;
        if ((onA && yOff != aOff) || (onB && yOff != bOff)) {
            throw new IllegalArgumentException("y may share a buffer with an input only at the same offset");
        }
        if (n == 0) {
            return encoder;
        }
        Dimensions groups = groups(n);
        if (onA && onB) {
            return dispatchSelf(op, encoder, y, yOff, n, groups);
        }
        if (onA || onB) {
            GpuBuffer other = onA ? b : a;
            long otherOff = onA ? bOff : aOff;
            byte[] params = new Kernels.Params(PARAMS_IN_PLACE).u32(op.ordinal()).u32(n).u32(yOff).u32(otherOff)
                    .u32(onB ? 1 : 0).u32(groups.x()).bytes();
            return Kernels.dispatch(encoder, inPlace, Map.of(0, y, 1, other), params, groups);
        }
        byte[] params = new Kernels.Params(PARAMS).u32(op.ordinal()).u32(n).u32(aOff).u32(bOff).u32(yOff)
                .u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, twoBuffers, Map.of(0, a, 1, b, 2, y), params, groups);
    }

    private GpuCommandEncoder dispatchSelf(Op op, GpuCommandEncoder encoder, GpuBuffer x, long off, int n,
            Dimensions groups) {
        byte[] params = new Kernels.Params(PARAMS_SELF).u32(op.ordinal()).u32(n).u32(off).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, self, Map.of(0, x), params, groups);
    }

    private Dimensions groups(int n) {
        return Kernels.grid(device.capabilities(), (n + GROUP - 1L) / GROUP);
    }

    @Override
    public void close() {
        try {
            twoBuffers.close();
        } finally {
            try {
                inPlace.close();
            } finally {
                self.close();
            }
        }
    }
}
