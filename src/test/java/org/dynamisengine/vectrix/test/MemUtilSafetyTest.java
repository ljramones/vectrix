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
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ReadOnlyBufferException;

import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Matrix4x3f;
import org.dynamisengine.vectrix.core.Vector3f;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for review findings C-1/C-2: buffer access through the memory backend must behave like NIO
 * (correct placement, bounds and read-only checks) for heap and direct buffers alike, and must never touch
 * memory outside the buffer. Before the fix, heap buffers crashed the JVM (SIGSEGV) under the Unsafe backend and
 * out-of-range direct writes silently corrupted neighbouring memory.
 *
 * <p>The suite is backend-agnostic. Run it under the default (Unsafe) backend, with {@code -Djoml.nounsafe}
 * (NIO), and in an FFM build with {@code -Djoml.forceFfm=true}.
 */
class MemUtilSafetyTest {

    private static Matrix4f sampleMatrix() {
        Matrix4f m = new Matrix4f();
        float v = 1.0f;
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                m.set(c, r, v++);
            }
        }
        return m;
    }

    /** Column-major expected contents, built without going through the memory backend. */
    private static float[] columnMajor(Matrix4f m) {
        float[] e = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                e[c * 4 + r] = m.get(c, r);
            }
        }
        return e;
    }

    private static float[] read(FloatBuffer b, int from, int count) {
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = b.get(from + i);
        }
        return out;
    }

    private static boolean ffmBackend() {
        try {
            Class<?> c = Class.forName("org.dynamisengine.vectrix.core.MemUtil");
            java.lang.reflect.Field f = c.getField("INSTANCE");
            f.setAccessible(true);
            return f.get(null).getClass().getSimpleName().contains("FFM");
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Test
    void heapFloatBufferAbsoluteAndRelativePut() {
        Matrix4f m = sampleMatrix();
        FloatBuffer heap = FloatBuffer.allocate(40);
        m.get(7, heap);
        assertArrayEquals(columnMajor(m), read(heap, 7, 16));
        assertEquals(0.0f, heap.get(6));
        assertEquals(0.0f, heap.get(23));

        heap.position(20);
        m.get(heap);
        assertArrayEquals(columnMajor(m), read(heap, 20, 16));
        assertEquals(20, heap.position(), "relative get must not move the position");
    }

    @Test
    void heapByteBufferRoundTrip() {
        Matrix4f m = sampleMatrix();
        ByteBuffer heap = ByteBuffer.allocate(128).order(ByteOrder.nativeOrder());
        m.get(8, heap);
        Matrix4f back = new Matrix4f().set(8, heap);
        assertEquals(m, back);
    }

    @Test
    void heapBufferGetIntoObject() {
        FloatBuffer heap = FloatBuffer.allocate(16);
        for (int i = 0; i < 16; i++) {
            heap.put(i, i + 1);
        }
        Matrix4f m = new Matrix4f().set(heap);
        assertArrayEquals(read(heap, 0, 16), columnMajor(m));
        Vector3f v = new Vector3f().set(4, heap);
        assertEquals(new Vector3f(5, 6, 7), v);
    }

    @Test
    void directBufferHonoursPositionAndIndex() {
        Matrix4f m = sampleMatrix();
        FloatBuffer direct = ByteBuffer.allocateDirect(64 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        direct.position(8);
        m.get(direct);
        assertArrayEquals(columnMajor(m), read(direct, 8, 16));
        // A buffer used at a non-zero position must not affect a later absolute write at index 0.
        m.get(0, direct);
        assertArrayEquals(columnMajor(m), read(direct, 0, 16));

        Matrix4x3f a = new Matrix4x3f().translation(1, 2, 3);
        direct.position(30);
        a.get(direct);
        assertEquals(1.0f, direct.get(30));
        assertEquals(1.0f, direct.get(30 + 9));
        assertEquals(2.0f, direct.get(30 + 10));
        assertEquals(3.0f, direct.get(30 + 11));
    }

    @Test
    void directSliceWriteStaysInsideSlice() {
        FloatBuffer parent = ByteBuffer.allocateDirect(128 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        parent.position(52);
        FloatBuffer slice = parent.slice();
        Matrix4f m = sampleMatrix();
        m.get(0, slice);
        assertArrayEquals(columnMajor(m), read(parent, 52, 16));
        for (int i = 0; i < 52; i++) {
            assertEquals(0.0f, parent.get(i), "write leaked before the slice at " + i);
        }
        for (int i = 68; i < 128; i++) {
            assertEquals(0.0f, parent.get(i), "write leaked after the slice at " + i);
        }
    }

    @Test
    void outOfBoundsDirectAccessThrowsInsteadOfCorrupting() {
        assumeFalse(ffmBackend(), "FFM backend has no bounds checks yet (finding H-9); it is opt-in only");
        FloatBuffer parent = ByteBuffer.allocateDirect(256 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        parent.position(16).limit(24);
        FloatBuffer slice = parent.slice(); // capacity 8
        parent.clear();
        Matrix4f m = sampleMatrix();
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(slice));
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(100, slice));
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(-1, slice));
        assertThrows(IndexOutOfBoundsException.class, () -> new Matrix4f().set(0, slice));
        // NIO may write a prefix that fits inside the slice before throwing; nothing may land outside it.
        for (int i = 0; i < 256; i++) {
            if (i < 16 || i >= 24) {
                assertEquals(0.0f, parent.get(i), "out-of-bounds access wrote parent index " + i);
            }
        }
    }

    @Test
    void readOnlyDirectBufferIsNotWritten() {
        assumeFalse(ffmBackend(), "FFM backend has no read-only checks yet (finding H-9); it is opt-in only");
        FloatBuffer direct = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        FloatBuffer readOnly = direct.asReadOnlyBuffer();
        assertThrows(ReadOnlyBufferException.class, () -> sampleMatrix().get(0, readOnly));
        assertArrayEquals(new float[16], read(direct, 0, 16));
    }

    @Test
    void heapAndDirectProduceIdenticalBytes() {
        Matrix4f m = sampleMatrix().rotateXYZ(0.3f, -1.1f, 2.0f);
        ByteBuffer heap = ByteBuffer.allocate(96).order(ByteOrder.nativeOrder());
        ByteBuffer direct = ByteBuffer.allocateDirect(96).order(ByteOrder.nativeOrder());
        m.get(12, heap);
        m.get(12, direct);
        for (int i = 0; i < 96; i++) {
            assertEquals(heap.get(i), direct.get(i), "byte " + i);
        }
    }
}
