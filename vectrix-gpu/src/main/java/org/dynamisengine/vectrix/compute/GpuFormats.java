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

/**
 * The GPU twin of Vectrix {@code nn.Formats}: bfloat16 and FP8 (E4M3, E5M2) conversions over buffers, and int8 block
 * quantization with its quantized dot product and matrix-vector product.
 * <p>
 * <b>Packing.</b> Buffers are 32-bit words. Floats, scales and int8 dot results are one per word. The 8- and 16-bit
 * data is packed little-endian, element 0 in the low bits of word 0: two bfloat16 per word ({@code h[i]} is bits
 * {@code 16·(i % 2)} and up of word {@code i / 2}), four FP8 or int8 codes per word ({@code q[i]} is bits
 * {@code 8·(i % 4)} and up of word {@code i / 4}). That is the byte layout of a {@code short[]} or {@code byte[]}
 * written little-endian, so the bytes of a Vectrix array can be uploaded as they are. Offsets and counts of packed
 * data are in elements and need not be aligned to words: a word only partly inside the written range keeps its other
 * elements.
 * <p>
 * <b>Results.</b> Each kernel runs the Vectrix method's arithmetic. The conversions, {@code quantizeInt8} and
 * {@code dequantizeInt8} give the bits Vectrix computes on every device with correctly rounded single-precision
 * division, including devices that flush subnormal floats to zero: they work on the raw bits where the Java
 * arithmetic is exact, and rescale subnormal operands by {@code 2^64} where it divides. {@link #dotInt8} and
 * {@link #gemvInt8} give the Vectrix bits on an IEEE device (fused multiply-adds in Vectrix's order); on a device that
 * flushes subnormals, a subnormal scale, input or partial sum is read as zero (see the package comment).
 * <p>
 * <b>Non-finite input to {@code quantizeInt8}.</b> The CPU method rejects a NaN or infinite value before writing
 * anything; the GPU cannot check the data before it runs, so a block holding one gets the scale NaN
 * ({@code 0x7FC00000}) and all-zero codes, and the other blocks are quantized normally.
 * <p>
 * Seven pipelines, compiled when the instance is created. Destinations must be different buffers from the inputs.
 *
 * @since 3.2.0
 */
public final class GpuFormats implements AutoCloseable {

    /** The parameter block of the bfloat16 encoder (checked against the compiled kernel). */
    static final String[] PARAMS_TO_BF16 = {"xOff", "hOff", "n", "gridX"};

    /** The parameter block of the bfloat16 decoder (checked against the compiled kernel). */
    static final String[] PARAMS_FROM_BF16 = {"hOff", "xOff", "n", "gridX"};

    /** The parameter block of the FP8 encoder (checked against the compiled kernel). */
    static final String[] PARAMS_TO_FP8 = {"xOff", "cOff", "n", "e5m2", "saturate", "gridX"};

    /** The parameter block of the FP8 decoder (checked against the compiled kernel). */
    static final String[] PARAMS_FROM_FP8 = {"cOff", "xOff", "n", "e5m2", "gridX"};

    /** The parameter block of the int8 quantizer (checked against the compiled kernel). */
    static final String[] PARAMS_QUANTIZE = {"xOff", "n", "qOff", "sOff", "gridX"};

    /** The parameter block of the int8 dequantizer (checked against the compiled kernel). */
    static final String[] PARAMS_DEQUANTIZE = {"qOff", "sOff", "xOff", "n", "gridX"};

    /** The parameter block of the int8 matrix-vector product (checked against the compiled kernel). */
    static final String[] PARAMS_GEMV = {"m", "n", "qOff", "sOff", "xOff", "yOff", "gridX"};

    /** The int8 quantization block length, Vectrix {@code Formats.QBLOCK}: each block of 32 shares one scale. */
    public static final int QBLOCK = 32;

    private static final int GROUP = 64;

    private static final int QUANTIZE_GROUP = 256;

