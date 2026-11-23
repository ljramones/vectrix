/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix Contributors
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
 * GPU data contracts: the layouts and encodings that CPU writers and shaders must agree on.
 * <ul>
 * <li>Interface-block layouts ({@code ShaderLayout}: std140, std430 and scalar, checked against glslang), which can
 * emit their GLSL declarations.</li>
 * <li>Transform layouts and writers (always little-endian).</li>
 * <li>Vertex layouts.</li>
 * <li>Codecs:
 * <ul>
 * <li>half floats;</li>
 * <li>unorm/snorm 8 and 16 bits;</li>
 * <li>10_10_10_2, RGB9E5 and R11G11B10F;</li>
 * <li>sRGB8;</li>
 * <li>octahedral normals, QTangent and smallest-three quaternions.</li>
 * </ul></li>
 * <li>Meshlet normal cones.</li>
 * <li>A reference instance-submission pipeline (TRS to packed affine, bounds and upload staging).</li>
 * </ul>
 * Vectrix never calls a graphics API: DynamisGPU binds these contracts. {@code StdLayout} is a small helper for flat
 * blocks of {@code float}, {@code vec2}-{@code vec4} and {@code mat4}; {@code ShaderLayout} implements the full
 * rules.
 * <p>
 * <b>Contract.</b> Threading: the codecs, layout calculators and write kernels are stateless and safe to call from
 * any thread; {@code GpuTransformLayout}, {@code VertexLayout}, {@code VertexAttribute} and the {@code ShaderLayout}
 * types are immutable and shareable; an {@code InstanceSubmissionPipeline} holds mutable scratch and must be used by
 * one thread at a time. Allocation: the codecs and the transform writers do not allocate; {@code ShaderLayout},
 * {@code StdLayout} offsets, the vertex layout constructors, {@code MeshletCones.normalCone} and the QTangent
 * encoder allocate (setup and asset packing); {@code InstanceSubmissionPipeline} allocates only when the instance or
 * joint count grows past its capacity. Determinism: the codecs use exact integer and IEEE double arithmetic, and the
 * sRGB curve and quaternion error use {@code AccurateMath}, so the bits match across platforms under
 * {@code -Dvectrix.strictMath}. Errors: the layout descriptors throw {@code IllegalArgumentException} for an invalid
 * description; the codecs clamp out-of-range inputs and do not throw; the write kernels do not check their
 * arguments, so a bad index or count throws {@code IndexOutOfBoundsException} (or its array subclass) from the
 * array or buffer access, possibly after part of the output was written ({@code GpuTransformLayout} throws
 * {@code ArithmeticException} when the byte offset overflows). Precision and naming: the codecs and
 * writers take {@code float} data; several compute internally in double for exact rounding.
 */
package org.dynamisengine.vectrix.gpu;
