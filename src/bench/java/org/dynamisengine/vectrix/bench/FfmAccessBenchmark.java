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
package org.dynamisengine.vectrix.bench;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.TimeUnit;

import org.dynamisengine.vectrix.core.Matrix4f;
import org.openjdk.jmh.annotations.*;

/**
 * Cost of the layers of one 64-byte FFM write (8 long stores), to locate the fixed per-call overhead of the FFM
 * backend relative to Unsafe: segment creation from a buffer, slicing, and the store bounds checks.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 2, jvmArgsAppend = {"--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow"})
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class FfmAccessBenchmark {
    private static final ValueLayout.OfLong L = ValueLayout.JAVA_LONG_UNALIGNED;
    private static final MemorySegment ALL = MemorySegment.NULL.reinterpret(Long.MAX_VALUE);
    private static final sun.misc.Unsafe U;

    static {
        try {
            java.lang.reflect.Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (sun.misc.Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ByteBuffer bb;
    private MemorySegment cached;
    private long address;
    private long v;
    private final Matrix4f m = new Matrix4f().rotateXYZ(0.1f, 0.2f, 0.3f).translate(1, 2, 3);
    private static final ValueLayout.OfFloat F = ValueLayout.JAVA_FLOAT_UNALIGNED;

    @Setup
    public void setup() {
        bb = ByteBuffer.allocateDirect(256).order(ByteOrder.nativeOrder());
        cached = MemorySegment.ofBuffer(bb);
        address = cached.address();
        v = 0x3F8000003F800000L;
    }

    private static void store8(MemorySegment s, long o, long v) {
        s.set(L, o, v);
        s.set(L, o + 8, v);
        s.set(L, o + 16, v);
        s.set(L, o + 24, v);
        s.set(L, o + 32, v);
        s.set(L, o + 40, v);
        s.set(L, o + 48, v);
        s.set(L, o + 56, v);
    }

    @Benchmark
    public ByteBuffer ofBufferSliced() {
        store8(MemorySegment.ofBuffer(bb).asSlice(64, 64), 0, v);
        return bb;
    }

    @Benchmark
    public ByteBuffer ofBufferUnsliced() {
        store8(MemorySegment.ofBuffer(bb), 64, v);
        return bb;
    }

    @Benchmark
    public ByteBuffer cachedSegment() {
        store8(cached, 64, v);
        return bb;
    }

    @Benchmark
    public ByteBuffer addressSpaceSegment() {
        store8(ALL, address + 64, v);
        return bb;
    }

    @Benchmark
    public ByteBuffer unsafe() {
        long a = address + 64;
        U.putLong(a, v);
        U.putLong(a + 8, v);
        U.putLong(a + 16, v);
        U.putLong(a + 24, v);
        U.putLong(a + 32, v);
        U.putLong(a + 40, v);
        U.putLong(a + 48, v);
        U.putLong(a + 56, v);
        return bb;
    }
    /** The real call: Matrix4f.get(index, ByteBuffer) on the default (FFM) backend. */
    @Benchmark
    public ByteBuffer matrixGetFfm() {
        return m.get(64, bb);
    }

    private static long pack(float a, float b) {
        return (Float.floatToRawIntBits(b) & 0xFFFFFFFFL) << 32 | (Float.floatToRawIntBits(a) & 0xFFFFFFFFL);
    }

    /** The backend's stores without its checks: 8 packed longs into a fresh slice. */
    @Benchmark
    public ByteBuffer matrixPacked() {
        MemorySegment s = MemorySegment.ofBuffer(bb).asSlice(64, 64);
        Matrix4f x = m;
        s.set(L, 0, pack(x.m00(), x.m01()));
        s.set(L, 8, pack(x.m02(), x.m03()));
        s.set(L, 16, pack(x.m10(), x.m11()));
        s.set(L, 24, pack(x.m12(), x.m13()));
        s.set(L, 32, pack(x.m20(), x.m21()));
        s.set(L, 40, pack(x.m22(), x.m23()));
        s.set(L, 48, pack(x.m30(), x.m31()));
        s.set(L, 56, pack(x.m32(), x.m33()));
        return bb;
    }

    /** 16 float stores into a fresh slice instead of 8 packed longs. */
    @Benchmark
    public ByteBuffer matrixFloats() {
        MemorySegment s = MemorySegment.ofBuffer(bb).asSlice(64, 64);
        Matrix4f x = m;
        s.set(F, 0, x.m00()); s.set(F, 4, x.m01()); s.set(F, 8, x.m02()); s.set(F, 12, x.m03());
        s.set(F, 16, x.m10()); s.set(F, 20, x.m11()); s.set(F, 24, x.m12()); s.set(F, 28, x.m13());
        s.set(F, 32, x.m20()); s.set(F, 36, x.m21()); s.set(F, 40, x.m22()); s.set(F, 44, x.m23());
        s.set(F, 48, x.m30()); s.set(F, 52, x.m31()); s.set(F, 56, x.m32()); s.set(F, 60, x.m33());
        return bb;
    }

    /** The backend's applicability checks alone (direct, writable, native order). */
    @Benchmark
    public boolean checksOnly() {
        ByteBuffer b = bb;
        return b.isDirect() && !b.isReadOnly() && b.order() == ByteOrder.nativeOrder();
    }

    /** Unsafe backend reference: 16 float stores at the buffer address. */
    @Benchmark
    public ByteBuffer matrixUnsafe() {
        long a = address + 64;
        Matrix4f x = m;
        U.putFloat(a, x.m00()); U.putFloat(a + 4, x.m01()); U.putFloat(a + 8, x.m02()); U.putFloat(a + 12, x.m03());
        U.putFloat(a + 16, x.m10()); U.putFloat(a + 20, x.m11()); U.putFloat(a + 24, x.m12()); U.putFloat(a + 28, x.m13());
        U.putFloat(a + 32, x.m20()); U.putFloat(a + 36, x.m21()); U.putFloat(a + 40, x.m22()); U.putFloat(a + 44, x.m23());
        U.putFloat(a + 48, x.m30()); U.putFloat(a + 52, x.m31()); U.putFloat(a + 56, x.m32()); U.putFloat(a + 60, x.m33());
        return bb;
    }
}