    /** Rows per group of the matrix-vector kernel: 8 threads (lanes) per row. */
    private static final int GEMV_ROWS = 8;

    private static final Set<String> TYPES = Set.of("INT32", "FP32");

    private final GpuDevice device;

    private final List<GpuComputePipeline> pipelines = new ArrayList<>();

    private final GpuComputePipeline toBf16;

    private final GpuComputePipeline fromBf16;

    private final GpuComputePipeline toFp8;

    private final GpuComputePipeline fromFp8;

    private final GpuComputePipeline quantize;

    private final GpuComputePipeline dequantize;

    private final GpuComputePipeline gemv;

    /**
     * Compiles the kernels on {@code device}.
     *
     * @param device the device to run on
     * @throws IllegalArgumentException if {@code device} is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the device cannot create the pipelines
     */
    public GpuFormats(GpuDevice device) {
        Kernels.notNull(device, "device");
        try {
            toBf16 = add(device, "to_bf16", GROUP, PARAMS_TO_BF16, Access.READ, Access.READ_WRITE);
            fromBf16 = add(device, "from_bf16", GROUP, PARAMS_FROM_BF16, Access.READ, Access.WRITE);
            toFp8 = add(device, "to_fp8", GROUP, PARAMS_TO_FP8, Access.READ, Access.READ_WRITE);
            fromFp8 = add(device, "from_fp8", GROUP, PARAMS_FROM_FP8, Access.READ, Access.WRITE);
            quantize = add(device, "quantize_int8", QUANTIZE_GROUP, PARAMS_QUANTIZE, Access.READ, Access.READ_WRITE,
                    Access.WRITE);
            dequantize = add(device, "dequantize_int8", GROUP, PARAMS_DEQUANTIZE, Access.READ, Access.READ,
                    Access.WRITE);
            gemv = add(device, "gemv_int8", GEMV_ROWS * 8, PARAMS_GEMV, Access.READ, Access.READ, Access.READ,
                    Access.WRITE);
        } catch (RuntimeException e) {
            close();
            throw e;
        }
        this.device = device;
    }

    private GpuComputePipeline add(GpuDevice d, String name, int group, String[] params, Access... slots) {
        GpuComputePipeline p = Kernels.pipeline(d, name, new Dimensions(group, 1, 1), params, TYPES, slots);
        pipelines.add(p);
        return p;
    }

