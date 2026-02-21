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
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.Test;
import org.vectrix.affine.Transformf;
import org.vectrix.core.Quaternionf;
import org.vectrix.core.Vector3f;
import org.vectrix.gpu.GpuTransformLayout;
import org.vectrix.gpu.Half;
import org.vectrix.gpu.OctaNormal;
import org.vectrix.gpu.PackedNorm;
import org.vectrix.gpu.QuatCompression;

class GpuPackingTest {
    @Test
    void halfRoundTrip() {
        float[] values = {0.0f, 1.0f, -1.0f, 0.125f, -0.75f, 123.5f, -65504.0f};
        for (float v : values) {
            float decoded = Half.unpack(Half.pack(v));
            assertEquals(v, decoded, java.lang.Math.max(java.lang.Math.abs(v) * 0.001f, 0.001f));
        }
    }

    @Test
    void halfSpecialValues() {
        short posInf = Half.pack(Float.POSITIVE_INFINITY);
        short negInf = Half.pack(Float.NEGATIVE_INFINITY);
        short nan = Half.pack(Float.NaN);
        assertEquals(Float.POSITIVE_INFINITY, Half.unpack(posInf));
        assertEquals(Float.NEGATIVE_INFINITY, Half.unpack(negInf));
        assertTrue(Float.isNaN(Half.unpack(nan)));
    }

    @Test
    void normRoundTrip() {
        assertEquals(0.0f, PackedNorm.unpackUnorm8(PackedNorm.packUnorm8(0.0f)), 1.0f / 255.0f);
        assertEquals(1.0f, PackedNorm.unpackUnorm8(PackedNorm.packUnorm8(1.0f)), 1.0f / 255.0f);
        assertEquals(-1.0f, PackedNorm.unpackSnorm8(PackedNorm.packSnorm8(-1.0f)), 1.0f / 127.0f);
        assertEquals(1.0f, PackedNorm.unpackSnorm8(PackedNorm.packSnorm8(1.0f)), 1.0f / 127.0f);
        assertEquals(0.0f, PackedNorm.unpackUnorm16(PackedNorm.packUnorm16(-0.5f)), 1.0f / 65535.0f);
        assertEquals(1.0f, PackedNorm.unpackUnorm16(PackedNorm.packUnorm16(1.5f)), 1.0f / 65535.0f);
        assertEquals(-1.0f, PackedNorm.unpackSnorm16(PackedNorm.packSnorm16(-4.0f)), 1.0f / 32767.0f);
        assertEquals(1.0f, PackedNorm.unpackSnorm16(PackedNorm.packSnorm16(4.0f)), 1.0f / 32767.0f);
    }

    @Test
    void pack8x4WritesEachLane() {
        int unorm = PackedNorm.packUnorm8x4(0.0f, 0.5f, 1.0f, 0.25f);
        assertEquals(PackedNorm.packUnorm8(0.0f), unorm & 0xFF);
        assertEquals(PackedNorm.packUnorm8(0.5f), (unorm >>> 8) & 0xFF);
        assertEquals(PackedNorm.packUnorm8(1.0f), (unorm >>> 16) & 0xFF);
        assertEquals(PackedNorm.packUnorm8(0.25f), (unorm >>> 24) & 0xFF);

        int snorm = PackedNorm.packSnorm8x4(-1.0f, -0.25f, 0.25f, 1.0f);
        assertEquals(PackedNorm.packSnorm8(-1.0f), snorm & 0xFF);
        assertEquals(PackedNorm.packSnorm8(-0.25f), (snorm >>> 8) & 0xFF);
        assertEquals(PackedNorm.packSnorm8(0.25f), (snorm >>> 16) & 0xFF);
        assertEquals(PackedNorm.packSnorm8(1.0f), (snorm >>> 24) & 0xFF);
    }

    @Test
    void octaRoundTrip() {
        Vector3f decoded = new Vector3f();
        int packed = OctaNormal.encodeSnorm16(0.2f, -0.4f, 0.8944272f);
        OctaNormal.decodeSnorm16(packed, decoded);
        assertTrue(decoded.dot(0.2f, -0.4f, 0.8944272f) > 0.999f);
    }

    @Test
    void octaRoundTripNegativeZHemisphere() {
        Vector3f n = new Vector3f(-0.31f, 0.21f, -0.9261203f);
        n.normalize();
        Vector3f decoded = new Vector3f();
        int packed = OctaNormal.encodeSnorm16(n.x, n.y, n.z);
        OctaNormal.decodeSnorm16(packed, decoded);
        assertTrue(decoded.dot(n) > 0.999f);
    }

    @Test
    void quatSmallest3RoundTripErrorBound() {
        Quaternionf q = new Quaternionf().rotationXYZ(0.31f, -1.12f, 0.77f);
        long packed = QuatCompression.packSmallest3(q);
        Quaternionf out = QuatCompression.unpackSmallest3(packed, new Quaternionf());
        float errDeg = QuatCompression.angularErrorDegrees(q, out);
        assertTrue(errDeg < 0.05f);
    }

    @Test
    void gpuTransformLayoutFloatAndCompactWrite() {
        Transformf t = new Transformf();
        t.translation.set(1.25f, -2.5f, 3.75f);
        t.rotation.set(new Quaternionf().rotationXYZ(0.2f, -0.4f, 0.8f));
        t.scale.set(0.5f, 2.0f, 4.0f);

        GpuTransformLayout full = GpuTransformLayout.floatTRS();
        ByteBuffer a = ByteBuffer.allocate(full.requiredBytes(1)).order(ByteOrder.LITTLE_ENDIAN);
        full.write(a, 0, t);
        assertEquals(1.25f, a.getFloat(0), 0.0f);
        assertEquals(-2.5f, a.getFloat(4), 0.0f);
        assertEquals(3.75f, a.getFloat(8), 0.0f);
        assertEquals(0.5f, a.getFloat(28), 0.0f);
        assertEquals(2.0f, a.getFloat(32), 0.0f);
        assertEquals(4.0f, a.getFloat(36), 0.0f);

        GpuTransformLayout compact = GpuTransformLayout.compactTRS();
        ByteBuffer b = ByteBuffer.allocate(compact.requiredBytes(1)).order(ByteOrder.LITTLE_ENDIAN);
        compact.write(b, 0, t);
        assertEquals(1.25f, b.getFloat(0), 0.0f);
        assertEquals(-2.5f, b.getFloat(4), 0.0f);
        assertEquals(3.75f, b.getFloat(8), 0.0f);
        long packedQ = b.getLong(12);
        Quaternionf dq = QuatCompression.unpackSmallest3(packedQ, new Quaternionf());
        assertTrue(QuatCompression.angularErrorDegrees(t.rotation, dq) < 0.05f);
        assertEquals(t.scale.x, Half.unpack(b.getShort(20)), 0.001f);
        assertEquals(t.scale.y, Half.unpack(b.getShort(22)), 0.001f);
        assertEquals(t.scale.z, Half.unpack(b.getShort(24)), 0.001f);
    }
}
