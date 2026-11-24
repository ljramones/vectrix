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
 * GPU twins of Vectrix kernels: {@link org.dynamisengine.vectrix.compute.GpuGemmf} (of {@code blas.Gemmf}),
 * {@link org.dynamisengine.vectrix.compute.GpuPhilox} (of {@code hash.Philox}) and
 * {@link org.dynamisengine.vectrix.compute.GpuSoftmax} (of {@code nn.Softmax}).
 * <p>
 * Each class compiles its pipelines on one DynamisGPU device and records dispatches into that device's command
 * encoders. The signatures follow the Vectrix methods, with a {@link org.dynamisengine.gpu.api.compute.GpuBuffer}
 * and an element offset in place of an array and its offset. Arguments are checked before anything is recorded and
 * rejected with {@link IllegalArgumentException}, as the CPU kernels do; device limits and binding conflicts are
 * reported by DynamisGPU as {@link org.dynamisengine.gpu.api.compute.ComputeException}.
 * <p>
 * <b>Results.</b> Each kernel runs the Vectrix algorithm itself (the same summation tree, the same exponential
 * polynomial, Java's {@code Math.max}), and the shaders are compiled so that every operation rounds as written. On a
 * device with IEEE single-precision arithmetic (correctly rounded add, multiply and divide, and a fused
 * multiply-add) the results are the bits Vectrix computes on the CPU. Integer kernels such as Philox match on every
 * device. The exception is subnormal values (magnitude below {@code 2^-126}): GPUs that flush them to zero,
 * including every Apple GPU, produce zero where Vectrix produces a subnormal, and anything computed from that value
 * follows from the zero. DynamisGPU 1.0.3 or later is needed on MoltenVK: earlier versions leave Metal fast math
 * on, which reassociates arithmetic.
 * <p>
 * Buffers hold little-endian 32-bit values. Like DynamisGPU devices, instances are confined to the thread that
 * created their device.
 */
package org.dynamisengine.vectrix.compute;