    /**
     * Records {@code h[hOff + i] = Formats.toBf16(x[xOff + i])} for {@code i} in {@code [0, n)}: round to nearest,
     * ties to even; a NaN keeps its sign and payload with the quiet bit set. {@code h} holds two bfloat16 per word.
     * Nothing is recorded when {@code n} is 0.
     *
     * @param encoder the encoder to record into
     * @param x the floats
     * @param xOff the element index of the first float
     * @param h receives the bfloat16 bit patterns, packed two per word
     * @param hOff the bfloat16 index of the first result (any value; a partly covered word keeps its other half)
     * @param n the number of values, {@code >= 0}
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code h} is
     *         {@code x}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder toBf16(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer h, long hOff,
            int n) {
        Kernels.notNull(encoder, "encoder");
        Kernels.range(x, xOff, n, "x");
        packed(h, hOff, n, 2, "h");
        distinct(h, "h", x, "x");
        if (n == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), groups(words(hOff, n, 2), GROUP));
        byte[] params = new Kernels.Params(PARAMS_TO_BF16).u32(xOff).u32(hOff).u32(n).u32(groups.x())
                .bytes();
        return Kernels.dispatch(encoder, toBf16, Map.of(0, x, 1, h), params, groups);
    }

    /**
     * Records {@code x[xOff + i] = Formats.fromBf16(h[hOff + i])} for {@code i} in {@code [0, n)}, exactly.
     * {@code h} holds two bfloat16 per word. Nothing is recorded when {@code n} is 0.
     *
     * @param encoder the encoder to record into
     * @param h the bfloat16 bit patterns, packed two per word
     * @param hOff the bfloat16 index of the first value
     * @param x receives the floats
     * @param xOff the element index of the first result
     * @param n the number of values, {@code >= 0}
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code x} is
     *         {@code h}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder fromBf16(GpuCommandEncoder encoder, GpuBuffer h, long hOff, GpuBuffer x, long xOff,
            int n) {
        Kernels.notNull(encoder, "encoder");
        packed(h, hOff, n, 2, "h");
        Kernels.range(x, xOff, n, "x");
        distinct(x, "x", h, "h");
        if (n == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), groups(n, GROUP));
        byte[] params = new Kernels.Params(PARAMS_FROM_BF16).u32(hOff).u32(xOff).u32(n).u32(groups.x())
                .bytes();
        return Kernels.dispatch(encoder, fromBf16, Map.of(0, h, 1, x), params, groups);
    }

    /**
     * Records {@code codes[cOff + i] = Formats.toE4M3(x[xOff + i], saturate)} for {@code i} in {@code [0, n)}: the
     * OCP E4M3 "FN" code, rounding to nearest, ties to even; NaN gives {@code 0x7f}, and values beyond the range give
     * NaN ({@code 0x7f} with the sign), or ±448 when saturating. {@code codes} holds four codes per word. Nothing is
     * recorded when {@code n} is 0.
     *
     * @param encoder the encoder to record into
     * @param x the floats
     * @param xOff the element index of the first float
     * @param codes receives the codes, packed four per word
     * @param cOff the code index of the first result (any value; a partly covered word keeps its other codes)
     * @param n the number of values, {@code >= 0}
     * @param saturate whether values beyond the range give ±448 instead of NaN
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code codes} is
     *         {@code x}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder toE4M3(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer codes, long cOff,
            int n, boolean saturate) {
        return toFp8(encoder, x, xOff, codes, cOff, n, false, saturate);
    }

    /**
     * Records {@code x[xOff + i] = Formats.fromE4M3(codes[cOff + i])} for {@code i} in {@code [0, n)}, exactly; codes
     * {@code 0x7f} and {@code 0xff} give NaN ({@code 0x7FC00000}). {@code codes} holds four codes per word. Nothing
     * is recorded when {@code n} is 0.
     *
     * @param encoder the encoder to record into
     * @param codes the E4M3 codes, packed four per word
     * @param cOff the code index of the first value
     * @param x receives the floats
     * @param xOff the element index of the first result
     * @param n the number of values, {@code >= 0}
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code x} is
     *         {@code codes}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder fromE4M3(GpuCommandEncoder encoder, GpuBuffer codes, long cOff, GpuBuffer x, long xOff,
            int n) {
        return fromFp8(encoder, codes, cOff, x, xOff, n, false);
    }

    /**
     * Records {@code codes[cOff + i] = Formats.toE5M2(x[xOff + i], saturate)} for {@code i} in {@code [0, n)}: the
     * E5M2 code, rounding to nearest, ties to even; NaN gives {@code 0x7f}, and values beyond the range give
     * ±infinity, or ±57344 when saturating. {@code codes} holds four codes per word. Nothing is recorded when
     * {@code n} is 0.
     *
     * @param encoder the encoder to record into
     * @param x the floats
     * @param xOff the element index of the first float
     * @param codes receives the codes, packed four per word
     * @param cOff the code index of the first result (any value; a partly covered word keeps its other codes)
     * @param n the number of values, {@code >= 0}
     * @param saturate whether values beyond the range give ±57344 instead of ±infinity
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code codes} is
     *         {@code x}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder toE5M2(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer codes, long cOff,
            int n, boolean saturate) {
        return toFp8(encoder, x, xOff, codes, cOff, n, true, saturate);
    }

    /**
     * Records {@code x[xOff + i] = Formats.fromE5M2(codes[cOff + i])} for {@code i} in {@code [0, n)}, exactly,
     * infinities included; every NaN code gives {@code 0x7FC00000}. {@code codes} holds four codes per word. Nothing
     * is recorded when {@code n} is 0.
     *
     * @param encoder the encoder to record into
     * @param codes the E5M2 codes, packed four per word
     * @param cOff the code index of the first value
     * @param x receives the floats
     * @param xOff the element index of the first result
     * @param n the number of values, {@code >= 0}
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} or an offset is negative, a buffer is too small, {@code x} is
     *         {@code codes}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder fromE5M2(GpuCommandEncoder encoder, GpuBuffer codes, long cOff, GpuBuffer x, long xOff,
            int n) {
        return fromFp8(encoder, codes, cOff, x, xOff, n, true);
    }

    private GpuCommandEncoder toFp8(GpuCommandEncoder encoder, GpuBuffer x, long xOff, GpuBuffer codes, long cOff,
            int n, boolean e5m2, boolean saturate) {
        Kernels.notNull(encoder, "encoder");
        Kernels.range(x, xOff, n, "x");
        packed(codes, cOff, n, 4, "codes");
        distinct(codes, "codes", x, "x");
        if (n == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), groups(words(cOff, n, 4), GROUP));
        byte[] params = new Kernels.Params(PARAMS_TO_FP8).u32(xOff).u32(cOff).u32(n).u32(e5m2 ? 1 : 0)
                .u32(saturate ? 1 : 0).u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, toFp8, Map.of(0, x, 1, codes), params, groups);
    }

    private GpuCommandEncoder fromFp8(GpuCommandEncoder encoder, GpuBuffer codes, long cOff, GpuBuffer x, long xOff,
            int n, boolean e5m2) {
        Kernels.notNull(encoder, "encoder");
        packed(codes, cOff, n, 4, "codes");
        Kernels.range(x, xOff, n, "x");
        distinct(x, "x", codes, "codes");
        if (n == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), groups(n, GROUP));
        byte[] params = new Kernels.Params(PARAMS_FROM_FP8).u32(cOff).u32(xOff).u32(n).u32(e5m2 ? 1 : 0)
                .u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, fromFp8, Map.of(0, codes, 1, x), params, groups);
    }

    /**
     * Records {@code Formats.quantizeInt8}: {@code n} floats from {@code x[xOff]}, by blocks of {@link #QBLOCK},
     * become int8 codes {@code q[qOff ..]} (four per word) and one scale per block {@code scales[sOff ..]}. Each
     * block's scale is {@code max|x| / 127} (rounded up to a float when below {@code 2^-126}), and each code is
     * {@code rint(x / scale)} within ±127; an all-zero block gets scale 0 and codes 0. A block holding a NaN or an
     * infinity gets the scale NaN and codes 0 (the CPU method throws instead; see the class comment). Nothing is
     * recorded when {@code n} is 0.
     *
     * @param encoder the encoder to record into
     * @param x the floats
     * @param xOff the element index of the first float
     * @param n the number of values, a non-negative multiple of 32
     * @param q receives {@code n} codes, packed four per word
     * @param qOff the code index of the first code (any value; a partly covered word keeps its other codes)
     * @param scales receives {@code n / 32} scales
     * @param sOff the element index of the first scale
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} is not a non-negative multiple of 32, an offset is negative, a
     *         buffer is too small, two of the buffers are the same, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder quantizeInt8(GpuCommandEncoder encoder, GpuBuffer x, long xOff, int n, GpuBuffer q,
            long qOff, GpuBuffer scales, long sOff) {
        Kernels.notNull(encoder, "encoder");
        blockCount(n);
        Kernels.range(x, xOff, n, "x");
        packed(q, qOff, n, 4, "q");
        Kernels.range(scales, sOff, n / QBLOCK, "scales");
        distinct(q, "q", x, "x");
        distinct(scales, "scales", x, "x");
        distinct(scales, "scales", q, "q");
        if (n == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), groups(words(qOff, n, 4), QUANTIZE_GROUP));
        byte[] params = new Kernels.Params(PARAMS_QUANTIZE).u32(xOff).u32(n).u32(qOff).u32(sOff)
                .u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, quantize, Map.of(0, x, 1, q, 2, scales), params, groups);
    }

    /**
     * Records {@code Formats.dequantizeInt8}: {@code x[xOff + i] = q[qOff + i]·scales[sOff + i / 32]} for {@code i}
     * in {@code [0, n)}, {@code q} packed four codes per word. Nothing is recorded when {@code n} is 0.
     *
     * @param encoder the encoder to record into
     * @param q the codes, packed four per word
     * @param qOff the code index of the first code
     * @param scales the {@code n / 32} block scales
     * @param sOff the element index of the first scale
     * @param x receives {@code n} floats
     * @param xOff the element index of the first result
     * @param n the number of values, a non-negative multiple of 32
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} is not a non-negative multiple of 32, an offset is negative, a
     *         buffer is too small, {@code x} is {@code q} or {@code scales}, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder dequantizeInt8(GpuCommandEncoder encoder, GpuBuffer q, long qOff, GpuBuffer scales,
            long sOff, GpuBuffer x, long xOff, int n) {
        Kernels.notNull(encoder, "encoder");
        blockCount(n);
        packed(q, qOff, n, 4, "q");
        Kernels.range(scales, sOff, n / QBLOCK, "scales");
        Kernels.range(x, xOff, n, "x");
        distinct(x, "x", q, "q");
        distinct(x, "x", scales, "scales");
        if (n == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), groups(n, GROUP));
        byte[] params = new Kernels.Params(PARAMS_DEQUANTIZE).u32(qOff).u32(sOff).u32(xOff).u32(n)
                .u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, dequantize, Map.of(0, q, 1, scales, 2, x), params, groups);
    }

    /**
     * Records {@code dest[destOff] = Formats.dotInt8(q, qOff, scales, sOff, x, xOff, n)}: the dot product of
     * {@code n} int8 block-quantized weights (codes packed four per word) with floats, without dequantizing, in
     * Vectrix's order (eight lanes, fused multiply-adds, {@code Blasf}'s lane combine). For {@code n == 0} it writes
     * 0. This is {@link #gemvInt8} with one row.
     *
     * @param encoder the encoder to record into
     * @param q the codes, packed four per word
     * @param qOff the code index of the first code
     * @param scales the {@code n / 32} block scales
     * @param sOff the element index of the first scale
     * @param x the floats
     * @param xOff the element index of the first float
     * @param n the number of elements, a non-negative multiple of 32
     * @param dest receives the dot product
     * @param destOff the element index of the result
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code n} is not a non-negative multiple of 32, an offset is negative, a
     *         buffer is too small, {@code dest} is one of the inputs, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder dotInt8(GpuCommandEncoder encoder, GpuBuffer q, long qOff, GpuBuffer scales, long sOff,
            GpuBuffer x, long xOff, int n, GpuBuffer dest, long destOff) {
        Kernels.notNull(encoder, "encoder");
        blockCount(n);
        return gemv(encoder, 1, n, q, qOff, scales, sOff, x, xOff, dest, destOff, "dest");
    }

    /**
     * Records {@code Formats.gemvInt8}: {@code y = W·x} for an {@code m×n} int8 block-quantized row-major {@code W}
     * (row {@code r} at codes {@code q[qOff + r·n ..]}, packed four per word, and scales
     * {@code scales[sOff + r·n/32 ..]}); each {@code y[yOff + r]} is {@link #dotInt8} of the row and {@code x}.
     * Nothing is recorded when {@code m} is 0; for {@code n == 0} the rows are 0.
     *
     * @param encoder the encoder to record into
     * @param m the number of rows, {@code >= 0}
     * @param n the number of columns, a non-negative multiple of 32
     * @param q the codes of {@code W}, row-major, packed four per word
     * @param qOff the code index of row 0
     * @param scales the block scales of {@code W}, {@code n / 32} per row
     * @param sOff the element index of row 0's scales
     * @param x the vector, {@code n} floats
     * @param xOff the element index of the first float
     * @param y receives {@code m} floats
     * @param yOff the element index of the first result
     * @return {@code encoder}
     * @throws IllegalArgumentException if {@code m < 0}, {@code n} is not a non-negative multiple of 32, an offset is
     *         negative, a buffer is too small, {@code y} is one of the inputs, or an argument is null
     * @throws org.dynamisengine.gpu.api.compute.ComputeException if the dispatch exceeds the device's limits
     */
    public GpuCommandEncoder gemvInt8(GpuCommandEncoder encoder, int m, int n, GpuBuffer q, long qOff,
            GpuBuffer scales, long sOff, GpuBuffer x, long xOff, GpuBuffer y, long yOff) {
        Kernels.notNull(encoder, "encoder");
        if (m < 0) {
            throw new IllegalArgumentException("m must not be negative: " + m);
        }
        blockCount(n);
        return gemv(encoder, m, n, q, qOff, scales, sOff, x, xOff, y, yOff, "y");
    }

