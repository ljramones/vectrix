/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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
package org.vectrix.gpu;

import java.nio.ByteBuffer;

import org.vectrix.affine.Transformf;

/**
 * Descriptor/writer for GPU transform instance layouts.
 */
public final class GpuTransformLayout {
    public enum RotationEncoding {
        FLOAT4,
        QUAT_SMALLEST3_16
    }

    public enum ScaleEncoding {
        FLOAT3,
        HALF3
    }

    public final int strideBytes;
    public final int translationOffset;
    public final int rotationOffset;
    public final int scaleOffset;
    public final RotationEncoding rotationEncoding;
    public final ScaleEncoding scaleEncoding;

    public GpuTransformLayout(int strideBytes, int translationOffset, int rotationOffset, int scaleOffset,
            RotationEncoding rotationEncoding, ScaleEncoding scaleEncoding) {
        this.strideBytes = strideBytes;
        this.translationOffset = translationOffset;
        this.rotationOffset = rotationOffset;
        this.scaleOffset = scaleOffset;
        this.rotationEncoding = rotationEncoding;
        this.scaleEncoding = scaleEncoding;
    }

    public static GpuTransformLayout floatTRS() {
        return new GpuTransformLayout(40, 0, 12, 28, RotationEncoding.FLOAT4, ScaleEncoding.FLOAT3);
    }

    public static GpuTransformLayout compactTRS() {
        return new GpuTransformLayout(28, 0, 12, 20, RotationEncoding.QUAT_SMALLEST3_16, ScaleEncoding.HALF3);
    }

    public int requiredBytes(int count) {
        return count * strideBytes;
    }

    public void write(ByteBuffer dst, int index, Transformf transform) {
        int base = index * strideBytes;
        dst.putFloat(base + translationOffset, transform.translation.x);
        dst.putFloat(base + translationOffset + 4, transform.translation.y);
        dst.putFloat(base + translationOffset + 8, transform.translation.z);

        if (rotationEncoding == RotationEncoding.FLOAT4) {
            dst.putFloat(base + rotationOffset, transform.rotation.x);
            dst.putFloat(base + rotationOffset + 4, transform.rotation.y);
            dst.putFloat(base + rotationOffset + 8, transform.rotation.z);
            dst.putFloat(base + rotationOffset + 12, transform.rotation.w);
        } else {
            long packed = QuatCompression.packSmallest3(transform.rotation);
            dst.putLong(base + rotationOffset, packed);
        }

        if (scaleEncoding == ScaleEncoding.FLOAT3) {
            dst.putFloat(base + scaleOffset, transform.scale.x);
            dst.putFloat(base + scaleOffset + 4, transform.scale.y);
            dst.putFloat(base + scaleOffset + 8, transform.scale.z);
        } else {
            dst.putShort(base + scaleOffset, Half.pack(transform.scale.x));
            dst.putShort(base + scaleOffset + 2, Half.pack(transform.scale.y));
            dst.putShort(base + scaleOffset + 4, Half.pack(transform.scale.z));
        }
    }
}
