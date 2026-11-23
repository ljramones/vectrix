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
 * </ul>
 * Vectrix never calls a graphics API: DynamisGPU binds these contracts.
 */
package org.dynamisengine.vectrix.gpu;