    private GpuCommandEncoder gemv(GpuCommandEncoder encoder, int m, int n, GpuBuffer q, long qOff, GpuBuffer scales,
            long sOff, GpuBuffer x, long xOff, GpuBuffer y, long yOff, String yName) {
        packed(q, qOff, (long) m * n, 4, "q");
        Kernels.range(scales, sOff, (long) m * (n / QBLOCK), "scales");
        Kernels.range(x, xOff, n, "x");
        Kernels.range(y, yOff, m, yName);
        distinct(y, yName, q, "q");
        distinct(y, yName, scales, "scales");
        distinct(y, yName, x, "x");
        if (m == 0) {
            return encoder;
        }
        Dimensions groups = Kernels.grid(device.capabilities(), groups(m, GEMV_ROWS));
        byte[] params = new Kernels.Params(PARAMS_GEMV).u32(m).u32(n).u32(qOff).u32(sOff).u32(xOff).u32(yOff)
                .u32(groups.x()).bytes();
        return Kernels.dispatch(encoder, gemv, Map.of(0, q, 1, scales, 2, x, 3, y), params, groups);
    }

    /** Releases the pipelines; work already recorded keeps them alive until it completes. Idempotent. */
    @Override
    public void close() {
        RuntimeException first = null;
        for (GpuComputePipeline p : pipelines) {
            try {
                p.close();
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    private static void blockCount(int n) {
        if (n < 0 || n % QBLOCK != 0) {
            throw new IllegalArgumentException("n must be a multiple of " + QBLOCK + ": " + n);
        }
    }

    /**
     * Checks that {@code n} packed elements, {@code perWord} to a 32-bit word, fit {@code buffer} from element
     * {@code off}, with 32-bit element indices.
     */
    private static void packed(GpuBuffer buffer, long off, long n, int perWord, String name) {
        Kernels.notNull(buffer, name);
        long capacity = Kernels.elements(buffer) * perWord;
        if (n < 0 || off < 0 || off > capacity - n || off + n - 1 > Kernels.MAX_INDEX) {
            throw new IllegalArgumentException(name + " must hold " + n + " packed values from offset " + off
                    + " (has " + capacity + ", " + perWord + " per word)");
        }
    }

    private static void distinct(GpuBuffer out, String outName, GpuBuffer in, String inName) {
        if (out == in) {
            throw new IllegalArgumentException(outName + " must be a different buffer from " + inName);
        }
    }

    /** The number of words holding packed elements {@code [off, off + n)}, {@code perWord} to a word; n >= 1. */
    private static long words(long off, long n, int perWord) {
        return (off + n - 1) / perWord - off / perWord + 1;
    }

    private static long groups(long work, int perGroup) {
        return (work + perGroup - 1) / perGroup;
    }
}
