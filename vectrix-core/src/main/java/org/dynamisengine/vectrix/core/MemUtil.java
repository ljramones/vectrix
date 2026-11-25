/*
 * The MIT License
 *
 * Copyright (c) 2016-2024 Kai Burjack
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
package org.dynamisengine.vectrix.core;

import java.lang.reflect.Field;
import java.nio.*;
//#ifdef __HAS_FFM__
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
//#endif

/**
 * Helper class to do efficient memory operations on all JOML objects, NIO buffers and primitive arrays.
 * This class is used internally throughout JOML, is undocumented and is subject to change.
 * Use with extreme caution!
 * 
 * @author The LWJGL authors
 * @author Kai Burjack
 */
abstract class MemUtil {
    public static final MemUtil INSTANCE = createInstance();

    private static final boolean NATIVE_LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    /**
     * A 64-bit word that, stored in native byte order, puts {@code first} at the lower address and {@code second} at
     * the higher one. The raw-memory backends write element pairs this way; building the word as
     * {@code second << 32 | first} only holds on little-endian CPUs.
     */
    static long pairInts(int first, int second) {
        return NATIVE_LITTLE_ENDIAN
                ? ((long) second << 32) | (first & 0xFFFFFFFFL)
                : ((long) first << 32) | (second & 0xFFFFFFFFL);
    }

    /** The int at the lower address of a native-order word (see {@link #pairInts}). */
    static int firstInt(long word) {
        return NATIVE_LITTLE_ENDIAN ? (int) word : (int) (word >>> 32);
    }

    /** The int at the higher address of a native-order word (see {@link #pairInts}). */
    static int secondInt(long word) {
        return NATIVE_LITTLE_ENDIAN ? (int) (word >>> 32) : (int) word;
    }

    /** Two floats as one native-order word, {@code first} at the lower address. */
    static long pairFloats(float first, float second) {
        return pairInts(Float.floatToRawIntBits(first), Float.floatToRawIntBits(second));
    }
//#ifdef __HAS_UNSAFE__
    /**
     * Whether the raw-address API ({@code getToAddress}/{@code setFromAddress} and their transposed forms) can be
     * used. It runs on FFM by default: a segment spanning the whole address space, which needs native access
     * ({@code --enable-native-access=org.dynamisengine.vectrix}, or {@code ALL-UNNAMED} on the class path; without
     * it the JDK prints a one-time "restricted method" warning). It falls back to {@code sun.misc.Unsafe} with
     * {@code -Djoml.forceUnsafe} or {@code -Djoml.noffm}, or when native access is denied
     * ({@code --illegal-native-access=deny}). When neither is available, those methods throw
     * {@link UnsupportedOperationException}.
     * <p>
     * Evaluated lazily, on the first raw-address call, so programs which never use that API never request native
     * access or touch {@code sun.misc.Unsafe}.
     */
    static boolean addressApi() {
        return AddressAccess.AVAILABLE;
    }

    static final String ADDRESS_API_UNAVAILABLE =
            "Not supported: native access is denied and sun.misc.Unsafe is disabled (-Djoml.nounsafe) or denied";

    /** The raw-address API: FFM over the whole address space, or Unsafe (see {@link #addressApi()}). */
    static final class AddressAccess {
//#ifdef __HAS_FFM__
        /**
         * Spans the whole address space; {@code null} when the Unsafe path is used. Accessed directly: taking an
         * {@code asSlice(address, size)} per call (as the buffer path does) measured 40 % slower here.
         */
        private static final MemorySegment ADDRESS_SPACE = addressSpace();
//#endif
        static final boolean AVAILABLE =
//#ifdef __HAS_FFM__
                ADDRESS_SPACE != null ||
//#endif
                (!Options.NO_UNSAFE && unsafeFunctional());

        private AddressAccess() {
        }
//#ifdef __HAS_FFM__

        private static MemorySegment addressSpace() {
            if (Options.FORCE_UNSAFE || Options.NO_FFM)
                return null;
            try {
                return MemorySegment.NULL.reinterpret(Long.MAX_VALUE);
            } catch (IllegalCallerException | UnsupportedOperationException e) {
                return null;
            }
        }
//#endif

        private static boolean unsafeFunctional() {
            try {
                return MemUtilUnsafe.UNSAFE != null;
            } catch (Throwable e) {
                return false;
            }
        }

        static void put(Matrix4f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(m, address);
        }
        static void put(Matrix4x3f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(m, address);
        }
        static void putTransposed(Matrix4f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.putTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.putTransposed(m, address);
        }
        static void putTransposed(Matrix4x3f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.putTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.putTransposed(m, address);
        }
        static void putTransposed(Matrix3f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.putTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.putTransposed(m, address);
        }
        static void putTransposed(Matrix3x2f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.putTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.putTransposed(m, address);
        }
        static void putTransposed(Matrix2f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.putTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.putTransposed(m, address);
        }
        static void put(Matrix4d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(m, address);
        }
        static void put(Matrix4x3d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(m, address);
        }
        static void putTransposed(Matrix4d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.putTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.putTransposed(m, address);
        }
        static void putTransposed(Matrix4x3d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.putTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.putTransposed(m, address);
        }
        static void putTransposed(Matrix3d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.putTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.putTransposed(m, address);
        }
        static void putTransposed(Matrix3x2d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.putTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.putTransposed(m, address);
        }
        static void putTransposed(Matrix2d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.putTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.putTransposed(m, address);
        }
        static void put(Matrix3f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(m, address);
        }
        static void put(Matrix3d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(m, address);
        }
        static void put(Matrix3x2f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(m, address);
        }
        static void put(Matrix3x2d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(m, address);
        }
        static void put(Matrix2f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(m, address);
        }
        static void put(Matrix2d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(m, address);
        }
        static void put(Vector4d src, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(src, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(src, address);
        }
        static void put(Vector4f src, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(src, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(src, address);
        }
        static void put(Vector4i src, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(src, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(src, address);
        }
        static void put(Vector4L src, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(src, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(src, address);
        }
        static void put(Vector3f src, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(src, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(src, address);
        }
        static void put(Vector3d src, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(src, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(src, address);
        }
        static void put(Vector3i src, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(src, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(src, address);
        }
        static void put(Vector3L src, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(src, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(src, address);
        }
        static void put(Vector2f src, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(src, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(src, address);
        }
        static void put(Vector2d src, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(src, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(src, address);
        }
        static void put(Vector2i src, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(src, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(src, address);
        }
        static void put(Vector2L src, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.put(src, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.put(src, address);
        }
        static void get(Matrix4f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(m, address);
        }
        static void getTransposed(Matrix4f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.getTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.getTransposed(m, address);
        }
        static void getTransposed(Matrix3f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.getTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.getTransposed(m, address);
        }
        static void getTransposed(Matrix4x3f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.getTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.getTransposed(m, address);
        }
        static void getTransposed(Matrix3x2f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.getTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.getTransposed(m, address);
        }
        static void getTransposed(Matrix2f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.getTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.getTransposed(m, address);
        }
        static void getTransposed(Matrix2d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.getTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.getTransposed(m, address);
        }
        static void getTransposed(Matrix4x3d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.getTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.getTransposed(m, address);
        }
        static void getTransposed(Matrix3x2d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.getTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.getTransposed(m, address);
        }
        static void getTransposed(Matrix3d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.getTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.getTransposed(m, address);
        }
        static void getTransposed(Matrix4d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.getTransposed(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.getTransposed(m, address);
        }
        static void get(Matrix4x3f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(m, address);
        }
        static void get(Matrix4d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(m, address);
        }
        static void get(Matrix4x3d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(m, address);
        }
        static void get(Matrix3f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(m, address);
        }
        static void get(Matrix3d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(m, address);
        }
        static void get(Matrix3x2f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(m, address);
        }
        static void get(Matrix3x2d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(m, address);
        }
        static void get(Matrix2f m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(m, address);
        }
        static void get(Matrix2d m, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(m, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(m, address);
        }
        static void get(Vector4d dst, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(dst, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(dst, address);
        }
        static void get(Vector4f dst, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(dst, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(dst, address);
        }
        static void get(Vector4i dst, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(dst, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(dst, address);
        }
        static void get(Vector4L dst, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(dst, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(dst, address);
        }
        static void get(Vector3f dst, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(dst, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(dst, address);
        }
        static void get(Vector3d dst, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(dst, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(dst, address);
        }
        static void get(Vector3i dst, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(dst, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(dst, address);
        }
        static void get(Vector3L dst, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(dst, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(dst, address);
        }
        static void get(Vector2f dst, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(dst, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(dst, address);
        }
        static void get(Vector2d dst, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(dst, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(dst, address);
        }
        static void get(Vector2i dst, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(dst, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(dst, address);
        }
        static void get(Vector2L dst, long address) {
//#ifdef __HAS_FFM__
            if (ADDRESS_SPACE != null) {
                MemUtilFFM.get(dst, ADDRESS_SPACE, address);
                return;
            }
//#endif
            MemUtilUnsafe.get(dst, address);
        }
    }
//#endif
    /**
     * Backend selection, in order:
     * <ol>
     * <li>{@code -Djoml.forceUnsafe=true}: the Unsafe backend (raw addresses, bounds-checked against the limit).</li>
     * <li>{@code -Djoml.forceFfm=true}, or by default: the FFM backend (bounds-checked {@code MemorySegment} views
     * of the buffer). It never touches {@code sun.misc.Unsafe}, so the JDK prints no deprecation warning.</li>
     * <li>With {@code -Djoml.noffm=true}: the Unsafe backend, unless disabled with {@code -Djoml.nounsafe} or not
     * functional (for example under {@code --sun-misc-unsafe-memory-access=deny}).</li>
     * <li>Otherwise plain NIO.</li>
     * </ol>
     * Every backend honours NIO semantics: absolute indices are checked against {@code limit()}, slices are not
     * escaped, and heap, read-only and non-native-order buffers take the NIO path.
     */
    private static MemUtil createInstance() {
        if (Options.FORCE_UNSAFE && Options.FORCE_FFM)
            throw new ConfigurationException("Cannot enable both -Djoml.forceUnsafe and -Djoml.forceFfm", null);
//#ifdef __HAS_UNSAFE__
        if (Options.NO_UNSAFE && Options.FORCE_UNSAFE)
            throw new ConfigurationException("Cannot enable both -Djoml.nounsafe and -Djoml.forceUnsafe", null);
        if (Options.FORCE_UNSAFE) {
            try {
                return new MemUtilUnsafe();
            } catch (Throwable e) {
                throw new ConfigurationException("Unsafe is not supported but its use was forced via -Djoml.forceUnsafe", e);
            }
        }
//#endif
//#ifdef __HAS_FFM__
        if (Options.NO_FFM && Options.FORCE_FFM)
            throw new ConfigurationException("Cannot enable both -Djoml.noffm and -Djoml.forceFfm", null);
        if (!Options.NO_FFM) {
            try {
                return new MemUtilFFM();
            } catch (Throwable e) {
                if (Options.FORCE_FFM)
                    throw new ConfigurationException("FFM is not supported but its use was forced via -Djoml.forceFfm", e);
            }
        }
//#endif
//#ifdef __HAS_UNSAFE__
        if (!Options.NO_UNSAFE) {
            try {
                return new MemUtilUnsafe();
            } catch (Throwable e) {
                // fall through to NIO
            }
        }
//#endif
        return new MemUtilNIO();
    }

    public abstract void put(Matrix4f m, int offset, FloatBuffer dest);
    public abstract void put(Matrix4f m, int offset, ByteBuffer dest);
    public abstract void put(Matrix4x3f m, int offset, FloatBuffer dest);
    public abstract void put(Matrix4x3f m, int offset, ByteBuffer dest);
    public abstract void put4x4(Matrix4x3f m, int offset, FloatBuffer dest);
    public abstract void put4x4(Matrix4x3f m, int offset, ByteBuffer dest);
    public abstract void put4x4(Matrix4x3d m, int offset, DoubleBuffer dest);
    public abstract void put4x4(Matrix4x3d m, int offset, ByteBuffer dest);
    public abstract void put4x4(Matrix3x2f m, int offset, FloatBuffer dest);
    public abstract void put4x4(Matrix3x2f m, int offset, ByteBuffer dest);
    public abstract void put4x4(Matrix3x2d m, int offset, DoubleBuffer dest);
    public abstract void put4x4(Matrix3x2d m, int offset, ByteBuffer dest);
    public abstract void put3x3(Matrix3x2f m, int offset, FloatBuffer dest);
    public abstract void put3x3(Matrix3x2f m, int offset, ByteBuffer dest);
    public abstract void put3x3(Matrix3x2d m, int offset, DoubleBuffer dest);
    public abstract void put3x3(Matrix3x2d m, int offset, ByteBuffer dest);
    public abstract void put4x3(Matrix4f m, int offset, FloatBuffer dest);
    public abstract void put4x3(Matrix4f m, int offset, ByteBuffer dest);
    public abstract void put3x4(Matrix4f m, int offset, FloatBuffer dest);
    public abstract void put3x4(Matrix4f m, int offset, ByteBuffer dest);
    public abstract void put3x4(Matrix4x3f m, int offset, FloatBuffer dest);
    public abstract void put3x4(Matrix4x3f m, int offset, ByteBuffer dest);
    public abstract void put3x4(Matrix3f m, int offset, FloatBuffer dest);
    public abstract void put3x4(Matrix3f m, int offset, ByteBuffer dest);
    public abstract void putTransposed(Matrix4f m, int offset, FloatBuffer dest);
    public abstract void putTransposed(Matrix4f m, int offset, ByteBuffer dest);
    public abstract void put4x3Transposed(Matrix4f m, int offset, FloatBuffer dest);
    public abstract void put4x3Transposed(Matrix4f m, int offset, ByteBuffer dest);
    public abstract void putTransposed(Matrix4x3f m, int offset, FloatBuffer dest);
    public abstract void putTransposed(Matrix4x3f m, int offset, ByteBuffer dest);
    public abstract void putTransposed(Matrix3x2f m, int offset, FloatBuffer dest);
    public abstract void putTransposed(Matrix3f m, int offset, FloatBuffer dest);
    public abstract void putTransposed(Matrix3f m, int offset, ByteBuffer dest);
    public abstract void putTransposed(Matrix2f m, int offset, FloatBuffer dest);
    public abstract void putTransposed(Matrix2f m, int offset, ByteBuffer dest);
    public abstract void put(Matrix4d m, int offset, DoubleBuffer dest);
    public abstract void put(Matrix4d m, int offset, ByteBuffer dest);
    public abstract void put(Matrix4x3d m, int offset, DoubleBuffer dest);
    public abstract void put(Matrix4x3d m, int offset, ByteBuffer dest);
    public abstract void putf(Matrix4d m, int offset, FloatBuffer dest);
    public abstract void putf(Matrix4d m, int offset, ByteBuffer dest);
    public abstract void putf(Matrix4x3d m, int offset, FloatBuffer dest);
    public abstract void putf(Matrix4x3d m, int offset, ByteBuffer dest);
    public abstract void put4x3Transposed(Matrix4d m, int offset, DoubleBuffer dest);
    public abstract void put4x3Transposed(Matrix4d m, int offset, ByteBuffer dest);
    public abstract void putTransposed(Matrix4d m, int offset, DoubleBuffer dest);
    public abstract void putTransposed(Matrix4d m, int offset, ByteBuffer dest);
    public abstract void putTransposed(Matrix4x3d m, int offset, DoubleBuffer dest);
    public abstract void putTransposed(Matrix4x3d m, int offset, ByteBuffer dest);
    public abstract void putTransposed(Matrix3d m, int offset, DoubleBuffer dest);
    public abstract void putTransposed(Matrix3d m, int offset, ByteBuffer dest);
    public abstract void putTransposed(Matrix3x2d m, int offset, DoubleBuffer dest);
    public abstract void putTransposed(Matrix3x2d m, int offset, ByteBuffer dest);
    public abstract void putTransposed(Matrix2d m, int offset, DoubleBuffer dest);
    public abstract void putTransposed(Matrix2d m, int offset, ByteBuffer dest);
    public abstract void putfTransposed(Matrix4d m, int offset, FloatBuffer dest);
    public abstract void putfTransposed(Matrix4d m, int offset, ByteBuffer dest);
    public abstract void putfTransposed(Matrix4x3d m, int offset, FloatBuffer dest);
    public abstract void putfTransposed(Matrix4x3d m, int offset, ByteBuffer dest);
    public abstract void putfTransposed(Matrix3d m, int offset, FloatBuffer dest);
    public abstract void putfTransposed(Matrix3d m, int offset, ByteBuffer dest);
    public abstract void putfTransposed(Matrix3x2d m, int offset, FloatBuffer dest);
    public abstract void putfTransposed(Matrix3x2d m, int offset, ByteBuffer dest);
    public abstract void putfTransposed(Matrix2d m, int offset, FloatBuffer dest);
    public abstract void putfTransposed(Matrix2d m, int offset, ByteBuffer dest);
    public abstract void put(Matrix3f m, int offset, FloatBuffer dest);
    public abstract void put(Matrix3f m, int offset, ByteBuffer dest);
    public abstract void put(Matrix3d m, int offset, DoubleBuffer dest);
    public abstract void put(Matrix3d m, int offset, ByteBuffer dest);
    public abstract void putf(Matrix3d m, int offset, FloatBuffer dest);
    public abstract void putf(Matrix3d m, int offset, ByteBuffer dest);
    public abstract void put(Matrix3x2f m, int offset, FloatBuffer dest);
    public abstract void put(Matrix3x2f m, int offset, ByteBuffer dest);
    public abstract void put(Matrix3x2d m, int offset, DoubleBuffer dest);
    public abstract void put(Matrix3x2d m, int offset, ByteBuffer dest);
    public abstract void put(Matrix2f m, int offset, FloatBuffer dest);
    public abstract void put(Matrix2f m, int offset, ByteBuffer dest);
    public abstract void put(Matrix2d m, int offset, DoubleBuffer dest);
    public abstract void put(Matrix2d m, int offset, ByteBuffer dest);
    public abstract void putf(Matrix2d m, int offset, FloatBuffer dest);
    public abstract void putf(Matrix2d m, int offset, ByteBuffer dest);
    public abstract void put(Vector4d src, int offset, DoubleBuffer dest);
    public abstract void put(Vector4d src, int offset, FloatBuffer dest);
    public abstract void put(Vector4d src, int offset, ByteBuffer dest);
    public abstract void putf(Vector4d src, int offset, ByteBuffer dest);
    public abstract void put(Vector4f src, int offset, FloatBuffer dest);
    public abstract void put(Vector4f src, int offset, ByteBuffer dest);
    public abstract void put(Vector4i src, int offset, IntBuffer dest);
    public abstract void put(Vector4i src, int offset, ByteBuffer dest);
    public abstract void put(Vector4L src, int offset, LongBuffer dest);
    public abstract void put(Vector4L src, int offset, ByteBuffer dest);
    public abstract void put(Vector3f src, int offset, FloatBuffer dest);
    public abstract void put(Vector3f src, int offset, ByteBuffer dest);
    public abstract void put(Vector3d src, int offset, DoubleBuffer dest);
    public abstract void put(Vector3d src, int offset, FloatBuffer dest);
    public abstract void put(Vector3d src, int offset, ByteBuffer dest);
    public abstract void putf(Vector3d src, int offset, ByteBuffer dest);
    public abstract void put(Vector3i src, int offset, IntBuffer dest);
    public abstract void put(Vector3i src, int offset, ByteBuffer dest);
    public abstract void put(Vector3L src, int offset, LongBuffer dest);
    public abstract void put(Vector3L src, int offset, ByteBuffer dest);
    public abstract void put(Vector2f src, int offset, FloatBuffer dest);
    public abstract void put(Vector2f src, int offset, ByteBuffer dest);
    public abstract void put(Vector2d src, int offset, DoubleBuffer dest);
    public abstract void put(Vector2d src, int offset, ByteBuffer dest);
    public abstract void put(Vector2i src, int offset, IntBuffer dest);
    public abstract void put(Vector2i src, int offset, ByteBuffer dest);
    public abstract void put(Vector2L src, int offset, LongBuffer dest);
    public abstract void put(Vector2L src, int offset, ByteBuffer dest);
    public abstract void get(Matrix4f m, int offset, FloatBuffer src);
    public abstract void get(Matrix4f m, int offset, ByteBuffer src);
    public abstract void getTransposed(Matrix4f m, int offset, FloatBuffer src);
    public abstract void getTransposed(Matrix4f m, int offset, ByteBuffer src);
    public abstract void get(Matrix4x3f m, int offset, FloatBuffer src);
    public abstract void get(Matrix4x3f m, int offset, ByteBuffer src);
    public abstract void get(Matrix4d m, int offset, DoubleBuffer src);
    public abstract void get(Matrix4d m, int offset, ByteBuffer src);
    public abstract void get(Matrix4x3d m, int offset, DoubleBuffer src);
    public abstract void get(Matrix4x3d m, int offset, ByteBuffer src);
    public abstract void getf(Matrix4d m, int offset, FloatBuffer src);
    public abstract void getf(Matrix4d m, int offset, ByteBuffer src);
    public abstract void getf(Matrix4x3d m, int offset, FloatBuffer src);
    public abstract void getf(Matrix4x3d m, int offset, ByteBuffer src);
    public abstract void get(Matrix3f m, int offset, FloatBuffer src);
    public abstract void get(Matrix3f m, int offset, ByteBuffer src);
    public abstract void get(Matrix3d m, int offset, DoubleBuffer src);
    public abstract void get(Matrix3d m, int offset, ByteBuffer src);
    public abstract void get(Matrix3x2f m, int offset, FloatBuffer src);
    public abstract void get(Matrix3x2f m, int offset, ByteBuffer src);
    public abstract void get(Matrix3x2d m, int offset, DoubleBuffer src);
    public abstract void get(Matrix3x2d m, int offset, ByteBuffer src);
    public abstract void getf(Matrix3d m, int offset, FloatBuffer src);
    public abstract void getf(Matrix3d m, int offset, ByteBuffer src);
    public abstract void get(Matrix2f m, int offset, FloatBuffer src);
    public abstract void get(Matrix2f m, int offset, ByteBuffer src);
    public abstract void get(Matrix2d m, int offset, DoubleBuffer src);
    public abstract void get(Matrix2d m, int offset, ByteBuffer src);
    public abstract void getf(Matrix2d m, int offset, FloatBuffer src);
    public abstract void getf(Matrix2d m, int offset, ByteBuffer src);
    public abstract void get(Vector4d dst, int offset, DoubleBuffer src);
    public abstract void get(Vector4d dst, int offset, ByteBuffer src);
    public abstract void get(Vector4f dst, int offset, FloatBuffer src);
    public abstract void get(Vector4f dst, int offset, ByteBuffer src);
    public abstract void get(Vector4i dst, int offset, IntBuffer src);
    public abstract void get(Vector4i dst, int offset, ByteBuffer src);
    public abstract void get(Vector4L dst, int offset, LongBuffer src);
    public abstract void get(Vector4L dst, int offset, ByteBuffer src);
    public abstract void get(Vector3f dst, int offset, FloatBuffer src);
    public abstract void get(Vector3f dst, int offset, ByteBuffer src);
    public abstract void get(Vector3d dst, int offset, DoubleBuffer src);
    public abstract void get(Vector3d dst, int offset, ByteBuffer src);
    public abstract void get(Vector3i dst, int offset, IntBuffer src);
    public abstract void get(Vector3i dst, int offset, ByteBuffer src);
    public abstract void get(Vector3L dst, int offset, LongBuffer src);
    public abstract void get(Vector3L dst, int offset, ByteBuffer src);
    public abstract void get(Vector2f dst, int offset, FloatBuffer src);
    public abstract void get(Vector2f dst, int offset, ByteBuffer src);
    public abstract void get(Vector2d dst, int offset, DoubleBuffer src);
    public abstract void get(Vector2d dst, int offset, ByteBuffer src);
    public abstract void get(Vector2i dst, int offset, IntBuffer src);
    public abstract void get(Vector2i dst, int offset, ByteBuffer src);
    public abstract void get(Vector2L dst, int offset, LongBuffer src);
    public abstract void get(Vector2L dst, int offset, ByteBuffer src);
    public abstract void putMatrix3f(Quaternionf q, int position, ByteBuffer dest);
    public abstract void putMatrix3f(Quaternionf q, int position, FloatBuffer dest);
    public abstract void putMatrix4f(Quaternionf q, int position, ByteBuffer dest);
    public abstract void putMatrix4f(Quaternionf q, int position, FloatBuffer dest);
    public abstract void putMatrix4x3f(Quaternionf q, int position, ByteBuffer dest);
    public abstract void putMatrix4x3f(Quaternionf q, int position, FloatBuffer dest);

    public abstract float get(Matrix4f m, int column, int row);
    public abstract Matrix4f set(Matrix4f m, int column, int row, float v);
    public abstract double get(Matrix4d m, int column, int row);
    public abstract Matrix4d set(Matrix4d m, int column, int row, double v);
    public abstract float get(Matrix3f m, int column, int row);
    public abstract Matrix3f set(Matrix3f m, int column, int row, float v);
    public abstract double get(Matrix3d m, int column, int row);
    public abstract Matrix3d set(Matrix3d m, int column, int row, double v);
    public abstract Vector4f getColumn(Matrix4f m, int column, Vector4f dest);
    public abstract Matrix4f setColumn(Vector4f v, int column, Matrix4f dest);
    public abstract Matrix4f setColumn(Vector4fc v, int column, Matrix4f dest);
    public abstract void copy(Matrix4fc src, Matrix4f dest);
    public abstract void copy(Matrix4x3fc src, Matrix4x3f dest);
    public abstract void copy(Matrix4fc src, Matrix4x3f dest);
    public abstract void copy(Matrix4x3fc src, Matrix4f dest);
    public abstract void copy(Matrix3fc src, Matrix3f dest);
    public abstract void copy(Matrix3fc src, Matrix4f dest);
    public abstract void copy(Matrix4fc src, Matrix3f dest);
    public abstract void copy(Matrix3fc src, Matrix4x3f dest);
    public abstract void copy(Matrix3x2fc src, Matrix3x2f dest);
    public abstract void copy(Matrix3x2dc src, Matrix3x2d dest);
    public abstract void copy(Matrix2fc src, Matrix2f dest);
    public abstract void copy(Matrix2dc src, Matrix2d dest);
    public abstract void copy(Matrix2fc src, Matrix3f dest);
    public abstract void copy(Matrix3fc src, Matrix2f dest);
    public abstract void copy(Matrix2fc src, Matrix3x2f dest);
    public abstract void copy(Matrix3x2fc src, Matrix2f dest);
    public abstract void copy(Matrix2dc src, Matrix3d dest);
    public abstract void copy(Matrix3dc src, Matrix2d dest);
    public abstract void copy(Matrix2dc src, Matrix3x2d dest);
    public abstract void copy(Matrix3x2dc src, Matrix2d dest);
    public abstract void copy3x3(Matrix4fc src, Matrix4f dest);
    public abstract void copy3x3(Matrix4x3fc src, Matrix4x3f dest);
    public abstract void copy3x3(Matrix3fc src, Matrix4x3f dest);
    public abstract void copy3x3(Matrix3fc src, Matrix4f dest);
    public abstract void copy4x3(Matrix4fc src, Matrix4f dest);
    public abstract void copy4x3(Matrix4x3fc src, Matrix4f dest);
    public abstract void copy(float[] arr, int off, Matrix4f dest);
    public abstract void copyTransposed(float[] arr, int off, Matrix4f dest);
    public abstract void copy(float[] arr, int off, Matrix3f dest);
    public abstract void copy(float[] arr, int off, Matrix4x3f dest);
    public abstract void copy(float[] arr, int off, Matrix3x2f dest);
    public abstract void copy(double[] arr, int off, Matrix3x2d dest);
    public abstract void copy(float[] arr, int off, Matrix3x2d dest);
    public abstract void copy(float[] arr, int off, Matrix2f dest);
    public abstract void copy(double[] arr, int off, Matrix2d dest);
    public abstract void copy(Matrix4fc src, float[] dest, int off);
    public abstract void copy(Matrix3fc src, float[] dest, int off);
    public abstract void copy(Matrix4x3fc src, float[] dest, int off);
    public abstract void copy(Matrix3x2fc src, float[] dest, int off);
    public abstract void copy(Matrix3x2dc src, double[] dest, int off);
    public abstract void copy(Matrix2fc src, float[] dest, int off);
    public abstract void copy(Matrix2dc src, double[] dest, int off);
    public abstract void copy4x4(Matrix4x3fc src, float[] dest, int off);
    public abstract void copy4x4(Matrix4x3dc src, float[] dest, int off);
    public abstract void copy4x4(Matrix4x3dc src, double[] dest, int off);
    public abstract void copy4x4(Matrix3x2fc src, float[] dest, int off);
    public abstract void copy4x4(Matrix3x2dc src, double[] dest, int off);
    public abstract void copy3x3(Matrix3x2fc src, float[] dest, int off);
    public abstract void copy3x3(Matrix3x2dc src, double[] dest, int off);
    public abstract void identity(Matrix4f dest);
    public abstract void identity(Matrix4x3f dest);
    public abstract void identity(Matrix3f dest);
    public abstract void identity(Matrix3x2f dest);
    public abstract void identity(Matrix3x2d dest);
    public abstract void identity(Matrix2f dest);
    public abstract void swap(Matrix4f m1, Matrix4f m2);
    public abstract void swap(Matrix4x3f m1, Matrix4x3f m2);
    public abstract void swap(Matrix3f m1, Matrix3f m2);
    public abstract void swap(Matrix2f m1, Matrix2f m2);
    public abstract void swap(Matrix2d m1, Matrix2d m2);
    public abstract void zero(Matrix4f dest);
    public abstract void zero(Matrix4x3f dest);
    public abstract void zero(Matrix3f dest);
    public abstract void zero(Matrix3x2f dest);
    public abstract void zero(Matrix3x2d dest);
    public abstract void zero(Matrix2f dest);
    public abstract void zero(Matrix2d dest);

    public static class MemUtilNIO extends MemUtil {
        public void put0(Matrix4f m, FloatBuffer dest) {
            dest.put(0,  m.m00())
            .put(1,  m.m01())
            .put(2,  m.m02())
            .put(3,  m.m03())
            .put(4,  m.m10())
            .put(5,  m.m11())
            .put(6,  m.m12())
            .put(7,  m.m13())
            .put(8,  m.m20())
            .put(9,  m.m21())
            .put(10, m.m22())
            .put(11, m.m23())
            .put(12, m.m30())
            .put(13, m.m31())
            .put(14, m.m32())
            .put(15, m.m33());
        }
        public void putN(Matrix4f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m01())
            .put(offset+2,  m.m02())
            .put(offset+3,  m.m03())
            .put(offset+4,  m.m10())
            .put(offset+5,  m.m11())
            .put(offset+6,  m.m12())
            .put(offset+7,  m.m13())
            .put(offset+8,  m.m20())
            .put(offset+9,  m.m21())
            .put(offset+10, m.m22())
            .put(offset+11, m.m23())
            .put(offset+12, m.m30())
            .put(offset+13, m.m31())
            .put(offset+14, m.m32())
            .put(offset+15, m.m33());
        }
        public void put(Matrix4f m, int offset, FloatBuffer dest) {
            if (offset == 0)
                put0(m, dest);
            else
                putN(m, offset, dest);
        }

        public void put0(Matrix4f m, ByteBuffer dest) {
            dest.putFloat(0,  m.m00())
            .putFloat(4,  m.m01())
            .putFloat(8,  m.m02())
            .putFloat(12, m.m03())
            .putFloat(16, m.m10())
            .putFloat(20, m.m11())
            .putFloat(24, m.m12())
            .putFloat(28, m.m13())
            .putFloat(32, m.m20())
            .putFloat(36, m.m21())
            .putFloat(40, m.m22())
            .putFloat(44, m.m23())
            .putFloat(48, m.m30())
            .putFloat(52, m.m31())
            .putFloat(56, m.m32())
            .putFloat(60, m.m33());
        }
        private void putN(Matrix4f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m01())
            .putFloat(offset+8,  m.m02())
            .putFloat(offset+12, m.m03())
            .putFloat(offset+16, m.m10())
            .putFloat(offset+20, m.m11())
            .putFloat(offset+24, m.m12())
            .putFloat(offset+28, m.m13())
            .putFloat(offset+32, m.m20())
            .putFloat(offset+36, m.m21())
            .putFloat(offset+40, m.m22())
            .putFloat(offset+44, m.m23())
            .putFloat(offset+48, m.m30())
            .putFloat(offset+52, m.m31())
            .putFloat(offset+56, m.m32())
            .putFloat(offset+60, m.m33());
        }
        public void put(Matrix4f m, int offset, ByteBuffer dest) {
            if (offset == 0)
                put0(m, dest);
            else
                putN(m, offset, dest);
        }

        public void put4x3_0(Matrix4f m, FloatBuffer dest) {
            dest.put(0,  m.m00())
            .put(1,  m.m01())
            .put(2,  m.m02())
            .put(3,  m.m10())
            .put(4,  m.m11())
            .put(5,  m.m12())
            .put(6,  m.m20())
            .put(7,  m.m21())
            .put(8,  m.m22())
            .put(9,  m.m30())
            .put(10, m.m31())
            .put(11, m.m32());
        }
        public void put4x3_N(Matrix4f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m01())
            .put(offset+2,  m.m02())
            .put(offset+3,  m.m10())
            .put(offset+4,  m.m11())
            .put(offset+5,  m.m12())
            .put(offset+6,  m.m20())
            .put(offset+7,  m.m21())
            .put(offset+8,  m.m22())
            .put(offset+9,  m.m30())
            .put(offset+10, m.m31())
            .put(offset+11, m.m32());
        }
        public void put4x3(Matrix4f m, int offset, FloatBuffer dest) {
            if (offset == 0)
                put4x3_0(m, dest);
            else
                put4x3_N(m, offset, dest);
        }

        public void put4x3_0(Matrix4f m, ByteBuffer dest) {
            dest.putFloat(0,  m.m00())
            .putFloat(4,  m.m01())
            .putFloat(8,  m.m02())
            .putFloat(12, m.m10())
            .putFloat(16, m.m11())
            .putFloat(20, m.m12())
            .putFloat(24, m.m20())
            .putFloat(28, m.m21())
            .putFloat(32, m.m22())
            .putFloat(36, m.m30())
            .putFloat(40, m.m31())
            .putFloat(44, m.m32());
        }
        private void put4x3_N(Matrix4f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m01())
            .putFloat(offset+8,  m.m02())
            .putFloat(offset+12, m.m10())
            .putFloat(offset+16, m.m11())
            .putFloat(offset+20, m.m12())
            .putFloat(offset+24, m.m20())
            .putFloat(offset+28, m.m21())
            .putFloat(offset+32, m.m22())
            .putFloat(offset+36, m.m30())
            .putFloat(offset+40, m.m31())
            .putFloat(offset+44, m.m32());
        }
        public void put4x3(Matrix4f m, int offset, ByteBuffer dest) {
            if (offset == 0)
                put4x3_0(m, dest);
            else
                put4x3_N(m, offset, dest);
        }

        public void put3x4_0(Matrix4f m, ByteBuffer dest) {
            dest.putFloat(0,  m.m00())
            .putFloat(4,  m.m01())
            .putFloat(8,  m.m02())
            .putFloat(12, m.m03())
            .putFloat(16, m.m10())
            .putFloat(20, m.m11())
            .putFloat(24, m.m12())
            .putFloat(28, m.m13())
            .putFloat(32, m.m20())
            .putFloat(36, m.m21())
            .putFloat(40, m.m22())
            .putFloat(44, m.m23());
        }
        private void put3x4_N(Matrix4f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m01())
            .putFloat(offset+8,  m.m02())
            .putFloat(offset+12, m.m03())
            .putFloat(offset+16, m.m10())
            .putFloat(offset+20, m.m11())
            .putFloat(offset+24, m.m12())
            .putFloat(offset+28, m.m13())
            .putFloat(offset+32, m.m20())
            .putFloat(offset+36, m.m21())
            .putFloat(offset+40, m.m22())
            .putFloat(offset+44, m.m23());
        }
        public void put3x4(Matrix4f m, int offset, ByteBuffer dest) {
            if (offset == 0)
                put3x4_0(m, dest);
            else
                put3x4_N(m, offset, dest);
        }

        public void put3x4_0(Matrix4f m, FloatBuffer dest) {
            dest.put(0,  m.m00())
            .put(1,  m.m01())
            .put(2,  m.m02())
            .put(3,  m.m03())
            .put(4,  m.m10())
            .put(5,  m.m11())
            .put(6,  m.m12())
            .put(7,  m.m13())
            .put(8,  m.m20())
            .put(9,  m.m21())
            .put(10, m.m22())
            .put(11, m.m23());
        }
        public void put3x4_N(Matrix4f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m01())
            .put(offset+2,  m.m02())
            .put(offset+3,  m.m03())
            .put(offset+4,  m.m10())
            .put(offset+5,  m.m11())
            .put(offset+6,  m.m12())
            .put(offset+7,  m.m13())
            .put(offset+8,  m.m20())
            .put(offset+9,  m.m21())
            .put(offset+10, m.m22())
            .put(offset+11, m.m23());
        }
        public void put3x4(Matrix4f m, int offset, FloatBuffer dest) {
            if (offset == 0)
                put3x4_0(m, dest);
            else
                put3x4_N(m, offset, dest);
        }

        public void put3x4_0(Matrix4x3f m, ByteBuffer dest) {
            dest.putFloat(0,  m.m00())
            .putFloat(4,  m.m01())
            .putFloat(8,  m.m02())
            .putFloat(12, 0.0f)
            .putFloat(16, m.m10())
            .putFloat(20, m.m11())
            .putFloat(24, m.m12())
            .putFloat(28, 0.0f)
            .putFloat(32, m.m20())
            .putFloat(36, m.m21())
            .putFloat(40, m.m22())
            .putFloat(44, 0.0f);
        }
        private void put3x4_N(Matrix4x3f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m01())
            .putFloat(offset+8,  m.m02())
            .putFloat(offset+12, 0.0f)
            .putFloat(offset+16, m.m10())
            .putFloat(offset+20, m.m11())
            .putFloat(offset+24, m.m12())
            .putFloat(offset+28, 0.0f)
            .putFloat(offset+32, m.m20())
            .putFloat(offset+36, m.m21())
            .putFloat(offset+40, m.m22())
            .putFloat(offset+44, 0.0f);
        }
        public void put3x4(Matrix4x3f m, int offset, ByteBuffer dest) {
            if (offset == 0)
                put3x4_0(m, dest);
            else
                put3x4_N(m, offset, dest);
        }

        public void put3x4_0(Matrix4x3f m, FloatBuffer dest) {
            dest.put(0,  m.m00())
            .put(1,  m.m01())
            .put(2,  m.m02())
            .put(3,  0.0f)
            .put(4,  m.m10())
            .put(5,  m.m11())
            .put(6,  m.m12())
            .put(7,  0.0f)
            .put(8,  m.m20())
            .put(9,  m.m21())
            .put(10, m.m22())
            .put(11, 0.0f);
        }
        public void put3x4_N(Matrix4x3f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m01())
            .put(offset+2,  m.m02())
            .put(offset+3,  0.0f)
            .put(offset+4,  m.m10())
            .put(offset+5,  m.m11())
            .put(offset+6,  m.m12())
            .put(offset+7,  0.0f)
            .put(offset+8,  m.m20())
            .put(offset+9,  m.m21())
            .put(offset+10, m.m22())
            .put(offset+11, 0.0f);
        }
        public void put3x4(Matrix4x3f m, int offset, FloatBuffer dest) {
            if (offset == 0)
                put3x4_0(m, dest);
            else
                put3x4_N(m, offset, dest);
        }

        public void put0(Matrix4x3f m, FloatBuffer dest) {
            dest.put(0,  m.m00())
            .put(1,  m.m01())
            .put(2,  m.m02())
            .put(3,  m.m10())
            .put(4,  m.m11())
            .put(5,  m.m12())
            .put(6,  m.m20())
            .put(7,  m.m21())
            .put(8,  m.m22())
            .put(9,  m.m30())
            .put(10, m.m31())
            .put(11, m.m32());
        }
        public void putN(Matrix4x3f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m01())
            .put(offset+2,  m.m02())
            .put(offset+3,  m.m10())
            .put(offset+4,  m.m11())
            .put(offset+5,  m.m12())
            .put(offset+6,  m.m20())
            .put(offset+7,  m.m21())
            .put(offset+8,  m.m22())
            .put(offset+9,  m.m30())
            .put(offset+10, m.m31())
            .put(offset+11, m.m32());
        }
        public void put(Matrix4x3f m, int offset, FloatBuffer dest) {
            if (offset == 0)
                put0(m, dest);
            else
                putN(m, offset, dest);
        }

        public void put0(Matrix4x3f m, ByteBuffer dest) {
            dest.putFloat(0,  m.m00())
            .putFloat(4,  m.m01())
            .putFloat(8,  m.m02())
            .putFloat(12, m.m10())
            .putFloat(16, m.m11())
            .putFloat(20, m.m12())
            .putFloat(24, m.m20())
            .putFloat(28, m.m21())
            .putFloat(32, m.m22())
            .putFloat(36, m.m30())
            .putFloat(40, m.m31())
            .putFloat(44, m.m32());
        }
        public void putN(Matrix4x3f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m01())
            .putFloat(offset+8,  m.m02())
            .putFloat(offset+12, m.m10())
            .putFloat(offset+16, m.m11())
            .putFloat(offset+20, m.m12())
            .putFloat(offset+24, m.m20())
            .putFloat(offset+28, m.m21())
            .putFloat(offset+32, m.m22())
            .putFloat(offset+36, m.m30())
            .putFloat(offset+40, m.m31())
            .putFloat(offset+44, m.m32());
        }
        public void put(Matrix4x3f m, int offset, ByteBuffer dest) {
            if (offset == 0)
                put0(m, dest);
            else
                putN(m, offset, dest);
        }

        public void put4x4(Matrix4x3f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m01())
            .put(offset+2,  m.m02())
            .put(offset+3,  0.0f)
            .put(offset+4,  m.m10())
            .put(offset+5,  m.m11())
            .put(offset+6,  m.m12())
            .put(offset+7,  0.0f)
            .put(offset+8,  m.m20())
            .put(offset+9,  m.m21())
            .put(offset+10, m.m22())
            .put(offset+11, 0.0f)
            .put(offset+12, m.m30())
            .put(offset+13, m.m31())
            .put(offset+14, m.m32())
            .put(offset+15, 1.0f);
        }

        public void put4x4(Matrix4x3f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m01())
            .putFloat(offset+8,  m.m02())
            .putFloat(offset+12, 0.0f)
            .putFloat(offset+16, m.m10())
            .putFloat(offset+20, m.m11())
            .putFloat(offset+24, m.m12())
            .putFloat(offset+28, 0.0f)
            .putFloat(offset+32, m.m20())
            .putFloat(offset+36, m.m21())
            .putFloat(offset+40, m.m22())
            .putFloat(offset+44, 0.0f)
            .putFloat(offset+48, m.m30())
            .putFloat(offset+52, m.m31())
            .putFloat(offset+56, m.m32())
            .putFloat(offset+60, 1.0f);
        }

        public void put4x4(Matrix4x3d m, int offset, DoubleBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m01())
            .put(offset+2,  m.m02())
            .put(offset+3,  0.0)
            .put(offset+4,  m.m10())
            .put(offset+5,  m.m11())
            .put(offset+6,  m.m12())
            .put(offset+7,  0.0)
            .put(offset+8,  m.m20())
            .put(offset+9,  m.m21())
            .put(offset+10, m.m22())
            .put(offset+11, 0.0)
            .put(offset+12, m.m30())
            .put(offset+13, m.m31())
            .put(offset+14, m.m32())
            .put(offset+15, 1.0);
        }

        public void put4x4(Matrix4x3d m, int offset, ByteBuffer dest) {
            dest.putDouble(offset,     m.m00())
            .putDouble(offset+8,   m.m01())
            .putDouble(offset+16,   m.m02())
            .putDouble(offset+24,  0.0)
            .putDouble(offset+32,  m.m10())
            .putDouble(offset+40,  m.m11())
            .putDouble(offset+48,  m.m12())
            .putDouble(offset+56,  0.0)
            .putDouble(offset+64,  m.m20())
            .putDouble(offset+72,  m.m21())
            .putDouble(offset+80,  m.m22())
            .putDouble(offset+88,  0.0)
            .putDouble(offset+96,  m.m30())
            .putDouble(offset+104, m.m31())
            .putDouble(offset+112, m.m32())
            .putDouble(offset+120, 1.0);
        }

        public void put4x4(Matrix3x2f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m01())
            .put(offset+2,  0.0f)
            .put(offset+3,  0.0f)
            .put(offset+4,  m.m10())
            .put(offset+5,  m.m11())
            .put(offset+6,  0.0f)
            .put(offset+7,  0.0f)
            .put(offset+8,  0.0f)
            .put(offset+9,  0.0f)
            .put(offset+10, 1.0f)
            .put(offset+11, 0.0f)
            .put(offset+12, m.m20())
            .put(offset+13, m.m21())
            .put(offset+14, 0.0f)
            .put(offset+15, 1.0f);
        }

        public void put4x4(Matrix3x2f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m01())
            .putFloat(offset+8,  0.0f)
            .putFloat(offset+12, 0.0f)
            .putFloat(offset+16, m.m10())
            .putFloat(offset+20, m.m11())
            .putFloat(offset+24, 0.0f)
            .putFloat(offset+28, 0.0f)
            .putFloat(offset+32, 0.0f)
            .putFloat(offset+36, 0.0f)
            .putFloat(offset+40, 1.0f)
            .putFloat(offset+44, 0.0f)
            .putFloat(offset+48, m.m20())
            .putFloat(offset+52, m.m21())
            .putFloat(offset+56, 0.0f)
            .putFloat(offset+60, 1.0f);
        }

        public void put4x4(Matrix3x2d m, int offset, DoubleBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m01())
            .put(offset+2,  0.0)
            .put(offset+3,  0.0)
            .put(offset+4,  m.m10())
            .put(offset+5,  m.m11())
            .put(offset+6,  0.0)
            .put(offset+7,  0.0)
            .put(offset+8,  0.0)
            .put(offset+9,  0.0)
            .put(offset+10, 1.0)
            .put(offset+11, 0.0)
            .put(offset+12, m.m20())
            .put(offset+13, m.m21())
            .put(offset+14, 0.0)
            .put(offset+15, 1.0);
        }

        public void put4x4(Matrix3x2d m, int offset, ByteBuffer dest) {
            dest.putDouble(offset,     m.m00())
            .putDouble(offset+8,   m.m01())
            .putDouble(offset+16,  0.0)
            .putDouble(offset+24,  0.0)
            .putDouble(offset+32,  m.m10())
            .putDouble(offset+40,  m.m11())
            .putDouble(offset+48,  0.0)
            .putDouble(offset+56,  0.0)
            .putDouble(offset+64,  0.0)
            .putDouble(offset+72,  0.0)
            .putDouble(offset+80,  1.0)
            .putDouble(offset+88,  0.0)
            .putDouble(offset+96,  m.m20())
            .putDouble(offset+104, m.m21())
            .putDouble(offset+112, 0.0)
            .putDouble(offset+120, 1.0);
        }

        public void put3x3(Matrix3x2f m, int offset, FloatBuffer dest) {
            dest.put(offset,   m.m00())
            .put(offset+1, m.m01())
            .put(offset+2, 0.0f)
            .put(offset+3, m.m10())
            .put(offset+4, m.m11())
            .put(offset+5, 0.0f)
            .put(offset+6, m.m20())
            .put(offset+7, m.m21())
            .put(offset+8, 1.0f);
        }

        public void put3x3(Matrix3x2f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m01())
            .putFloat(offset+8,  0.0f)
            .putFloat(offset+12, m.m10())
            .putFloat(offset+16, m.m11())
            .putFloat(offset+20, 0.0f)
            .putFloat(offset+24, m.m20())
            .putFloat(offset+28, m.m21())
            .putFloat(offset+32, 1.0f);
        }

        public void put3x3(Matrix3x2d m, int offset, DoubleBuffer dest) {
            dest.put(offset,   m.m00())
            .put(offset+1, m.m01())
            .put(offset+2, 0.0)
            .put(offset+3, m.m10())
            .put(offset+4, m.m11())
            .put(offset+5, 0.0)
            .put(offset+6, m.m20())
            .put(offset+7, m.m21())
            .put(offset+8, 1.0);
        }

        public void put3x3(Matrix3x2d m, int offset, ByteBuffer dest) {
            dest.putDouble(offset,    m.m00())
            .putDouble(offset+8,  m.m01())
            .putDouble(offset+16, 0.0)
            .putDouble(offset+24, m.m10())
            .putDouble(offset+32, m.m11())
            .putDouble(offset+40, 0.0)
            .putDouble(offset+48, m.m20())
            .putDouble(offset+56, m.m21())
            .putDouble(offset+64, 1.0);
        }

        private void putTransposedN(Matrix4f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m10())
            .put(offset+2,  m.m20())
            .put(offset+3,  m.m30())
            .put(offset+4,  m.m01())
            .put(offset+5,  m.m11())
            .put(offset+6,  m.m21())
            .put(offset+7,  m.m31())
            .put(offset+8,  m.m02())
            .put(offset+9,  m.m12())
            .put(offset+10, m.m22())
            .put(offset+11, m.m32())
            .put(offset+12, m.m03())
            .put(offset+13, m.m13())
            .put(offset+14, m.m23())
            .put(offset+15, m.m33());
        }
        private void putTransposed0(Matrix4f m, FloatBuffer dest) {
            dest.put(0,    m.m00())
            .put(1,  m.m10())
            .put(2,  m.m20())
            .put(3,  m.m30())
            .put(4,  m.m01())
            .put(5,  m.m11())
            .put(6,  m.m21())
            .put(7,  m.m31())
            .put(8,  m.m02())
            .put(9,  m.m12())
            .put(10, m.m22())
            .put(11, m.m32())
            .put(12, m.m03())
            .put(13, m.m13())
            .put(14, m.m23())
            .put(15, m.m33());
        }
        public void putTransposed(Matrix4f m, int offset, FloatBuffer dest) {
            if (offset == 0)
                putTransposed0(m, dest);
            else
                putTransposedN(m, offset, dest);
        }

        private void putTransposedN(Matrix4f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m10())
            .putFloat(offset+8,  m.m20())
            .putFloat(offset+12, m.m30())
            .putFloat(offset+16, m.m01())
            .putFloat(offset+20, m.m11())
            .putFloat(offset+24, m.m21())
            .putFloat(offset+28, m.m31())
            .putFloat(offset+32, m.m02())
            .putFloat(offset+36, m.m12())
            .putFloat(offset+40, m.m22())
            .putFloat(offset+44, m.m32())
            .putFloat(offset+48, m.m03())
            .putFloat(offset+52, m.m13())
            .putFloat(offset+56, m.m23())
            .putFloat(offset+60, m.m33());
        }
        private void putTransposed0(Matrix4f m, ByteBuffer dest) {
            dest.putFloat(0,    m.m00())
            .putFloat(4,  m.m10())
            .putFloat(8,  m.m20())
            .putFloat(12, m.m30())
            .putFloat(16, m.m01())
            .putFloat(20, m.m11())
            .putFloat(24, m.m21())
            .putFloat(28, m.m31())
            .putFloat(32, m.m02())
            .putFloat(36, m.m12())
            .putFloat(40, m.m22())
            .putFloat(44, m.m32())
            .putFloat(48, m.m03())
            .putFloat(52, m.m13())
            .putFloat(56, m.m23())
            .putFloat(60, m.m33());
        }
        public void putTransposed(Matrix4f m, int offset, ByteBuffer dest) {
            if (offset == 0)
                putTransposed0(m, dest);
            else
                putTransposedN(m, offset, dest);
        }

        public void put4x3Transposed(Matrix4f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m10())
            .put(offset+2,  m.m20())
            .put(offset+3,  m.m30())
            .put(offset+4,  m.m01())
            .put(offset+5,  m.m11())
            .put(offset+6,  m.m21())
            .put(offset+7,  m.m31())
            .put(offset+8,  m.m02())
            .put(offset+9,  m.m12())
            .put(offset+10, m.m22())
            .put(offset+11, m.m32());
        }

        public void put4x3Transposed(Matrix4f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m10())
            .putFloat(offset+8,  m.m20())
            .putFloat(offset+12, m.m30())
            .putFloat(offset+16, m.m01())
            .putFloat(offset+20, m.m11())
            .putFloat(offset+24, m.m21())
            .putFloat(offset+28, m.m31())
            .putFloat(offset+32, m.m02())
            .putFloat(offset+36, m.m12())
            .putFloat(offset+40, m.m22())
            .putFloat(offset+44, m.m32());
        }

        public void putTransposed(Matrix4x3f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m10())
            .put(offset+2,  m.m20())
            .put(offset+3,  m.m30())
            .put(offset+4,  m.m01())
            .put(offset+5,  m.m11())
            .put(offset+6,  m.m21())
            .put(offset+7,  m.m31())
            .put(offset+8,  m.m02())
            .put(offset+9,  m.m12())
            .put(offset+10, m.m22())
            .put(offset+11, m.m32());
        }

        public void putTransposed(Matrix4x3f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m10())
            .putFloat(offset+8,  m.m20())
            .putFloat(offset+12, m.m30())
            .putFloat(offset+16, m.m01())
            .putFloat(offset+20, m.m11())
            .putFloat(offset+24, m.m21())
            .putFloat(offset+28, m.m31())
            .putFloat(offset+32, m.m02())
            .putFloat(offset+36, m.m12())
            .putFloat(offset+40, m.m22())
            .putFloat(offset+44, m.m32());
        }

        public void putTransposed(Matrix3f m, int offset, FloatBuffer dest) {
            dest.put(offset,   m.m00())
            .put(offset+1, m.m10())
            .put(offset+2, m.m20())
            .put(offset+3, m.m01())
            .put(offset+4, m.m11())
            .put(offset+5, m.m21())
            .put(offset+6, m.m02())
            .put(offset+7, m.m12())
            .put(offset+8, m.m22());
        }

        public void putTransposed(Matrix3f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m10())
            .putFloat(offset+8,  m.m20())
            .putFloat(offset+12, m.m01())
            .putFloat(offset+16, m.m11())
            .putFloat(offset+20, m.m21())
            .putFloat(offset+24, m.m02())
            .putFloat(offset+28, m.m12())
            .putFloat(offset+32, m.m22());
        }

        public void putTransposed(Matrix2f m, int offset, FloatBuffer dest) {
            dest.put(offset,   m.m00())
            .put(offset+1, m.m10())
            .put(offset+2, m.m01())
            .put(offset+3, m.m11());
        }

        public void putTransposed(Matrix3x2f m, int offset, FloatBuffer dest) {
            dest
            .put(offset,   m.m00())
            .put(offset+1, m.m10())
            .put(offset+2, m.m20())
            .put(offset+3, m.m01())
            .put(offset+4, m.m11())
            .put(offset+5, m.m21());
        }

        public void putTransposed(Matrix2f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m10())
            .putFloat(offset+8,  m.m01())
            .putFloat(offset+12, m.m11());
        }

        public void put(Matrix4d m, int offset, DoubleBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m01())
            .put(offset+2,  m.m02())
            .put(offset+3,  m.m03())
            .put(offset+4,  m.m10())
            .put(offset+5,  m.m11())
            .put(offset+6,  m.m12())
            .put(offset+7,  m.m13())
            .put(offset+8,  m.m20())
            .put(offset+9,  m.m21())
            .put(offset+10, m.m22())
            .put(offset+11, m.m23())
            .put(offset+12, m.m30())
            .put(offset+13, m.m31())
            .put(offset+14, m.m32())
            .put(offset+15, m.m33());
        }

        public void put(Matrix4d m, int offset, ByteBuffer dest) {
            dest.putDouble(offset,     m.m00())
            .putDouble(offset+8,   m.m01())
            .putDouble(offset+16,   m.m02())
            .putDouble(offset+24,  m.m03())
            .putDouble(offset+32,  m.m10())
            .putDouble(offset+40,  m.m11())
            .putDouble(offset+48,  m.m12())
            .putDouble(offset+56,  m.m13())
            .putDouble(offset+64,  m.m20())
            .putDouble(offset+72,  m.m21())
            .putDouble(offset+80,  m.m22())
            .putDouble(offset+88,  m.m23())
            .putDouble(offset+96,  m.m30())
            .putDouble(offset+104, m.m31())
            .putDouble(offset+112, m.m32())
            .putDouble(offset+120, m.m33());
        }

        public void put(Matrix4x3d m, int offset, DoubleBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m01())
            .put(offset+2,  m.m02())
            .put(offset+3,  m.m10())
            .put(offset+4,  m.m11())
            .put(offset+5,  m.m12())
            .put(offset+6,  m.m20())
            .put(offset+7,  m.m21())
            .put(offset+8,  m.m22())
            .put(offset+9,  m.m30())
            .put(offset+10, m.m31())
            .put(offset+11, m.m32());
        }

        public void put(Matrix4x3d m, int offset, ByteBuffer dest) {
            dest.putDouble(offset,    m.m00())
            .putDouble(offset+8,  m.m01())
            .putDouble(offset+16,  m.m02())
            .putDouble(offset+24, m.m10())
            .putDouble(offset+32, m.m11())
            .putDouble(offset+40, m.m12())
            .putDouble(offset+48, m.m20())
            .putDouble(offset+56, m.m21())
            .putDouble(offset+64, m.m22())
            .putDouble(offset+72, m.m30())
            .putDouble(offset+80, m.m31())
            .putDouble(offset+88, m.m32());
        }

        public void putf(Matrix4d m, int offset, FloatBuffer dest) {
            dest.put(offset,    (float)m.m00())
            .put(offset+1,  (float)m.m01())
            .put(offset+2,  (float)m.m02())
            .put(offset+3,  (float)m.m03())
            .put(offset+4,  (float)m.m10())
            .put(offset+5,  (float)m.m11())
            .put(offset+6,  (float)m.m12())
            .put(offset+7,  (float)m.m13())
            .put(offset+8,  (float)m.m20())
            .put(offset+9,  (float)m.m21())
            .put(offset+10, (float)m.m22())
            .put(offset+11, (float)m.m23())
            .put(offset+12, (float)m.m30())
            .put(offset+13, (float)m.m31())
            .put(offset+14, (float)m.m32())
            .put(offset+15, (float)m.m33());
        }

        public void putf(Matrix4d m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    (float)m.m00())
            .putFloat(offset+4,  (float)m.m01())
            .putFloat(offset+8,  (float)m.m02())
            .putFloat(offset+12, (float)m.m03())
            .putFloat(offset+16, (float)m.m10())
            .putFloat(offset+20, (float)m.m11())
            .putFloat(offset+24, (float)m.m12())
            .putFloat(offset+28, (float)m.m13())
            .putFloat(offset+32, (float)m.m20())
            .putFloat(offset+36, (float)m.m21())
            .putFloat(offset+40, (float)m.m22())
            .putFloat(offset+44, (float)m.m23())
            .putFloat(offset+48, (float)m.m30())
            .putFloat(offset+52, (float)m.m31())
            .putFloat(offset+56, (float)m.m32())
            .putFloat(offset+60, (float)m.m33());
        }

        public void putf(Matrix4x3d m, int offset, FloatBuffer dest) {
            dest.put(offset,    (float)m.m00())
            .put(offset+1,  (float)m.m01())
            .put(offset+2,  (float)m.m02())
            .put(offset+3,  (float)m.m10())
            .put(offset+4,  (float)m.m11())
            .put(offset+5,  (float)m.m12())
            .put(offset+6,  (float)m.m20())
            .put(offset+7,  (float)m.m21())
            .put(offset+8,  (float)m.m22())
            .put(offset+9,  (float)m.m30())
            .put(offset+10, (float)m.m31())
            .put(offset+11, (float)m.m32());
        }

        public void putf(Matrix4x3d m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    (float)m.m00())
            .putFloat(offset+4,  (float)m.m01())
            .putFloat(offset+8,  (float)m.m02())
            .putFloat(offset+12, (float)m.m10())
            .putFloat(offset+16, (float)m.m11())
            .putFloat(offset+20, (float)m.m12())
            .putFloat(offset+24, (float)m.m20())
            .putFloat(offset+28, (float)m.m21())
            .putFloat(offset+32, (float)m.m22())
            .putFloat(offset+36, (float)m.m30())
            .putFloat(offset+40, (float)m.m31())
            .putFloat(offset+44, (float)m.m32());
        }

        public void putTransposed(Matrix4d m, int offset, DoubleBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m10())
            .put(offset+2,  m.m20())
            .put(offset+3,  m.m30())
            .put(offset+4,  m.m01())
            .put(offset+5,  m.m11())
            .put(offset+6,  m.m21())
            .put(offset+7,  m.m31())
            .put(offset+8,  m.m02())
            .put(offset+9,  m.m12())
            .put(offset+10, m.m22())
            .put(offset+11, m.m32())
            .put(offset+12, m.m03())
            .put(offset+13, m.m13())
            .put(offset+14, m.m23())
            .put(offset+15, m.m33());
        }

        public void putTransposed(Matrix4d m, int offset, ByteBuffer dest) {
            dest.putDouble(offset,     m.m00())
            .putDouble(offset+8,   m.m10())
            .putDouble(offset+16,  m.m20())
            .putDouble(offset+24,  m.m30())
            .putDouble(offset+32,  m.m01())
            .putDouble(offset+40,  m.m11())
            .putDouble(offset+48,  m.m21())
            .putDouble(offset+56,  m.m31())
            .putDouble(offset+64,  m.m02())
            .putDouble(offset+72,  m.m12())
            .putDouble(offset+80,  m.m22())
            .putDouble(offset+88,  m.m32())
            .putDouble(offset+96,  m.m03())
            .putDouble(offset+104, m.m13())
            .putDouble(offset+112, m.m23())
            .putDouble(offset+120, m.m33());
        }

        public void put4x3Transposed(Matrix4d m, int offset, DoubleBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m10())
            .put(offset+2,  m.m20())
            .put(offset+3,  m.m30())
            .put(offset+4,  m.m01())
            .put(offset+5,  m.m11())
            .put(offset+6,  m.m21())
            .put(offset+7,  m.m31())
            .put(offset+8,  m.m02())
            .put(offset+9,  m.m12())
            .put(offset+10, m.m22())
            .put(offset+11, m.m32());
        }

        public void put4x3Transposed(Matrix4d m, int offset, ByteBuffer dest) {
            dest.putDouble(offset,     m.m00())
            .putDouble(offset+8,   m.m10())
            .putDouble(offset+16,  m.m20())
            .putDouble(offset+24,  m.m30())
            .putDouble(offset+32,  m.m01())
            .putDouble(offset+40,  m.m11())
            .putDouble(offset+48,  m.m21())
            .putDouble(offset+56,  m.m31())
            .putDouble(offset+64,  m.m02())
            .putDouble(offset+72,  m.m12())
            .putDouble(offset+80,  m.m22())
            .putDouble(offset+88,  m.m32());
        }

        public void putTransposed(Matrix4x3d m, int offset, DoubleBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m10())
            .put(offset+2,  m.m20())
            .put(offset+3,  m.m30())
            .put(offset+4,  m.m01())
            .put(offset+5,  m.m11())
            .put(offset+6,  m.m21())
            .put(offset+7,  m.m31())
            .put(offset+8,  m.m02())
            .put(offset+9,  m.m12())
            .put(offset+10, m.m22())
            .put(offset+11, m.m32());
        }

        public void putTransposed(Matrix4x3d m, int offset, ByteBuffer dest) {
            dest.putDouble(offset,    m.m00())
            .putDouble(offset+8,  m.m10())
            .putDouble(offset+16,  m.m20())
            .putDouble(offset+24, m.m30())
            .putDouble(offset+32, m.m01())
            .putDouble(offset+40, m.m11())
            .putDouble(offset+48, m.m21())
            .putDouble(offset+56, m.m31())
            .putDouble(offset+64, m.m02())
            .putDouble(offset+72, m.m12())
            .putDouble(offset+80, m.m22())
            .putDouble(offset+88, m.m32());
        }

        public void putTransposed(Matrix3d m, int offset, DoubleBuffer dest) {
            dest
            .put(offset,   m.m00())
            .put(offset+1, m.m10())
            .put(offset+2, m.m20())
            .put(offset+3, m.m01())
            .put(offset+4, m.m11())
            .put(offset+5, m.m21())
            .put(offset+6, m.m02())
            .put(offset+7, m.m12())
            .put(offset+8, m.m22());
        }

        public void putTransposed(Matrix3d m, int offset, ByteBuffer dest) {
            dest.putDouble(offset,    m.m00())
            .putDouble(offset+8,  m.m10())
            .putDouble(offset+16,  m.m20())
            .putDouble(offset+24, m.m01())
            .putDouble(offset+32, m.m11())
            .putDouble(offset+40, m.m21())
            .putDouble(offset+48, m.m02())
            .putDouble(offset+56, m.m12())
            .putDouble(offset+64, m.m22());
        }

        public void putTransposed(Matrix3x2d m, int offset, ByteBuffer dest) {
            dest
            .putDouble(offset,    m.m00())
            .putDouble(offset+8,  m.m10())
            .putDouble(offset+16, m.m20())
            .putDouble(offset+24, m.m01())
            .putDouble(offset+32, m.m11())
            .putDouble(offset+40, m.m21());
        }

        public void putTransposed(Matrix3x2d m, int offset, DoubleBuffer dest) {
            dest
            .put(offset,   m.m00())
            .put(offset+1, m.m10())
            .put(offset+2, m.m20())
            .put(offset+3, m.m01())
            .put(offset+4, m.m11())
            .put(offset+5, m.m21());
        }

        public void putTransposed(Matrix2d m, int offset, DoubleBuffer dest) {
            dest.put(offset,   m.m00())
            .put(offset+1, m.m10())
            .put(offset+2, m.m01())
            .put(offset+3, m.m11());
        }

        public void putTransposed(Matrix2d m, int offset, ByteBuffer dest) {
            dest.putDouble(offset,    m.m00())
            .putDouble(offset+8,  m.m10())
            .putDouble(offset+16, m.m01())
            .putDouble(offset+24, m.m11());
        }

        public void putfTransposed(Matrix4x3d m, int offset, FloatBuffer dest) {
            dest.put(offset,    (float)m.m00())
            .put(offset+1,  (float)m.m10())
            .put(offset+2,  (float)m.m20())
            .put(offset+3,  (float)m.m30())
            .put(offset+4,  (float)m.m01())
            .put(offset+5,  (float)m.m11())
            .put(offset+6,  (float)m.m21())
            .put(offset+7,  (float)m.m31())
            .put(offset+8,  (float)m.m02())
            .put(offset+9,  (float)m.m12())
            .put(offset+10, (float)m.m22())
            .put(offset+11, (float)m.m32());
        }

        public void putfTransposed(Matrix4x3d m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    (float)m.m00())
            .putFloat(offset+4,  (float)m.m10())
            .putFloat(offset+8,  (float)m.m20())
            .putFloat(offset+12, (float)m.m30())
            .putFloat(offset+16, (float)m.m01())
            .putFloat(offset+20, (float)m.m11())
            .putFloat(offset+24, (float)m.m21())
            .putFloat(offset+28, (float)m.m31())
            .putFloat(offset+32, (float)m.m02())
            .putFloat(offset+36, (float)m.m12())
            .putFloat(offset+40, (float)m.m22())
            .putFloat(offset+44, (float)m.m32());
        }

        public void putfTransposed(Matrix3d m, int offset, FloatBuffer dest) {
            dest
            .put(offset,   (float)m.m00())
            .put(offset+1, (float)m.m10())
            .put(offset+2, (float)m.m20())
            .put(offset+3, (float)m.m01())
            .put(offset+4, (float)m.m11())
            .put(offset+5, (float)m.m21())
            .put(offset+6, (float)m.m02())
            .put(offset+7, (float)m.m12())
            .put(offset+8, (float)m.m22());
        }

        public void putfTransposed(Matrix3d m, int offset, ByteBuffer dest) {
            dest
            .putFloat(offset,    (float)m.m00())
            .putFloat(offset+4,  (float)m.m10())
            .putFloat(offset+8,  (float)m.m20())
            .putFloat(offset+12, (float)m.m01())
            .putFloat(offset+16, (float)m.m11())
            .putFloat(offset+20, (float)m.m21())
            .putFloat(offset+24, (float)m.m02())
            .putFloat(offset+28, (float)m.m12())
            .putFloat(offset+32, (float)m.m22());
        }

        public void putfTransposed(Matrix3x2d m, int offset, FloatBuffer dest) {
            dest
            .put(offset,   (float)m.m00())
            .put(offset+1, (float)m.m10())
            .put(offset+2, (float)m.m20())
            .put(offset+3, (float)m.m01())
            .put(offset+4, (float)m.m11())
            .put(offset+5, (float)m.m21());
        }

        public void putfTransposed(Matrix3x2d m, int offset, ByteBuffer dest) {
            dest
            .putFloat(offset,    (float)m.m00())
            .putFloat(offset+4,  (float)m.m10())
            .putFloat(offset+8,  (float)m.m20())
            .putFloat(offset+12, (float)m.m01())
            .putFloat(offset+16, (float)m.m11())
            .putFloat(offset+20, (float)m.m21());
        }

        public void putfTransposed(Matrix2d m, int offset, FloatBuffer dest) {
            dest.put(offset,   (float)m.m00())
            .put(offset+1, (float)m.m10())
            .put(offset+2, (float)m.m01())
            .put(offset+3, (float)m.m11());
        }

        public void putfTransposed(Matrix2d m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    (float)m.m00())
            .putFloat(offset+4,  (float)m.m10())
            .putFloat(offset+8,  (float)m.m01())
            .putFloat(offset+12, (float)m.m11());
        }

        public void putfTransposed(Matrix4d m, int offset, FloatBuffer dest) {
            dest.put(offset,    (float)m.m00())
            .put(offset+1,  (float)m.m10())
            .put(offset+2,  (float)m.m20())
            .put(offset+3,  (float)m.m30())
            .put(offset+4,  (float)m.m01())
            .put(offset+5,  (float)m.m11())
            .put(offset+6,  (float)m.m21())
            .put(offset+7,  (float)m.m31())
            .put(offset+8,  (float)m.m02())
            .put(offset+9,  (float)m.m12())
            .put(offset+10, (float)m.m22())
            .put(offset+11, (float)m.m32())
            .put(offset+12, (float)m.m03())
            .put(offset+13, (float)m.m13())
            .put(offset+14, (float)m.m23())
            .put(offset+15, (float)m.m33());
        }

        public void putfTransposed(Matrix4d m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    (float)m.m00())
            .putFloat(offset+4,  (float)m.m10())
            .putFloat(offset+8,  (float)m.m20())
            .putFloat(offset+12, (float)m.m30())
            .putFloat(offset+16, (float)m.m01())
            .putFloat(offset+20, (float)m.m11())
            .putFloat(offset+24, (float)m.m21())
            .putFloat(offset+28, (float)m.m31())
            .putFloat(offset+32, (float)m.m02())
            .putFloat(offset+36, (float)m.m12())
            .putFloat(offset+40, (float)m.m22())
            .putFloat(offset+44, (float)m.m32())
            .putFloat(offset+48, (float)m.m03())
            .putFloat(offset+52, (float)m.m13())
            .putFloat(offset+56, (float)m.m23())
            .putFloat(offset+60, (float)m.m33());
        }

        public void put0(Matrix3f m, FloatBuffer dest) {
            dest.put(0,   m.m00())
            .put(1, m.m01())
            .put(2, m.m02())
            .put(3, m.m10())
            .put(4, m.m11())
            .put(5, m.m12())
            .put(6, m.m20())
            .put(7, m.m21())
            .put(8, m.m22());
        }
        public void putN(Matrix3f m, int offset, FloatBuffer dest) {
            dest.put(offset,   m.m00())
            .put(offset+1, m.m01())
            .put(offset+2, m.m02())
            .put(offset+3, m.m10())
            .put(offset+4, m.m11())
            .put(offset+5, m.m12())
            .put(offset+6, m.m20())
            .put(offset+7, m.m21())
            .put(offset+8, m.m22());
        }
        public void put(Matrix3f m, int offset, FloatBuffer dest) {
            if (offset == 0)
                put0(m, dest);
            else
                putN(m, offset, dest);
        }

        public void put0(Matrix3f m, ByteBuffer dest) {
            dest.putFloat(0,    m.m00())
            .putFloat(4,  m.m01())
            .putFloat(8,  m.m02())
            .putFloat(12, m.m10())
            .putFloat(16, m.m11())
            .putFloat(20, m.m12())
            .putFloat(24, m.m20())
            .putFloat(28, m.m21())
            .putFloat(32, m.m22());
        }
        public void putN(Matrix3f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m01())
            .putFloat(offset+8,  m.m02())
            .putFloat(offset+12, m.m10())
            .putFloat(offset+16, m.m11())
            .putFloat(offset+20, m.m12())
            .putFloat(offset+24, m.m20())
            .putFloat(offset+28, m.m21())
            .putFloat(offset+32, m.m22());
        }
        public void put(Matrix3f m, int offset, ByteBuffer dest) {
            if (offset == 0)
                put0(m, dest);
            else
                putN(m, offset, dest);
        }

        public void put3x4_0(Matrix3f m, ByteBuffer dest) {
            dest.putFloat(0,  m.m00())
            .putFloat(4,  m.m01())
            .putFloat(8,  m.m02())
            .putFloat(12, 0.0f)
            .putFloat(16, m.m10())
            .putFloat(20, m.m11())
            .putFloat(24, m.m12())
            .putFloat(28, 0.0f)
            .putFloat(32, m.m20())
            .putFloat(36, m.m21())
            .putFloat(40, m.m22())
            .putFloat(44, 0.0f);
        }
        private void put3x4_N(Matrix3f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m01())
            .putFloat(offset+8,  m.m02())
            .putFloat(offset+12, 0.0f)
            .putFloat(offset+16, m.m10())
            .putFloat(offset+20, m.m11())
            .putFloat(offset+24, m.m12())
            .putFloat(offset+28, 0.0f)
            .putFloat(offset+32, m.m20())
            .putFloat(offset+36, m.m21())
            .putFloat(offset+40, m.m22())
            .putFloat(offset+44, 0.0f);
        }
        public void put3x4(Matrix3f m, int offset, ByteBuffer dest) {
            if (offset == 0)
                put3x4_0(m, dest);
            else
                put3x4_N(m, offset, dest);
        }

        public void put3x4_0(Matrix3f m, FloatBuffer dest) {
            dest.put(0,  m.m00())
            .put(1,  m.m01())
            .put(2,  m.m02())
            .put(3,  0.0f)
            .put(4,  m.m10())
            .put(5,  m.m11())
            .put(6,  m.m12())
            .put(7,  0.0f)
            .put(8,  m.m20())
            .put(9,  m.m21())
            .put(10, m.m22())
            .put(11, 0.0f);
        }
        public void put3x4_N(Matrix3f m, int offset, FloatBuffer dest) {
            dest.put(offset,    m.m00())
            .put(offset+1,  m.m01())
            .put(offset+2,  m.m02())
            .put(offset+3,  0.0f)
            .put(offset+4,  m.m10())
            .put(offset+5,  m.m11())
            .put(offset+6,  m.m12())
            .put(offset+7,  0.0f)
            .put(offset+8,  m.m20())
            .put(offset+9,  m.m21())
            .put(offset+10, m.m22())
            .put(offset+11, 0.0f);
        }
        public void put3x4(Matrix3f m, int offset, FloatBuffer dest) {
            if (offset == 0)
                put3x4_0(m, dest);
            else
                put3x4_N(m, offset, dest);
        }

        public void put(Matrix3d m, int offset, DoubleBuffer dest) {
            dest.put(offset,   m.m00())
            .put(offset+1, m.m01())
            .put(offset+2, m.m02())
            .put(offset+3, m.m10())
            .put(offset+4, m.m11())
            .put(offset+5, m.m12())
            .put(offset+6, m.m20())
            .put(offset+7, m.m21())
            .put(offset+8, m.m22());
        }

        public void put(Matrix3d m, int offset, ByteBuffer dest) {
            dest.putDouble(offset,    m.m00())
            .putDouble(offset+8,  m.m01())
            .putDouble(offset+16, m.m02())
            .putDouble(offset+24, m.m10())
            .putDouble(offset+32, m.m11())
            .putDouble(offset+40, m.m12())
            .putDouble(offset+48, m.m20())
            .putDouble(offset+56, m.m21())
            .putDouble(offset+64, m.m22());
        }

        public void put(Matrix3x2f m, int offset, FloatBuffer dest) {
            dest.put(offset,   m.m00())
            .put(offset+1, m.m01())
            .put(offset+2, m.m10())
            .put(offset+3, m.m11())
            .put(offset+4, m.m20())
            .put(offset+5, m.m21());
        }

        public void put(Matrix3x2f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m01())
            .putFloat(offset+8,  m.m10())
            .putFloat(offset+12, m.m11())
            .putFloat(offset+16, m.m20())
            .putFloat(offset+20, m.m21());
        }

        public void put(Matrix3x2d m, int offset, DoubleBuffer dest) {
            dest.put(offset,   m.m00())
            .put(offset+1, m.m01())
            .put(offset+2, m.m10())
            .put(offset+3, m.m11())
            .put(offset+4, m.m20())
            .put(offset+5, m.m21());
        }

        public void put(Matrix3x2d m, int offset, ByteBuffer dest) {
            dest.putDouble(offset,    m.m00())
            .putDouble(offset+8,  m.m01())
            .putDouble(offset+16, m.m10())
            .putDouble(offset+24, m.m11())
            .putDouble(offset+32, m.m20())
            .putDouble(offset+40, m.m21());
        }

        public void putf(Matrix3d m, int offset, FloatBuffer dest) {
            dest.put(offset,   (float)m.m00())
            .put(offset+1, (float)m.m01())
            .put(offset+2, (float)m.m02())
            .put(offset+3, (float)m.m10())
            .put(offset+4, (float)m.m11())
            .put(offset+5, (float)m.m12())
            .put(offset+6, (float)m.m20())
            .put(offset+7, (float)m.m21())
            .put(offset+8, (float)m.m22());
        }

        public void put(Matrix2f m, int offset, FloatBuffer dest) {
            dest.put(offset,   m.m00())
            .put(offset+1, m.m01())
            .put(offset+2, m.m10())
            .put(offset+3, m.m11());
        }

        public void put(Matrix2f m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    m.m00())
            .putFloat(offset+4,  m.m01())
            .putFloat(offset+8,  m.m10())
            .putFloat(offset+12, m.m11());
        }

        public void put(Matrix2d m, int offset, DoubleBuffer dest) {
            dest.put(offset,   m.m00())
            .put(offset+1, m.m01())
            .put(offset+2, m.m10())
            .put(offset+3, m.m11());
        }

        public void put(Matrix2d m, int offset, ByteBuffer dest) {
            dest.putDouble(offset,    m.m00())
            .putDouble(offset+8,  m.m01())
            .putDouble(offset+16, m.m10())
            .putDouble(offset+24, m.m11());
        }

        public void putf(Matrix2d m, int offset, FloatBuffer dest) {
            dest.put(offset,   (float)m.m00())
            .put(offset+1, (float)m.m01())
            .put(offset+2, (float)m.m10())
            .put(offset+3, (float)m.m11());
        }

        public void putf(Matrix2d m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    (float)m.m00())
            .putFloat(offset+4,  (float)m.m01())
            .putFloat(offset+8,  (float)m.m10())
            .putFloat(offset+12, (float)m.m11());
        }

        public void putf(Matrix3d m, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    (float)m.m00())
            .putFloat(offset+4,  (float)m.m01())
            .putFloat(offset+8,  (float)m.m02())
            .putFloat(offset+12, (float)m.m10())
            .putFloat(offset+16, (float)m.m11())
            .putFloat(offset+20, (float)m.m12())
            .putFloat(offset+24, (float)m.m20())
            .putFloat(offset+28, (float)m.m21())
            .putFloat(offset+32, (float)m.m22());
        }

        public void put(Vector4d src, int offset, DoubleBuffer dest) {
            dest.put(offset,   src.x)
            .put(offset+1, src.y)
            .put(offset+2, src.z)
            .put(offset+3, src.w);
        }

        public void put(Vector4d src, int offset, FloatBuffer dest) {
            dest.put(offset,   (float)src.x)
            .put(offset+1, (float)src.y)
            .put(offset+2, (float)src.z)
            .put(offset+3, (float)src.w);
        }

        public void put(Vector4d src, int offset, ByteBuffer dest) {
            dest.putDouble(offset,    src.x)
            .putDouble(offset+8,  src.y)
            .putDouble(offset+16, src.z)
            .putDouble(offset+24, src.w);
        }

        public void putf(Vector4d src, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    (float) src.x)
            .putFloat(offset+4,  (float) src.y)
            .putFloat(offset+8,  (float) src.z)
            .putFloat(offset+12, (float) src.w);
        }

        public void put(Vector4f src, int offset, FloatBuffer dest) {
            dest.put(offset,   src.x)
            .put(offset+1, src.y)
            .put(offset+2, src.z)
            .put(offset+3, src.w);
        }

        public void put(Vector4f src, int offset, ByteBuffer dest) {
            dest.putFloat(offset,    src.x)
            .putFloat(offset+4,  src.y)
            .putFloat(offset+8,  src.z)
            .putFloat(offset+12, src.w);
        }

        public void put(Vector4L src, int offset, LongBuffer dest) {
            dest.put(offset,   src.x)
            .put(offset+1, src.y)
            .put(offset+2, src.z)
            .put(offset+3, src.w);
        }

        public void put(Vector4L src, int offset, ByteBuffer dest) {
            dest.putLong(offset,    src.x)
            .putLong(offset+8,  src.y)
            .putLong(offset+16, src.z)
            .putLong(offset+24, src.w);
        }

        public void put(Vector4i src, int offset, IntBuffer dest) {
            dest.put(offset,   src.x)
            .put(offset+1, src.y)
            .put(offset+2, src.z)
            .put(offset+3, src.w);
        }

        public void put(Vector4i src, int offset, ByteBuffer dest) {
            dest.putInt(offset,    src.x)
            .putInt(offset+4,  src.y)
            .putInt(offset+8,  src.z)
            .putInt(offset+12, src.w);
        }

        public void put(Vector3f src, int offset, FloatBuffer dest) {
            dest.put(offset,   src.x)
            .put(offset+1, src.y)
            .put(offset+2, src.z);
        }

        public void put(Vector3f src, int offset, ByteBuffer dest) {
            dest.putFloat(offset,   src.x)
            .putFloat(offset+4, src.y)
            .putFloat(offset+8, src.z);
        }

        public void put(Vector3d src, int offset, DoubleBuffer dest) {
            dest.put(offset,   src.x)
            .put(offset+1, src.y)
            .put(offset+2, src.z);
        }

        public void put(Vector3d src, int offset, FloatBuffer dest) {
            dest.put(offset,   (float)src.x)
            .put(offset+1, (float)src.y)
            .put(offset+2, (float)src.z);
        }

        public void put(Vector3d src, int offset, ByteBuffer dest) {
            dest.putDouble(offset,    src.x)
            .putDouble(offset+8,  src.y)
            .putDouble(offset+16, src.z);
        }

        public void putf(Vector3d src, int offset, ByteBuffer dest) {
            dest.putFloat(offset,   (float) src.x)
            .putFloat(offset+4, (float) src.y)
            .putFloat(offset+8, (float) src.z);
        }

        public void put(Vector3i src, int offset, IntBuffer dest) {
            dest.put(offset,   src.x)
            .put(offset+1, src.y)
            .put(offset+2, src.z);
        }

        public void put(Vector3i src, int offset, ByteBuffer dest) {
            dest.putInt(offset,   src.x)
            .putInt(offset+4, src.y)
            .putInt(offset+8, src.z);
        }

        public void put(Vector3L src, int offset, LongBuffer dest) {
            dest.put(offset,   src.x)
                .put(offset+1, src.y)
                .put(offset+2, src.z);
        }

        public void put(Vector3L src, int offset, ByteBuffer dest) {
            dest.putLong(offset,   src.x)
                .putLong(offset+8, src.y)
                .putLong(offset+16, src.z);
        }

        public void put(Vector2f src, int offset, FloatBuffer dest) {
            dest.put(offset,   src.x)
            .put(offset+1, src.y);
        }

        public void put(Vector2f src, int offset, ByteBuffer dest) {
            dest.putFloat(offset,   src.x)
            .putFloat(offset+4, src.y);
        }

        public void put(Vector2d src, int offset, DoubleBuffer dest) {
            dest.put(offset,   src.x)
            .put(offset+1, src.y);
        }

        public void put(Vector2d src, int offset, ByteBuffer dest) {
            dest.putDouble(offset,   src.x)
            .putDouble(offset+8, src.y);
        }

        public void put(Vector2i src, int offset, IntBuffer dest) {
            dest.put(offset,   src.x)
            .put(offset+1, src.y);
        }

        public void put(Vector2i src, int offset, ByteBuffer dest) {
            dest.putInt(offset,   src.x)
            .putInt(offset+4, src.y);
        }

        public void put(Vector2i src, int offset, LongBuffer dest) {
            dest.put(offset,   src.x)
                .put(offset+1, src.y);
        }

        public void put(Vector2L src, int offset, ByteBuffer dest) {
            dest.putLong(offset,   src.x)
                .putLong(offset+8, src.y);
        }

        public void put(Vector2L src, int offset, LongBuffer dest) {
            dest.put(offset,   src.x)
                .put(offset+1, src.y);
        }

        public void get(Matrix4f m, int offset, FloatBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m02(src.get(offset+2))
            ._m03(src.get(offset+3))
            ._m10(src.get(offset+4))
            ._m11(src.get(offset+5))
            ._m12(src.get(offset+6))
            ._m13(src.get(offset+7))
            ._m20(src.get(offset+8))
            ._m21(src.get(offset+9))
            ._m22(src.get(offset+10))
            ._m23(src.get(offset+11))
            ._m30(src.get(offset+12))
            ._m31(src.get(offset+13))
            ._m32(src.get(offset+14))
            ._m33(src.get(offset+15));
        }

        public void get(Matrix4f m, int offset, ByteBuffer src) {
            m._m00(src.getFloat(offset))
            ._m01(src.getFloat(offset+4))
            ._m02(src.getFloat(offset+8))
            ._m03(src.getFloat(offset+12))
            ._m10(src.getFloat(offset+16))
            ._m11(src.getFloat(offset+20))
            ._m12(src.getFloat(offset+24))
            ._m13(src.getFloat(offset+28))
            ._m20(src.getFloat(offset+32))
            ._m21(src.getFloat(offset+36))
            ._m22(src.getFloat(offset+40))
            ._m23(src.getFloat(offset+44))
            ._m30(src.getFloat(offset+48))
            ._m31(src.getFloat(offset+52))
            ._m32(src.getFloat(offset+56))
            ._m33(src.getFloat(offset+60));
        }

        public void getTransposed(Matrix4f m, int offset, FloatBuffer src) {
            m._m00(src.get(offset))
            ._m10(src.get(offset+1))
            ._m20(src.get(offset+2))
            ._m30(src.get(offset+3))
            ._m01(src.get(offset+4))
            ._m11(src.get(offset+5))
            ._m21(src.get(offset+6))
            ._m31(src.get(offset+7))
            ._m02(src.get(offset+8))
            ._m12(src.get(offset+9))
            ._m22(src.get(offset+10))
            ._m32(src.get(offset+11))
            ._m03(src.get(offset+12))
            ._m13(src.get(offset+13))
            ._m23(src.get(offset+14))
            ._m33(src.get(offset+15));
        }

        public void getTransposed(Matrix4f m, int offset, ByteBuffer src) {
            m._m00(src.getFloat(offset))
            ._m10(src.getFloat(offset+4))
            ._m20(src.getFloat(offset+8))
            ._m30(src.getFloat(offset+12))
            ._m01(src.getFloat(offset+16))
            ._m11(src.getFloat(offset+20))
            ._m21(src.getFloat(offset+24))
            ._m31(src.getFloat(offset+28))
            ._m02(src.getFloat(offset+32))
            ._m12(src.getFloat(offset+36))
            ._m22(src.getFloat(offset+40))
            ._m32(src.getFloat(offset+44))
            ._m03(src.getFloat(offset+48))
            ._m13(src.getFloat(offset+52))
            ._m23(src.getFloat(offset+56))
            ._m33(src.getFloat(offset+60));
        }

        public void get(Matrix4x3f m, int offset, FloatBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m02(src.get(offset+2))
            ._m10(src.get(offset+3))
            ._m11(src.get(offset+4))
            ._m12(src.get(offset+5))
            ._m20(src.get(offset+6))
            ._m21(src.get(offset+7))
            ._m22(src.get(offset+8))
            ._m30(src.get(offset+9))
            ._m31(src.get(offset+10))
            ._m32(src.get(offset+11));
        }

        public void get(Matrix4x3f m, int offset, ByteBuffer src) {
            m._m00(src.getFloat(offset))
            ._m01(src.getFloat(offset+4))
            ._m02(src.getFloat(offset+8))
            ._m10(src.getFloat(offset+12))
            ._m11(src.getFloat(offset+16))
            ._m12(src.getFloat(offset+20))
            ._m20(src.getFloat(offset+24))
            ._m21(src.getFloat(offset+28))
            ._m22(src.getFloat(offset+32))
            ._m30(src.getFloat(offset+36))
            ._m31(src.getFloat(offset+40))
            ._m32(src.getFloat(offset+44));
        }

        public void get(Matrix4d m, int offset, DoubleBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m02(src.get(offset+2))
            ._m03(src.get(offset+3))
            ._m10(src.get(offset+4))
            ._m11(src.get(offset+5))
            ._m12(src.get(offset+6))
            ._m13(src.get(offset+7))
            ._m20(src.get(offset+8))
            ._m21(src.get(offset+9))
            ._m22(src.get(offset+10))
            ._m23(src.get(offset+11))
            ._m30(src.get(offset+12))
            ._m31(src.get(offset+13))
            ._m32(src.get(offset+14))
            ._m33(src.get(offset+15));
        }

        public void get(Matrix4d m, int offset, ByteBuffer src) {
            m._m00(src.getDouble(offset))
            ._m01(src.getDouble(offset+8))
            ._m02(src.getDouble(offset+16))
            ._m03(src.getDouble(offset+24))
            ._m10(src.getDouble(offset+32))
            ._m11(src.getDouble(offset+40))
            ._m12(src.getDouble(offset+48))
            ._m13(src.getDouble(offset+56))
            ._m20(src.getDouble(offset+64))
            ._m21(src.getDouble(offset+72))
            ._m22(src.getDouble(offset+80))
            ._m23(src.getDouble(offset+88))
            ._m30(src.getDouble(offset+96))
            ._m31(src.getDouble(offset+104))
            ._m32(src.getDouble(offset+112))
            ._m33(src.getDouble(offset+120));
        }

        public void get(Matrix4x3d m, int offset, DoubleBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m02(src.get(offset+2))
            ._m10(src.get(offset+3))
            ._m11(src.get(offset+4))
            ._m12(src.get(offset+5))
            ._m20(src.get(offset+6))
            ._m21(src.get(offset+7))
            ._m22(src.get(offset+8))
            ._m30(src.get(offset+9))
            ._m31(src.get(offset+10))
            ._m32(src.get(offset+11));
        }

        public void get(Matrix4x3d m, int offset, ByteBuffer src) {
            m._m00(src.getDouble(offset))
            ._m01(src.getDouble(offset+8))
            ._m02(src.getDouble(offset+16))
            ._m10(src.getDouble(offset+24))
            ._m11(src.getDouble(offset+32))
            ._m12(src.getDouble(offset+40))
            ._m20(src.getDouble(offset+48))
            ._m21(src.getDouble(offset+56))
            ._m22(src.getDouble(offset+64))
            ._m30(src.getDouble(offset+72))
            ._m31(src.getDouble(offset+80))
            ._m32(src.getDouble(offset+88));
        }

        public void getf(Matrix4d m, int offset, FloatBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m02(src.get(offset+2))
            ._m03(src.get(offset+3))
            ._m10(src.get(offset+4))
            ._m11(src.get(offset+5))
            ._m12(src.get(offset+6))
            ._m13(src.get(offset+7))
            ._m20(src.get(offset+8))
            ._m21(src.get(offset+9))
            ._m22(src.get(offset+10))
            ._m23(src.get(offset+11))
            ._m30(src.get(offset+12))
            ._m31(src.get(offset+13))
            ._m32(src.get(offset+14))
            ._m33(src.get(offset+15));
        }

        public void getf(Matrix4d m, int offset, ByteBuffer src) {
            m._m00(src.getFloat(offset))
            ._m01(src.getFloat(offset+4))
            ._m02(src.getFloat(offset+8))
            ._m03(src.getFloat(offset+12))
            ._m10(src.getFloat(offset+16))
            ._m11(src.getFloat(offset+20))
            ._m12(src.getFloat(offset+24))
            ._m13(src.getFloat(offset+28))
            ._m20(src.getFloat(offset+32))
            ._m21(src.getFloat(offset+36))
            ._m22(src.getFloat(offset+40))
            ._m23(src.getFloat(offset+44))
            ._m30(src.getFloat(offset+48))
            ._m31(src.getFloat(offset+52))
            ._m32(src.getFloat(offset+56))
            ._m33(src.getFloat(offset+60));
        }

        public void getf(Matrix4x3d m, int offset, FloatBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m02(src.get(offset+2))
            ._m10(src.get(offset+3))
            ._m11(src.get(offset+4))
            ._m12(src.get(offset+5))
            ._m20(src.get(offset+6))
            ._m21(src.get(offset+7))
            ._m22(src.get(offset+8))
            ._m30(src.get(offset+9))
            ._m31(src.get(offset+10))
            ._m32(src.get(offset+11));
        }

        public void getf(Matrix4x3d m, int offset, ByteBuffer src) {
            m._m00(src.getFloat(offset))
            ._m01(src.getFloat(offset+4))
            ._m02(src.getFloat(offset+8))
            ._m10(src.getFloat(offset+12))
            ._m11(src.getFloat(offset+16))
            ._m12(src.getFloat(offset+20))
            ._m20(src.getFloat(offset+24))
            ._m21(src.getFloat(offset+28))
            ._m22(src.getFloat(offset+32))
            ._m30(src.getFloat(offset+36))
            ._m31(src.getFloat(offset+40))
            ._m32(src.getFloat(offset+44));
        }

        public void get(Matrix3f m, int offset, FloatBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m02(src.get(offset+2))
            ._m10(src.get(offset+3))
            ._m11(src.get(offset+4))
            ._m12(src.get(offset+5))
            ._m20(src.get(offset+6))
            ._m21(src.get(offset+7))
            ._m22(src.get(offset+8));
        }

        public void get(Matrix3f m, int offset, ByteBuffer src) {
            m._m00(src.getFloat(offset))
            ._m01(src.getFloat(offset+4))
            ._m02(src.getFloat(offset+8))
            ._m10(src.getFloat(offset+12))
            ._m11(src.getFloat(offset+16))
            ._m12(src.getFloat(offset+20))
            ._m20(src.getFloat(offset+24))
            ._m21(src.getFloat(offset+28))
            ._m22(src.getFloat(offset+32));
        }

        public void get(Matrix3d m, int offset, DoubleBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m02(src.get(offset+2))
            ._m10(src.get(offset+3))
            ._m11(src.get(offset+4))
            ._m12(src.get(offset+5))
            ._m20(src.get(offset+6))
            ._m21(src.get(offset+7))
            ._m22(src.get(offset+8));
        }

        public void get(Matrix3d m, int offset, ByteBuffer src) {
            m._m00(src.getDouble(offset))
            ._m01(src.getDouble(offset+8))
            ._m02(src.getDouble(offset+16))
            ._m10(src.getDouble(offset+24))
            ._m11(src.getDouble(offset+32))
            ._m12(src.getDouble(offset+40))
            ._m20(src.getDouble(offset+48))
            ._m21(src.getDouble(offset+56))
            ._m22(src.getDouble(offset+64));
        }

        public void get(Matrix3x2f m, int offset, FloatBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m10(src.get(offset+2))
            ._m11(src.get(offset+3))
            ._m20(src.get(offset+4))
            ._m21(src.get(offset+5));
        }

        public void get(Matrix3x2f m, int offset, ByteBuffer src) {
            m._m00(src.getFloat(offset))
            ._m01(src.getFloat(offset+4))
            ._m10(src.getFloat(offset+8))
            ._m11(src.getFloat(offset+12))
            ._m20(src.getFloat(offset+16))
            ._m21(src.getFloat(offset+20));
        }

        public void get(Matrix3x2d m, int offset, DoubleBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m10(src.get(offset+2))
            ._m11(src.get(offset+3))
            ._m20(src.get(offset+4))
            ._m21(src.get(offset+5));
        }

        public void get(Matrix3x2d m, int offset, ByteBuffer src) {
            m._m00(src.getDouble(offset))
            ._m01(src.getDouble(offset+8))
            ._m10(src.getDouble(offset+16))
            ._m11(src.getDouble(offset+24))
            ._m20(src.getDouble(offset+32))
            ._m21(src.getDouble(offset+40));
        }

        public void getf(Matrix3d m, int offset, FloatBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m02(src.get(offset+2))
            ._m10(src.get(offset+3))
            ._m11(src.get(offset+4))
            ._m12(src.get(offset+5))
            ._m20(src.get(offset+6))
            ._m21(src.get(offset+7))
            ._m22(src.get(offset+8));
        }

        public void getf(Matrix3d m, int offset, ByteBuffer src) {
            m._m00(src.getFloat(offset))
            ._m01(src.getFloat(offset+4))
            ._m02(src.getFloat(offset+8))
            ._m10(src.getFloat(offset+12))
            ._m11(src.getFloat(offset+16))
            ._m12(src.getFloat(offset+20))
            ._m20(src.getFloat(offset+24))
            ._m21(src.getFloat(offset+28))
            ._m22(src.getFloat(offset+32));
        }

        public void get(Matrix2f m, int offset, FloatBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m10(src.get(offset+2))
            ._m11(src.get(offset+3));
        }

        public void get(Matrix2f m, int offset, ByteBuffer src) {
            m._m00(src.getFloat(offset))
            ._m01(src.getFloat(offset+4))
            ._m10(src.getFloat(offset+8))
            ._m11(src.getFloat(offset+12));
        }

        public void get(Matrix2d m, int offset, DoubleBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m10(src.get(offset+2))
            ._m11(src.get(offset+3));
        }

        public void get(Matrix2d m, int offset, ByteBuffer src) {
            m._m00(src.getDouble(offset))
            ._m01(src.getDouble(offset+8))
            ._m10(src.getDouble(offset+16))
            ._m11(src.getDouble(offset+24));
        }

        public void getf(Matrix2d m, int offset, FloatBuffer src) {
            m._m00(src.get(offset))
            ._m01(src.get(offset+1))
            ._m10(src.get(offset+2))
            ._m11(src.get(offset+3));
        }

        public void getf(Matrix2d m, int offset, ByteBuffer src) {
            m._m00(src.getFloat(offset))
            ._m01(src.getFloat(offset+4))
            ._m10(src.getFloat(offset+8))
            ._m11(src.getFloat(offset+12));
        }

        public void get(Vector4d dst, int offset, DoubleBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
            dst.z = src.get(offset+2);
            dst.w = src.get(offset+3);
        }

        public void get(Vector4d dst, int offset, ByteBuffer src) {
            dst.x = src.getDouble(offset);
            dst.y = src.getDouble(offset+8);
            dst.z = src.getDouble(offset+16);
            dst.w = src.getDouble(offset+24);
        }

        public void get(Vector4f dst, int offset, FloatBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
            dst.z = src.get(offset+2);
            dst.w = src.get(offset+3);
        }

        public void get(Vector4f dst, int offset, ByteBuffer src) {
            dst.x = src.getFloat(offset);
            dst.y = src.getFloat(offset+4);
            dst.z = src.getFloat(offset+8);
            dst.w = src.getFloat(offset+12);
        }

        public void get(Vector4L dst, int offset, LongBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
            dst.z = src.get(offset+2);
            dst.w = src.get(offset+3);
        }

        public void get(Vector4L dst, int offset, ByteBuffer src) {
            dst.x = src.getLong(offset);
            dst.y = src.getLong(offset+8);
            dst.z = src.getLong(offset+16);
            dst.w = src.getLong(offset+24);
        }

        public void get(Vector4i dst, int offset, IntBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
            dst.z = src.get(offset+2);
            dst.w = src.get(offset+3);
        }

        public void get(Vector4i dst, int offset, ByteBuffer src) {
            dst.x = src.getInt(offset);
            dst.y = src.getInt(offset+4);
            dst.z = src.getInt(offset+8);
            dst.w = src.getInt(offset+12);
        }

        public void get(Vector3f dst, int offset, FloatBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
            dst.z = src.get(offset+2);
        }

        public void get(Vector3f dst, int offset, ByteBuffer src) {
            dst.x = src.getFloat(offset);
            dst.y = src.getFloat(offset+4);
            dst.z = src.getFloat(offset+8);
        }

        public void get(Vector3d dst, int offset, DoubleBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
            dst.z = src.get(offset+2);
        }

        public void get(Vector3d dst, int offset, ByteBuffer src) {
            dst.x = src.getDouble(offset);
            dst.y = src.getDouble(offset+8);
            dst.z = src.getDouble(offset+16);
        }

        public void get(Vector3i dst, int offset, IntBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
            dst.z = src.get(offset+2);
        }

        public void get(Vector3i dst, int offset, ByteBuffer src) {
            dst.x = src.getInt(offset);
            dst.y = src.getInt(offset+4);
            dst.z = src.getInt(offset+8);
        }

        public void get(Vector3L dst, int offset, LongBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
            dst.z = src.get(offset+2);
        }

        public void get(Vector3L dst, int offset, ByteBuffer src) {
            dst.x = src.getLong(offset);
            dst.y = src.getLong(offset+8);
            dst.z = src.getLong(offset+16);
        }

        public void get(Vector2f dst, int offset, FloatBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
        }

        public void get(Vector2f dst, int offset, ByteBuffer src) {
            dst.x = src.getFloat(offset);
            dst.y = src.getFloat(offset+4);
        }

        public void get(Vector2d dst, int offset, DoubleBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
        }

        public void get(Vector2d dst, int offset, ByteBuffer src) {
            dst.x = src.getDouble(offset);
            dst.y = src.getDouble(offset+8);
        }

        public void get(Vector2i dst, int offset, IntBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
        }

        public void get(Vector2L dst, int offset, LongBuffer src) {
            dst.x = src.get(offset);
            dst.y = src.get(offset+1);
        }

        public void get(Vector2i dst, int offset, ByteBuffer src) {
            dst.x = src.getInt(offset);
            dst.y = src.getInt(offset+4);
        }

        public void get(Vector2L dst, int offset, ByteBuffer src) {
            dst.x = src.getLong(offset);
            dst.y = src.getLong(offset+8);
        }

        public float get(Matrix4f m, int column, int row) {
            switch (column) {
            case 0:
                switch (row) {
                case 0:
                    return m.m00();
                case 1:
                    return m.m01();
                case 2:
                    return m.m02();
                case 3:
                    return m.m03();
                default:
                    break;
                }
                break;
            case 1:
                switch (row) {
                case 0:
                    return m.m10();
                case 1:
                    return m.m11();
                case 2:
                    return m.m12();
                case 3:
                    return m.m13();
                default:
                    break;
                }
                break;
            case 2:
                switch (row) {
                case 0:
                    return m.m20();
                case 1:
                    return m.m21();
                case 2:
                    return m.m22();
                case 3:
                    return m.m23();
                default:
                    break;
                }
                break;
            case 3:
                switch (row) {
                case 0:
                    return m.m30();
                case 1:
                    return m.m31();
                case 2:
                    return m.m32();
                case 3:
                    return m.m33();
                default:
                    break;
                }
                break;
            default:
                break;
            }
            throw new IllegalArgumentException();
        }

        public Matrix4f set(Matrix4f m, int column, int row, float value) {
            switch (column) {
            case 0:
                switch (row) {
                case 0:
                    return m.m00(value);
                case 1:
                    return m.m01(value);
                case 2:
                    return m.m02(value);
                case 3:
                    return m.m03(value);
                default:
                    break;
                }
                break;
            case 1:
                switch (row) {
                case 0:
                    return m.m10(value);
                case 1:
                    return m.m11(value);
                case 2:
                    return m.m12(value);
                case 3:
                    return m.m13(value);
                default:
                    break;
                }
                break;
            case 2:
                switch (row) {
                case 0:
                    return m.m20(value);
                case 1:
                    return m.m21(value);
                case 2:
                    return m.m22(value);
                case 3:
                    return m.m23(value);
                default:
                    break;
                }
                break;
            case 3:
                switch (row) {
                case 0:
                    return m.m30(value);
                case 1:
                    return m.m31(value);
                case 2:
                    return m.m32(value);
                case 3:
                    return m.m33(value);
                default:
                    break;
                }
                break;
            default:
                break;
            }
            throw new IllegalArgumentException();
        }

        public double get(Matrix4d m, int column, int row) {
            switch (column) {
            case 0:
                switch (row) {
                case 0:
                    return m.m00;
                case 1:
                    return m.m01;
                case 2:
                    return m.m02;
                case 3:
                    return m.m03;
                default:
                    break;
                }
                break;
            case 1:
                switch (row) {
                case 0:
                    return m.m10;
                case 1:
                    return m.m11;
                case 2:
                    return m.m12;
                case 3:
                    return m.m13;
                default:
                    break;
                }
                break;
            case 2:
                switch (row) {
                case 0:
                    return m.m20;
                case 1:
                    return m.m21;
                case 2:
                    return m.m22;
                case 3:
                    return m.m23;
                default:
                    break;
                }
                break;
            case 3:
                switch (row) {
                case 0:
                    return m.m30;
                case 1:
                    return m.m31;
                case 2:
                    return m.m32;
                case 3:
                    return m.m33;
                default:
                    break;
                }
                break;
            default:
                break;
            }
            throw new IllegalArgumentException();
        }

        public Matrix4d set(Matrix4d m, int column, int row, double value) {
            switch (column) {
            case 0:
                switch (row) {
                case 0:
                    return m.m00(value);
                case 1:
                    return m.m01(value);
                case 2:
                    return m.m02(value);
                case 3:
                    return m.m03(value);
                default:
                    break;
                }
                break;
            case 1:
                switch (row) {
                case 0:
                    return m.m10(value);
                case 1:
                    return m.m11(value);
                case 2:
                    return m.m12(value);
                case 3:
                    return m.m13(value);
                default:
                    break;
                }
                break;
            case 2:
                switch (row) {
                case 0:
                    return m.m20(value);
                case 1:
                    return m.m21(value);
                case 2:
                    return m.m22(value);
                case 3:
                    return m.m23(value);
                default:
                    break;
                }
                break;
            case 3:
                switch (row) {
                case 0:
                    return m.m30(value);
                case 1:
                    return m.m31(value);
                case 2:
                    return m.m32(value);
                case 3:
                    return m.m33(value);
                default:
                    break;
                }
                break;
            default:
                break;
            }
            throw new IllegalArgumentException();
        }
        
        public float get(Matrix3f m, int column, int row) {
            switch (column) {
            case 0:
                switch (row) {
                case 0:
                    return m.m00;
                case 1:
                    return m.m01;
                case 2:
                    return m.m02;
                default:
                    break;
                }
                break;
            case 1:
                switch (row) {
                case 0:
                    return m.m10;
                case 1:
                    return m.m11;
                case 2:
                    return m.m12;
                default:
                    break;
                }
                break;
            case 2:
                switch (row) {
                case 0:
                    return m.m20;
                case 1:
                    return m.m21;
                case 2:
                    return m.m22;
                default:
                    break;
                }
                break;
            default:
                break;
            }
            throw new IllegalArgumentException();
        }

        public Matrix3f set(Matrix3f m, int column, int row, float value) {
            switch (column) {
            case 0:
                switch (row) {
                case 0:
                    return m.m00(value);
                case 1:
                    return m.m01(value);
                case 2:
                    return m.m02(value);
                default:
                    break;
                }
                break;
            case 1:
                switch (row) {
                case 0:
                    return m.m10(value);
                case 1:
                    return m.m11(value);
                case 2:
                    return m.m12(value);
                default:
                    break;
                }
                break;
            case 2:
                switch (row) {
                case 0:
                    return m.m20(value);
                case 1:
                    return m.m21(value);
                case 2:
                    return m.m22(value);
                default:
                    break;
                }
                break;
            default:
                break;
            }
            throw new IllegalArgumentException();
        }

        public double get(Matrix3d m, int column, int row) {
            switch (column) {
            case 0:
                switch (row) {
                case 0:
                    return m.m00;
                case 1:
                    return m.m01;
                case 2:
                    return m.m02;
                default:
                    break;
                }
                break;
            case 1:
                switch (row) {
                case 0:
                    return m.m10;
                case 1:
                    return m.m11;
                case 2:
                    return m.m12;
                default:
                    break;
                }
                break;
            case 2:
                switch (row) {
                case 0:
                    return m.m20;
                case 1:
                    return m.m21;
                case 2:
                    return m.m22;
                default:
                    break;
                }
                break;
            default:
                break;
            }
            throw new IllegalArgumentException();
        }

        public Matrix3d set(Matrix3d m, int column, int row, double value) {
            switch (column) {
            case 0:
                switch (row) {
                case 0:
                    return m.m00(value);
                case 1:
                    return m.m01(value);
                case 2:
                    return m.m02(value);
                default:
                    break;
                }
                break;
            case 1:
                switch (row) {
                case 0:
                    return m.m10(value);
                case 1:
                    return m.m11(value);
                case 2:
                    return m.m12(value);
                default:
                    break;
                }
                break;
            case 2:
                switch (row) {
                case 0:
                    return m.m20(value);
                case 1:
                    return m.m21(value);
                case 2:
                    return m.m22(value);
                default:
                    break;
                }
                break;
            default:
                break;
            }
            throw new IllegalArgumentException();
        }

        public Vector4f getColumn(Matrix4f m, int column, Vector4f dest) {
            switch (column) {
            case 0:
                return dest.set(m.m00(), m.m01(), m.m02(), m.m03());
            case 1:
                return dest.set(m.m10(), m.m11(), m.m12(), m.m13());
            case 2:
                return dest.set(m.m20(), m.m21(), m.m22(), m.m23());
            case 3:
                return dest.set(m.m30(), m.m31(), m.m32(), m.m33());
            default:
                throw new IndexOutOfBoundsException();
            }
        }

        public Matrix4f setColumn(Vector4f v, int column, Matrix4f dest) {
            switch (column) {
            case 0:
                return dest._m00(v.x)._m01(v.y)._m02(v.z)._m03(v.w);
            case 1:
                return dest._m10(v.x)._m11(v.y)._m12(v.z)._m13(v.w);
            case 2:
                return dest._m20(v.x)._m21(v.y)._m22(v.z)._m23(v.w);
            case 3:
                return dest._m30(v.x)._m31(v.y)._m32(v.z)._m33(v.w);
            default:
                throw new IndexOutOfBoundsException();
            }
        }

        public Matrix4f setColumn(Vector4fc v, int column, Matrix4f dest) {
            switch (column) {
            case 0:
                return dest._m00(v.x())._m01(v.y())._m02(v.z())._m03(v.w());
            case 1:
                return dest._m10(v.x())._m11(v.y())._m12(v.z())._m13(v.w());
            case 2:
                return dest._m20(v.x())._m21(v.y())._m22(v.z())._m23(v.w());
            case 3:
                return dest._m30(v.x())._m31(v.y())._m32(v.z())._m33(v.w());
            default:
                throw new IndexOutOfBoundsException();
            }
        }

        public void copy(Matrix4fc src, Matrix4f dest) {
            dest._m00(src.m00()).
            _m01(src.m01()).
            _m02(src.m02()).
            _m03(src.m03()).
            _m10(src.m10()).
            _m11(src.m11()).
            _m12(src.m12()).
            _m13(src.m13()).
            _m20(src.m20()).
            _m21(src.m21()).
            _m22(src.m22()).
            _m23(src.m23()).
            _m30(src.m30()).
            _m31(src.m31()).
            _m32(src.m32()).
            _m33(src.m33());
        }

        public void copy(Matrix3fc src, Matrix4f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(src.m02())
            ._m03(0.0f)
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(src.m12())
            ._m13(0.0f)
            ._m20(src.m20())
            ._m21(src.m21())
            ._m22(src.m22())
            ._m23(0.0f)
            ._m30(0.0f)
            ._m31(0.0f)
            ._m32(0.0f)
            ._m33(1.0f);
        }

        public void copy(Matrix4fc src, Matrix3f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(src.m02())
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(src.m12())
            ._m20(src.m20())
            ._m21(src.m21())
            ._m22(src.m22());
        }

        public void copy(Matrix3fc src, Matrix4x3f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(src.m02())
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(src.m12())
            ._m20(src.m20())
            ._m21(src.m21())
            ._m22(src.m22())
            ._m30(0.0f)
            ._m31(0.0f)
            ._m32(0.0f);
        }

        public void copy(Matrix3x2fc src, Matrix3x2f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m10(src.m10())
            ._m11(src.m11())
            ._m20(src.m20())
            ._m21(src.m21());
        }

        public void copy(Matrix3x2dc src, Matrix3x2d dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m10(src.m10())
            ._m11(src.m11())
            ._m20(src.m20())
            ._m21(src.m21());
        }

        public void copy(Matrix2fc src, Matrix2f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m10(src.m10())
            ._m11(src.m11());
        }

        public void copy(Matrix2dc src, Matrix2d dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m10(src.m10())
            ._m11(src.m11());
        }

        public void copy(Matrix2fc src, Matrix3f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(0.0f)
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(0.0f)
            ._m20(0.0f)
            ._m21(0.0f)
            ._m22(1.0f);
        }

        public void copy(Matrix3fc src, Matrix2f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m10(src.m10())
            ._m11(src.m11());
        }

        public void copy(Matrix2fc src, Matrix3x2f dest) {
            // set(Matrix2fc) is documented to leave the translation unchanged; it used to zero it. (Constructors
            // start from zero, so new matrices still get an identity translation.)
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m10(src.m10())
            ._m11(src.m11());
        }

        public void copy(Matrix3x2fc src, Matrix2f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m10(src.m10())
            ._m11(src.m11());
        }

        public void copy(Matrix2dc src, Matrix3d dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(0.0)
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(0.0)
            ._m20(0.0)
            ._m21(0.0)
            ._m22(1.0);
        }

        public void copy(Matrix3dc src, Matrix2d dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m10(src.m10())
            ._m11(src.m11());
        }

        public void copy(Matrix2dc src, Matrix3x2d dest) {
            // set(Matrix2dc) is documented to leave the translation unchanged; it used to zero it. (Constructors
            // start from zero, so new matrices still get an identity translation.)
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m10(src.m10())
            ._m11(src.m11());
        }

        public void copy(Matrix3x2dc src, Matrix2d dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m10(src.m10())
            ._m11(src.m11());
        }

        public void copy3x3(Matrix4fc src, Matrix4f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(src.m02())
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(src.m12())
            ._m20(src.m20())
            ._m21(src.m21())
            ._m22(src.m22());
        }

        public void copy3x3(Matrix4x3fc src, Matrix4x3f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(src.m02())
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(src.m12())
            ._m20(src.m20())
            ._m21(src.m21())
            ._m22(src.m22());
        }

        public void copy3x3(Matrix3fc src, Matrix4x3f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(src.m02())
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(src.m12())
            ._m20(src.m20())
            ._m21(src.m21())
            ._m22(src.m22());
        }

        public void copy3x3(Matrix3fc src, Matrix4f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(src.m02())
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(src.m12())
            ._m20(src.m20())
            ._m21(src.m21())
            ._m22(src.m22());
        }

        public void copy4x3(Matrix4x3fc src, Matrix4f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(src.m02())
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(src.m12())
            ._m20(src.m20())
            ._m21(src.m21())
            ._m22(src.m22())
            ._m30(src.m30())
            ._m31(src.m31())
            ._m32(src.m32());
        }

        public void copy4x3(Matrix4fc src, Matrix4f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(src.m02())
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(src.m12())
            ._m20(src.m20())
            ._m21(src.m21())
            ._m22(src.m22())
            ._m30(src.m30())
            ._m31(src.m31())
            ._m32(src.m32());
        }

        public void copy(Matrix4fc src, Matrix4x3f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(src.m02())
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(src.m12())
            ._m20(src.m20())
            ._m21(src.m21())
            ._m22(src.m22())
            ._m30(src.m30())
            ._m31(src.m31())
            ._m32(src.m32());
        }

        public void copy(Matrix4x3fc src, Matrix4f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(src.m02())
            ._m03(0.0f)
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(src.m12())
            ._m13(0.0f)
            ._m20(src.m20())
            ._m21(src.m21())
            ._m22(src.m22())
            ._m23(0.0f)
            ._m30(src.m30())
            ._m31(src.m31())
            ._m32(src.m32())
            ._m33(1.0f);
        }

        public void copy(Matrix4x3fc src, Matrix4x3f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(src.m02())
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(src.m12())
            ._m20(src.m20())
            ._m21(src.m21())
            ._m22(src.m22())
            ._m30(src.m30())
            ._m31(src.m31())
            ._m32(src.m32());
        }

        public void copy(Matrix3fc src, Matrix3f dest) {
            dest._m00(src.m00())
            ._m01(src.m01())
            ._m02(src.m02())
            ._m10(src.m10())
            ._m11(src.m11())
            ._m12(src.m12())
            ._m20(src.m20())
            ._m21(src.m21())
            ._m22(src.m22());
        }

        public void copy(float[] arr, int off, Matrix4f dest) {
            dest._m00(arr[off+0])
            ._m01(arr[off+1])
            ._m02(arr[off+2])
            ._m03(arr[off+3])
            ._m10(arr[off+4])
            ._m11(arr[off+5])
            ._m12(arr[off+6])
            ._m13(arr[off+7])
            ._m20(arr[off+8])
            ._m21(arr[off+9])
            ._m22(arr[off+10])
            ._m23(arr[off+11])
            ._m30(arr[off+12])
            ._m31(arr[off+13])
            ._m32(arr[off+14])
            ._m33(arr[off+15]);
        }

        public void copyTransposed(float[] arr, int off, Matrix4f dest) {
            dest._m00(arr[off+0])
            ._m10(arr[off+1])
            ._m20(arr[off+2])
            ._m30(arr[off+3])
            ._m01(arr[off+4])
            ._m11(arr[off+5])
            ._m21(arr[off+6])
            ._m31(arr[off+7])
            ._m02(arr[off+8])
            ._m12(arr[off+9])
            ._m22(arr[off+10])
            ._m32(arr[off+11])
            ._m03(arr[off+12])
            ._m13(arr[off+13])
            ._m23(arr[off+14])
            ._m33(arr[off+15]);
        }

        public void copy(float[] arr, int off, Matrix3f dest) {
            dest._m00(arr[off+0])
            ._m01(arr[off+1])
            ._m02(arr[off+2])
            ._m10(arr[off+3])
            ._m11(arr[off+4])
            ._m12(arr[off+5])
            ._m20(arr[off+6])
            ._m21(arr[off+7])
            ._m22(arr[off+8]);
        }

        public void copy(float[] arr, int off, Matrix4x3f dest) {
            dest._m00(arr[off+0])
            ._m01(arr[off+1])
            ._m02(arr[off+2])
            ._m10(arr[off+3])
            ._m11(arr[off+4])
            ._m12(arr[off+5])
            ._m20(arr[off+6])
            ._m21(arr[off+7])
            ._m22(arr[off+8])
            ._m30(arr[off+9])
            ._m31(arr[off+10])
            ._m32(arr[off+11]);
        }

        public void copy(float[] arr, int off, Matrix3x2f dest) {
            dest._m00(arr[off+0])
            ._m01(arr[off+1])
            ._m10(arr[off+2])
            ._m11(arr[off+3])
            ._m20(arr[off+4])
            ._m21(arr[off+5]);
        }

        public void copy(double[] arr, int off, Matrix3x2d dest) {
            dest._m00(arr[off+0])
            ._m01(arr[off+1])
            ._m10(arr[off+2])
            ._m11(arr[off+3])
            ._m20(arr[off+4])
            ._m21(arr[off+5]);
        }

        public void copy(float[] arr, int off, Matrix3x2d dest) {
            dest._m00(arr[off+0])
            ._m01(arr[off+1])
            ._m10(arr[off+2])
            ._m11(arr[off+3])
            ._m20(arr[off+4])
            ._m21(arr[off+5]);
        }

        public void copy(float[] arr, int off, Matrix2f dest) {
            dest._m00(arr[off+0])
            ._m01(arr[off+1])
            ._m10(arr[off+2])
            ._m11(arr[off+3]);
        }

        public void copy(double[] arr, int off, Matrix2d dest) {
            dest._m00(arr[off+0])
            ._m01(arr[off+1])
            ._m10(arr[off+2])
            ._m11(arr[off+3]);
        }

        public void copy(Matrix4fc src, float[] dest, int off) {
            dest[off+0]  = src.m00();
            dest[off+1]  = src.m01();
            dest[off+2]  = src.m02();
            dest[off+3]  = src.m03();
            dest[off+4]  = src.m10();
            dest[off+5]  = src.m11();
            dest[off+6]  = src.m12();
            dest[off+7]  = src.m13();
            dest[off+8]  = src.m20();
            dest[off+9]  = src.m21();
            dest[off+10] = src.m22();
            dest[off+11] = src.m23();
            dest[off+12] = src.m30();
            dest[off+13] = src.m31();
            dest[off+14] = src.m32();
            dest[off+15] = src.m33();
        }

        public void copy(Matrix3fc src, float[] dest, int off) {
            dest[off+0] = src.m00();
            dest[off+1] = src.m01();
            dest[off+2] = src.m02();
            dest[off+3] = src.m10();
            dest[off+4] = src.m11();
            dest[off+5] = src.m12();
            dest[off+6] = src.m20();
            dest[off+7] = src.m21();
            dest[off+8] = src.m22();
        }

        public void copy(Matrix4x3fc src, float[] dest, int off) {
            dest[off+0]  = src.m00();
            dest[off+1]  = src.m01();
            dest[off+2]  = src.m02();
            dest[off+3]  = src.m10();
            dest[off+4]  = src.m11();
            dest[off+5]  = src.m12();
            dest[off+6]  = src.m20();
            dest[off+7]  = src.m21();
            dest[off+8]  = src.m22();
            dest[off+9]  = src.m30();
            dest[off+10] = src.m31();
            dest[off+11] = src.m32();
        }

        public void copy(Matrix3x2fc src, float[] dest, int off) {
            dest[off+0] = src.m00();
            dest[off+1] = src.m01();
            dest[off+2] = src.m10();
            dest[off+3] = src.m11();
            dest[off+4] = src.m20();
            dest[off+5] = src.m21();
        }

        public void copy(Matrix3x2dc src, double[] dest, int off) {
            dest[off+0] = src.m00();
            dest[off+1] = src.m01();
            dest[off+2] = src.m10();
            dest[off+3] = src.m11();
            dest[off+4] = src.m20();
            dest[off+5] = src.m21();
        }

        public void copy(Matrix2fc src, float[] dest, int off) {
            dest[off+0] = src.m00();
            dest[off+1] = src.m01();
            dest[off+2] = src.m10();
            dest[off+3] = src.m11();
        }

        public void copy(Matrix2dc src, double[] dest, int off) {
            dest[off+0] = src.m00();
            dest[off+1] = src.m01();
            dest[off+2] = src.m10();
            dest[off+3] = src.m11();
        }

        public void copy4x4(Matrix4x3fc src, float[] dest, int off) {
            dest[off+0]  = src.m00();
            dest[off+1]  = src.m01();
            dest[off+2]  = src.m02();
            dest[off+3]  = 0.0f;
            dest[off+4]  = src.m10();
            dest[off+5]  = src.m11();
            dest[off+6]  = src.m12();
            dest[off+7]  = 0.0f;
            dest[off+8]  = src.m20();
            dest[off+9]  = src.m21();
            dest[off+10] = src.m22();
            dest[off+11] = 0.0f;
            dest[off+12] = src.m30();
            dest[off+13] = src.m31();
            dest[off+14] = src.m32();
            dest[off+15] = 1.0f;
        }

        public void copy4x4(Matrix4x3dc src, float[] dest, int off) {
            dest[off+0]  = (float) src.m00();
            dest[off+1]  = (float) src.m01();
            dest[off+2]  = (float) src.m02();
            dest[off+3]  = 0.0f;
            dest[off+4]  = (float) src.m10();
            dest[off+5]  = (float) src.m11();
            dest[off+6]  = (float) src.m12();
            dest[off+7]  = 0.0f;
            dest[off+8]  = (float) src.m20();
            dest[off+9]  = (float) src.m21();
            dest[off+10] = (float) src.m22();
            dest[off+11] = 0.0f;
            dest[off+12] = (float) src.m30();
            dest[off+13] = (float) src.m31();
            dest[off+14] = (float) src.m32();
            dest[off+15] = 1.0f;
        }

        public void copy4x4(Matrix4x3dc src, double[] dest, int off) {
            dest[off+0]  = src.m00();
            dest[off+1]  = src.m01();
            dest[off+2]  = src.m02();
            dest[off+3]  = 0.0;
            dest[off+4]  = src.m10();
            dest[off+5]  = src.m11();
            dest[off+6]  = src.m12();
            dest[off+7]  = 0.0;
            dest[off+8]  = src.m20();
            dest[off+9]  = src.m21();
            dest[off+10] = src.m22();
            dest[off+11] = 0.0;
            dest[off+12] = src.m30();
            dest[off+13] = src.m31();
            dest[off+14] = src.m32();
            dest[off+15] = 1.0;
        }

        public void copy3x3(Matrix3x2fc src, float[] dest, int off) {
            dest[off+0] = src.m00();
            dest[off+1] = src.m01();
            dest[off+2] = 0.0f;
            dest[off+3] = src.m10();
            dest[off+4] = src.m11();
            dest[off+5] = 0.0f;
            dest[off+6] = src.m20();
            dest[off+7] = src.m21();
            dest[off+8] = 1.0f;
        }

        public void copy3x3(Matrix3x2dc src, double[] dest, int off) {
            dest[off+0] = src.m00();
            dest[off+1] = src.m01();
            dest[off+2] = 0.0;
            dest[off+3] = src.m10();
            dest[off+4] = src.m11();
            dest[off+5] = 0.0;
            dest[off+6] = src.m20();
            dest[off+7] = src.m21();
            dest[off+8] = 1.0;
        }

        public void copy4x4(Matrix3x2fc src, float[] dest, int off) {
            dest[off+0]  = src.m00();
            dest[off+1]  = src.m01();
            dest[off+2]  = 0.0f;
            dest[off+3]  = 0.0f;
            dest[off+4]  = src.m10();
            dest[off+5]  = src.m11();
            dest[off+6]  = 0.0f;
            dest[off+7]  = 0.0f;
            dest[off+8]  = 0.0f;
            dest[off+9]  = 0.0f;
            dest[off+10] = 1.0f;
            dest[off+11] = 0.0f;
            dest[off+12] = src.m20();
            dest[off+13] = src.m21();
            dest[off+14] = 0.0f;
            dest[off+15] = 1.0f;
        }

        public void copy4x4(Matrix3x2dc src, double[] dest, int off) {
            dest[off+0]  = src.m00();
            dest[off+1]  = src.m01();
            dest[off+2]  = 0.0;
            dest[off+3]  = 0.0;
            dest[off+4]  = src.m10();
            dest[off+5]  = src.m11();
            dest[off+6]  = 0.0;
            dest[off+7]  = 0.0;
            dest[off+8]  = 0.0;
            dest[off+9]  = 0.0;
            dest[off+10] = 1.0;
            dest[off+11] = 0.0;
            dest[off+12] = src.m20();
            dest[off+13] = src.m21();
            dest[off+14] = 0.0;
            dest[off+15] = 1.0;
        }

        public void identity(Matrix4f dest) {
            dest._m00(1.0f)
            ._m01(0.0f)
            ._m02(0.0f)
            ._m03(0.0f)
            ._m10(0.0f)
            ._m11(1.0f)
            ._m12(0.0f)
            ._m13(0.0f)
            ._m20(0.0f)
            ._m21(0.0f)
            ._m22(1.0f)
            ._m23(0.0f)
            ._m30(0.0f)
            ._m31(0.0f)
            ._m32(0.0f)
            ._m33(1.0f);
        }

        public void identity(Matrix4x3f dest) {
            dest._m00(1.0f)
            ._m01(0.0f)
            ._m02(0.0f)
            ._m10(0.0f)
            ._m11(1.0f)
            ._m12(0.0f)
            ._m20(0.0f)
            ._m21(0.0f)
            ._m22(1.0f)
            ._m30(0.0f)
            ._m31(0.0f)
            ._m32(0.0f);
        }

        public void identity(Matrix3f dest) {
            dest._m00(1.0f)
            ._m01(0.0f)
            ._m02(0.0f)
            ._m10(0.0f)
            ._m11(1.0f)
            ._m12(0.0f)
            ._m20(0.0f)
            ._m21(0.0f)
            ._m22(1.0f);
        }

        public void identity(Matrix3x2f dest) {
            dest._m00(1.0f)
            ._m01(0.0f)
            ._m10(0.0f)
            ._m11(1.0f)
            ._m20(0.0f)
            ._m21(0.0f);
        }

        public void identity(Matrix3x2d dest) {
            dest._m00(1.0)
            ._m01(0.0)
            ._m10(0.0)
            ._m11(1.0)
            ._m20(0.0)
            ._m21(0.0);
        }

        public void identity(Matrix2f dest) {
            dest._m00(1.0f)
            ._m01(0.0f)
            ._m10(0.0f)
            ._m11(1.0f);
        }

        public void swap(Matrix4f m1, Matrix4f m2) {
            float tmp;
            tmp = m1.m00(); m1._m00(m2.m00()); m2._m00(tmp);
            tmp = m1.m01(); m1._m01(m2.m01()); m2._m01(tmp);
            tmp = m1.m02(); m1._m02(m2.m02()); m2._m02(tmp);
            tmp = m1.m03(); m1._m03(m2.m03()); m2._m03(tmp);
            tmp = m1.m10(); m1._m10(m2.m10()); m2._m10(tmp);
            tmp = m1.m11(); m1._m11(m2.m11()); m2._m11(tmp);
            tmp = m1.m12(); m1._m12(m2.m12()); m2._m12(tmp);
            tmp = m1.m13(); m1._m13(m2.m13()); m2._m13(tmp);
            tmp = m1.m20(); m1._m20(m2.m20()); m2._m20(tmp);
            tmp = m1.m21(); m1._m21(m2.m21()); m2._m21(tmp);
            tmp = m1.m22(); m1._m22(m2.m22()); m2._m22(tmp);
            tmp = m1.m23(); m1._m23(m2.m23()); m2._m23(tmp);
            tmp = m1.m30(); m1._m30(m2.m30()); m2._m30(tmp);
            tmp = m1.m31(); m1._m31(m2.m31()); m2._m31(tmp);
            tmp = m1.m32(); m1._m32(m2.m32()); m2._m32(tmp);
            tmp = m1.m33(); m1._m33(m2.m33()); m2._m33(tmp);
        }

        public void swap(Matrix4x3f m1, Matrix4x3f m2) {
            float tmp;
            tmp = m1.m00(); m1._m00(m2.m00()); m2._m00(tmp);
            tmp = m1.m01(); m1._m01(m2.m01()); m2._m01(tmp);
            tmp = m1.m02(); m1._m02(m2.m02()); m2._m02(tmp);
            tmp = m1.m10(); m1._m10(m2.m10()); m2._m10(tmp);
            tmp = m1.m11(); m1._m11(m2.m11()); m2._m11(tmp);
            tmp = m1.m12(); m1._m12(m2.m12()); m2._m12(tmp);
            tmp = m1.m20(); m1._m20(m2.m20()); m2._m20(tmp);
            tmp = m1.m21(); m1._m21(m2.m21()); m2._m21(tmp);
            tmp = m1.m22(); m1._m22(m2.m22()); m2._m22(tmp);
            tmp = m1.m30(); m1._m30(m2.m30()); m2._m30(tmp);
            tmp = m1.m31(); m1._m31(m2.m31()); m2._m31(tmp);
            tmp = m1.m32(); m1._m32(m2.m32()); m2._m32(tmp);
        }
        
        public void swap(Matrix3f m1, Matrix3f m2) {
            float tmp;
            tmp = m1.m00(); m1._m00(m2.m00()); m2._m00(tmp);
            tmp = m1.m01(); m1._m01(m2.m01()); m2._m01(tmp);
            tmp = m1.m02(); m1._m02(m2.m02()); m2._m02(tmp);
            tmp = m1.m10(); m1._m10(m2.m10()); m2._m10(tmp);
            tmp = m1.m11(); m1._m11(m2.m11()); m2._m11(tmp);
            tmp = m1.m12(); m1._m12(m2.m12()); m2._m12(tmp);
            tmp = m1.m20(); m1._m20(m2.m20()); m2._m20(tmp);
            tmp = m1.m21(); m1._m21(m2.m21()); m2._m21(tmp);
            tmp = m1.m22(); m1._m22(m2.m22()); m2._m22(tmp);
        }

        public void swap(Matrix2f m1, Matrix2f m2) {
            float tmp;
            tmp = m1.m00(); m1._m00(m2.m00()); m2._m00(tmp);
            tmp = m1.m01(); m1._m00(m2.m01()); m2._m01(tmp);
            tmp = m1.m10(); m1._m00(m2.m10()); m2._m10(tmp);
            tmp = m1.m11(); m1._m00(m2.m11()); m2._m11(tmp);
        }

        public void swap(Matrix2d m1, Matrix2d m2) {
            double tmp;
            tmp = m1.m00(); m1._m00(m2.m00()); m2._m00(tmp);
            tmp = m1.m01(); m1._m00(m2.m01()); m2._m01(tmp);
            tmp = m1.m10(); m1._m00(m2.m10()); m2._m10(tmp);
            tmp = m1.m11(); m1._m00(m2.m11()); m2._m11(tmp);
        }

        public void zero(Matrix4f dest) {
            dest._m00(0.0f)
            ._m01(0.0f)
            ._m02(0.0f)
            ._m03(0.0f)
            ._m10(0.0f)
            ._m11(0.0f)
            ._m12(0.0f)
            ._m13(0.0f)
            ._m20(0.0f)
            ._m21(0.0f)
            ._m22(0.0f)
            ._m23(0.0f)
            ._m30(0.0f)
            ._m31(0.0f)
            ._m32(0.0f)
            ._m33(0.0f);
        }

        public void zero(Matrix4x3f dest) {
            dest._m00(0.0f)
            ._m01(0.0f)
            ._m02(0.0f)
            ._m10(0.0f)
            ._m11(0.0f)
            ._m12(0.0f)
            ._m20(0.0f)
            ._m21(0.0f)
            ._m22(0.0f)
            ._m30(0.0f)
            ._m31(0.0f)
            ._m32(0.0f);
        }

        public void zero(Matrix3f dest) {
            dest._m00(0.0f)
            ._m01(0.0f)
            ._m02(0.0f)
            ._m10(0.0f)
            ._m11(0.0f)
            ._m12(0.0f)
            ._m20(0.0f)
            ._m21(0.0f)
            ._m22(0.0f);
        }

        public void zero(Matrix3x2f dest) {
            dest._m00(0.0f)
            ._m01(0.0f)
            ._m10(0.0f)
            ._m11(0.0f)
            ._m20(0.0f)
            ._m21(0.0f);
        }

        public void zero(Matrix3x2d dest) {
            dest._m00(0.0)
            ._m01(0.0)
            ._m10(0.0)
            ._m11(0.0)
            ._m20(0.0)
            ._m21(0.0);
        }

        public void zero(Matrix2f dest) {
            dest._m00(0.0f)
            ._m01(0.0f)
            ._m10(0.0f)
            ._m11(0.0f);
        }

        public void zero(Matrix2d dest) {
            dest._m00(0.0)
            ._m01(0.0)
            ._m10(0.0)
            ._m11(0.0);
        }

        public void putMatrix3f(Quaternionf q, int position, ByteBuffer dest) {
            float w2 = q.w * q.w;
            float x2 = q.x * q.x;
            float y2 = q.y * q.y;
            float z2 = q.z * q.z;
            float zw = q.z * q.w;
            float xy = q.x * q.y;
            float xz = q.x * q.z;
            float yw = q.y * q.w;
            float yz = q.y * q.z;
            float xw = q.x * q.w;
            dest.putFloat(position, w2 + x2 - z2 - y2)
            .putFloat(position + 4, xy + zw + zw + xy)
            .putFloat(position + 8, xz - yw + xz - yw)
            .putFloat(position + 12, -zw + xy - zw + xy)
            .putFloat(position + 16, y2 - z2 + w2 - x2)
            .putFloat(position + 20, yz + yz + xw + xw)
            .putFloat(position + 24, yw + xz + xz + yw)
            .putFloat(position + 28, yz + yz - xw - xw)
            .putFloat(position + 32, z2 - y2 - x2 + w2);
        }

        public void putMatrix3f(Quaternionf q, int position, FloatBuffer dest) {
            float w2 = q.w * q.w;
            float x2 = q.x * q.x;
            float y2 = q.y * q.y;
            float z2 = q.z * q.z;
            float zw = q.z * q.w;
            float xy = q.x * q.y;
            float xz = q.x * q.z;
            float yw = q.y * q.w;
            float yz = q.y * q.z;
            float xw = q.x * q.w;
            dest.put(position, w2 + x2 - z2 - y2)
            .put(position + 1, xy + zw + zw + xy)
            .put(position + 2, xz - yw + xz - yw)
            .put(position + 3, -zw + xy - zw + xy)
            .put(position + 4, y2 - z2 + w2 - x2)
            .put(position + 5, yz + yz + xw + xw)
            .put(position + 6, yw + xz + xz + yw)
            .put(position + 7, yz + yz - xw - xw)
            .put(position + 8, z2 - y2 - x2 + w2);
        }

        public void putMatrix4f(Quaternionf q, int position, ByteBuffer dest) {
            float w2 = q.w * q.w;
            float x2 = q.x * q.x;
            float y2 = q.y * q.y;
            float z2 = q.z * q.z;
            float zw = q.z * q.w;
            float xy = q.x * q.y;
            float xz = q.x * q.z;
            float yw = q.y * q.w;
            float yz = q.y * q.z;
            float xw = q.x * q.w;
            dest.putFloat(position, w2 + x2 - z2 - y2)
            .putFloat(position + 4, xy + zw + zw + xy)
            .putFloat(position + 8, xz - yw + xz - yw)
            .putFloat(position + 12, 0.0f)
            .putFloat(position + 16, -zw + xy - zw + xy)
            .putFloat(position + 20, y2 - z2 + w2 - x2)
            .putFloat(position + 24, yz + yz + xw + xw)
            .putFloat(position + 28, 0.0f)
            .putFloat(position + 32, yw + xz + xz + yw)
            .putFloat(position + 36, yz + yz - xw - xw)
            .putFloat(position + 40, z2 - y2 - x2 + w2)
            .putFloat(position + 44, 0.0f)
            .putLong(position + 48, 0L)
            // two floats, not one long: putLong follows the buffer's byte order, so the long constant wrote
            // m32 = 1, m33 = 0 into big-endian buffers (the default for ByteBuffer.allocate)
            .putFloat(position + 56, 0.0f)
            .putFloat(position + 60, 1.0f);
        }

        public void putMatrix4f(Quaternionf q, int position, FloatBuffer dest) {
            float w2 = q.w * q.w;
            float x2 = q.x * q.x;
            float y2 = q.y * q.y;
            float z2 = q.z * q.z;
            float zw = q.z * q.w;
            float xy = q.x * q.y;
            float xz = q.x * q.z;
            float yw = q.y * q.w;
            float yz = q.y * q.z;
            float xw = q.x * q.w;
            dest.put(position, w2 + x2 - z2 - y2)
            .put(position + 1, xy + zw + zw + xy)
            .put(position + 2, xz - yw + xz - yw)
            .put(position + 3, 0.0f)
            .put(position + 4, -zw + xy - zw + xy)
            .put(position + 5, y2 - z2 + w2 - x2)
            .put(position + 6, yz + yz + xw + xw)
            .put(position + 7, 0.0f)
            .put(position + 8, yw + xz + xz + yw)
            .put(position + 9, yz + yz - xw - xw)
            .put(position + 10, z2 - y2 - x2 + w2)
            .put(position + 11, 0.0f)
            .put(position + 12, 0.0f)
            .put(position + 13, 0.0f)
            .put(position + 14, 0.0f)
            .put(position + 15, 1.0f);
        }

        public void putMatrix4x3f(Quaternionf q, int position, ByteBuffer dest) {
            float w2 = q.w * q.w;
            float x2 = q.x * q.x;
            float y2 = q.y * q.y;
            float z2 = q.z * q.z;
            float zw = q.z * q.w;
            float xy = q.x * q.y;
            float xz = q.x * q.z;
            float yw = q.y * q.w;
            float yz = q.y * q.z;
            float xw = q.x * q.w;
            dest.putFloat(position, w2 + x2 - z2 - y2)
            .putFloat(position + 4, xy + zw + zw + xy)
            .putFloat(position + 8, xz - yw + xz - yw)
            .putFloat(position + 12, -zw + xy - zw + xy)
            .putFloat(position + 16, y2 - z2 + w2 - x2)
            .putFloat(position + 20, yz + yz + xw + xw)
            .putFloat(position + 24, yw + xz + xz + yw)
            .putFloat(position + 28, yz + yz - xw - xw)
            .putFloat(position + 32, z2 - y2 - x2 + w2)
            .putLong(position + 36, 0L)
            .putFloat(position + 44, 0.0f);
        }

        public void putMatrix4x3f(Quaternionf q, int position, FloatBuffer dest) {
            float w2 = q.w * q.w;
            float x2 = q.x * q.x;
            float y2 = q.y * q.y;
            float z2 = q.z * q.z;
            float zw = q.z * q.w;
            float xy = q.x * q.y;
            float xz = q.x * q.z;
            float yw = q.y * q.w;
            float yz = q.y * q.z;
            float xw = q.x * q.w;
            dest.put(position, w2 + x2 - z2 - y2)
            .put(position + 1, xy + zw + zw + xy)
            .put(position + 2, xz - yw + xz - yw)
            .put(position + 3, -zw + xy - zw + xy)
            .put(position + 4, y2 - z2 + w2 - x2)
            .put(position + 5, yz + yz + xw + xw)
            .put(position + 6, yw + xz + xz + yw)
            .put(position + 7, yz + yz - xw - xw)
            .put(position + 8, z2 - y2 - x2 + w2)
            .put(position + 9, 0.0f)
            .put(position + 10, 0.0f)
            .put(position + 11, 0.0f);
        }
    }

//#ifdef __HAS_FFM__
    public static class MemUtilFFM extends MemUtilNIO {
        private static final ValueLayout.OfFloat JAVA_FLOAT = ValueLayout.JAVA_FLOAT_UNALIGNED;
        private static final ValueLayout.OfDouble JAVA_DOUBLE = ValueLayout.JAVA_DOUBLE_UNALIGNED;
        private static final ValueLayout.OfInt JAVA_INT = ValueLayout.JAVA_INT_UNALIGNED;
        private static final ValueLayout.OfLong JAVA_LONG = ValueLayout.JAVA_LONG_UNALIGNED;

        /**
         * A bounds-checked segment over elements {@code [0, limit())} of a direct buffer. Callers take
         * {@code asSlice(offset, size)} of it: that one range check (throwing {@link IndexOutOfBoundsException}, like
         * NIO) yields a slice of constant size, so the JIT can drop the per-store checks and the transfer costs the
         * same as the Unsafe path for bulk use. The absolute
         * offsets used by the MemUtil API map straight to segment byte offsets. Every access through it is
         * checked against the buffer's limit (throwing {@link IndexOutOfBoundsException}, like the NIO path) and
         * stays inside a slice. No Unsafe and no raw addresses are involved.
         */
        private static MemorySegment bufferSegment(Buffer buf) {
            // ofBuffer spans [position, limit); a duplicate rewound to 0 is only needed when position > 0.
            return MemorySegment.ofBuffer(buf.position() == 0 ? buf : buf.duplicate().position(0));
        }

        /** Direct, writable and native-order: the segment fast path applies; otherwise defer to NIO. */
        private static boolean writableDirect(Buffer buf) {
            return buf.isDirect() && !buf.isReadOnly() && nativeOrder(buf);
        }

        /** Direct and native-order: the segment fast path applies for reads; otherwise defer to NIO. */
        private static boolean readableDirect(Buffer buf) {
            return buf.isDirect() && nativeOrder(buf);
        }

        private static boolean nativeOrder(Buffer buf) {
            if (buf instanceof ByteBuffer) return ((ByteBuffer) buf).order() == ByteOrder.nativeOrder();
            if (buf instanceof FloatBuffer) return ((FloatBuffer) buf).order() == ByteOrder.nativeOrder();
            if (buf instanceof DoubleBuffer) return ((DoubleBuffer) buf).order() == ByteOrder.nativeOrder();
            if (buf instanceof IntBuffer) return ((IntBuffer) buf).order() == ByteOrder.nativeOrder();
            if (buf instanceof java.nio.LongBuffer) return ((java.nio.LongBuffer) buf).order() == ByteOrder.nativeOrder();
            if (buf instanceof java.nio.ShortBuffer) return ((java.nio.ShortBuffer) buf).order() == ByteOrder.nativeOrder();
            return false;
        }
        private static long packFloats(float a, float b) {
            return pairFloats(a, b);
        }
        private static float unpackLo(long packed) {
            return Float.intBitsToFloat(firstInt(packed));
        }
        private static float unpackHi(long packed) {
            return Float.intBitsToFloat(secondInt(packed));
        }
        private static long packInts(int a, int b) {
            return pairInts(a, b);
        }

        // ============ Static put helpers ============

        public static void put(Matrix4f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m02(), m.m03()));
            s.set(JAVA_LONG, o + 16, packFloats(m.m10(), m.m11()));
            s.set(JAVA_LONG, o + 24, packFloats(m.m12(), m.m13()));
            s.set(JAVA_LONG, o + 32, packFloats(m.m20(), m.m21()));
            s.set(JAVA_LONG, o + 40, packFloats(m.m22(), m.m23()));
            s.set(JAVA_LONG, o + 48, packFloats(m.m30(), m.m31()));
            s.set(JAVA_LONG, o + 56, packFloats(m.m32(), m.m33()));
        }
        public static void put4x3(Matrix4f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m02(), m.m10()));
            s.set(JAVA_LONG, o + 16, packFloats(m.m11(), m.m12()));
            s.set(JAVA_LONG, o + 24, packFloats(m.m20(), m.m21()));
            s.set(JAVA_LONG, o + 32, packFloats(m.m22(), m.m30()));
            s.set(JAVA_LONG, o + 40, packFloats(m.m31(), m.m32()));
        }
        public static void put3x4(Matrix4f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m02(), m.m03()));
            s.set(JAVA_LONG, o + 16, packFloats(m.m10(), m.m11()));
            s.set(JAVA_LONG, o + 24, packFloats(m.m12(), m.m13()));
            s.set(JAVA_LONG, o + 32, packFloats(m.m20(), m.m21()));
            s.set(JAVA_LONG, o + 40, packFloats(m.m22(), m.m23()));
        }
        public static void put(Matrix4x3f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m02(), m.m10()));
            s.set(JAVA_LONG, o + 16, packFloats(m.m11(), m.m12()));
            s.set(JAVA_LONG, o + 24, packFloats(m.m20(), m.m21()));
            s.set(JAVA_LONG, o + 32, packFloats(m.m22(), m.m30()));
            s.set(JAVA_LONG, o + 40, packFloats(m.m31(), m.m32()));
        }
        public static void put4x4(Matrix4x3f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m02(), 0.0f));
            s.set(JAVA_LONG, o + 16, packFloats(m.m10(), m.m11()));
            s.set(JAVA_LONG, o + 24, packFloats(m.m12(), 0.0f));
            s.set(JAVA_LONG, o + 32, packFloats(m.m20(), m.m21()));
            s.set(JAVA_LONG, o + 40, packFloats(m.m22(), 0.0f));
            s.set(JAVA_LONG, o + 48, packFloats(m.m30(), m.m31()));
            s.set(JAVA_LONG, o + 56, packFloats(m.m32(), 1.0f));
        }
        public static void put3x4(Matrix4x3f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m02(), 0.0f));
            s.set(JAVA_LONG, o + 16, packFloats(m.m10(), m.m11()));
            s.set(JAVA_LONG, o + 24, packFloats(m.m12(), 0.0f));
            s.set(JAVA_LONG, o + 32, packFloats(m.m20(), m.m21()));
            s.set(JAVA_LONG, o + 40, packFloats(m.m22(), 0.0f));
        }
        public static void put4x4(Matrix4x3d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m01()); s.set(JAVA_DOUBLE, o + 16, m.m02()); s.set(JAVA_DOUBLE, o + 24, 0.0);
            s.set(JAVA_DOUBLE, o + 32, m.m10()); s.set(JAVA_DOUBLE, o + 40, m.m11()); s.set(JAVA_DOUBLE, o + 48, m.m12()); s.set(JAVA_DOUBLE, o + 56, 0.0);
            s.set(JAVA_DOUBLE, o + 64, m.m20()); s.set(JAVA_DOUBLE, o + 72, m.m21()); s.set(JAVA_DOUBLE, o + 80, m.m22()); s.set(JAVA_DOUBLE, o + 88, 0.0);
            s.set(JAVA_DOUBLE, o + 96, m.m30()); s.set(JAVA_DOUBLE, o + 104, m.m31()); s.set(JAVA_DOUBLE, o + 112, m.m32()); s.set(JAVA_DOUBLE, o + 120, 1.0);
        }
        public static void put4x4(Matrix3x2f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats(0.0f, 0.0f));
            s.set(JAVA_LONG, o + 16, packFloats(m.m10(), m.m11()));
            s.set(JAVA_LONG, o + 24, packFloats(0.0f, 0.0f));
            s.set(JAVA_LONG, o + 32, packFloats(0.0f, 0.0f));
            s.set(JAVA_LONG, o + 40, packFloats(1.0f, 0.0f));
            s.set(JAVA_LONG, o + 48, packFloats(m.m20(), m.m21()));
            s.set(JAVA_LONG, o + 56, packFloats(0.0f, 1.0f));
        }
        public static void put4x4(Matrix3x2d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m01()); s.set(JAVA_DOUBLE, o + 16, 0.0); s.set(JAVA_DOUBLE, o + 24, 0.0);
            s.set(JAVA_DOUBLE, o + 32, m.m10()); s.set(JAVA_DOUBLE, o + 40, m.m11()); s.set(JAVA_DOUBLE, o + 48, 0.0); s.set(JAVA_DOUBLE, o + 56, 0.0);
            s.set(JAVA_DOUBLE, o + 64, 0.0); s.set(JAVA_DOUBLE, o + 72, 0.0); s.set(JAVA_DOUBLE, o + 80, 1.0); s.set(JAVA_DOUBLE, o + 88, 0.0);
            s.set(JAVA_DOUBLE, o + 96, m.m20()); s.set(JAVA_DOUBLE, o + 104, m.m21()); s.set(JAVA_DOUBLE, o + 112, 0.0); s.set(JAVA_DOUBLE, o + 120, 1.0);
        }
        public static void put3x3(Matrix3x2f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats(0.0f, m.m10()));
            s.set(JAVA_LONG, o + 16, packFloats(m.m11(), 0.0f));
            s.set(JAVA_LONG, o + 24, packFloats(m.m20(), m.m21()));
            s.set(JAVA_FLOAT, o + 32, 1.0f);
        }
        public static void put3x3(Matrix3x2d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m01()); s.set(JAVA_DOUBLE, o + 16, 0.0);
            s.set(JAVA_DOUBLE, o + 24, m.m10()); s.set(JAVA_DOUBLE, o + 32, m.m11()); s.set(JAVA_DOUBLE, o + 40, 0.0);
            s.set(JAVA_DOUBLE, o + 48, m.m20()); s.set(JAVA_DOUBLE, o + 56, m.m21()); s.set(JAVA_DOUBLE, o + 64, 1.0);
        }
        public static void put(Matrix4d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m01()); s.set(JAVA_DOUBLE, o + 16, m.m02()); s.set(JAVA_DOUBLE, o + 24, m.m03());
            s.set(JAVA_DOUBLE, o + 32, m.m10()); s.set(JAVA_DOUBLE, o + 40, m.m11()); s.set(JAVA_DOUBLE, o + 48, m.m12()); s.set(JAVA_DOUBLE, o + 56, m.m13());
            s.set(JAVA_DOUBLE, o + 64, m.m20()); s.set(JAVA_DOUBLE, o + 72, m.m21()); s.set(JAVA_DOUBLE, o + 80, m.m22()); s.set(JAVA_DOUBLE, o + 88, m.m23());
            s.set(JAVA_DOUBLE, o + 96, m.m30()); s.set(JAVA_DOUBLE, o + 104, m.m31()); s.set(JAVA_DOUBLE, o + 112, m.m32()); s.set(JAVA_DOUBLE, o + 120, m.m33());
        }
        public static void put(Matrix4x3d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m01()); s.set(JAVA_DOUBLE, o + 16, m.m02());
            s.set(JAVA_DOUBLE, o + 24, m.m10()); s.set(JAVA_DOUBLE, o + 32, m.m11()); s.set(JAVA_DOUBLE, o + 40, m.m12());
            s.set(JAVA_DOUBLE, o + 48, m.m20()); s.set(JAVA_DOUBLE, o + 56, m.m21()); s.set(JAVA_DOUBLE, o + 64, m.m22());
            s.set(JAVA_DOUBLE, o + 72, m.m30()); s.set(JAVA_DOUBLE, o + 80, m.m31()); s.set(JAVA_DOUBLE, o + 88, m.m32());
        }
        public static void put(Matrix3f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m02(), m.m10()));
            s.set(JAVA_LONG, o + 16, packFloats(m.m11(), m.m12()));
            s.set(JAVA_LONG, o + 24, packFloats(m.m20(), m.m21()));
            s.set(JAVA_FLOAT, o + 32, m.m22());
        }
        public static void put3x4(Matrix3f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m02(), 0.0f));
            s.set(JAVA_LONG, o + 16, packFloats(m.m10(), m.m11()));
            s.set(JAVA_LONG, o + 24, packFloats(m.m12(), 0.0f));
            s.set(JAVA_LONG, o + 32, packFloats(m.m20(), m.m21()));
            s.set(JAVA_LONG, o + 40, packFloats(m.m22(), 0.0f));
        }
        public static void put(Matrix3d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m01()); s.set(JAVA_DOUBLE, o + 16, m.m02());
            s.set(JAVA_DOUBLE, o + 24, m.m10()); s.set(JAVA_DOUBLE, o + 32, m.m11()); s.set(JAVA_DOUBLE, o + 40, m.m12());
            s.set(JAVA_DOUBLE, o + 48, m.m20()); s.set(JAVA_DOUBLE, o + 56, m.m21()); s.set(JAVA_DOUBLE, o + 64, m.m22());
        }
        public static void put(Matrix3x2f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m10(), m.m11()));
            s.set(JAVA_LONG, o + 16, packFloats(m.m20(), m.m21()));
        }
        public static void put(Matrix3x2d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m01());
            s.set(JAVA_DOUBLE, o + 16, m.m10()); s.set(JAVA_DOUBLE, o + 24, m.m11());
            s.set(JAVA_DOUBLE, o + 32, m.m20()); s.set(JAVA_DOUBLE, o + 40, m.m21());
        }
        public static void put(Matrix2f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,     packFloats(m.m00(), m.m01()));
            s.set(JAVA_LONG, o + 8, packFloats(m.m10(), m.m11()));
        }
        public static void put(Matrix2d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m01());
            s.set(JAVA_DOUBLE, o + 16, m.m10()); s.set(JAVA_DOUBLE, o + 24, m.m11());
        }

        // ============ Transposed put helpers ============

        public static void putTransposed(Matrix4f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m10()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m20(), m.m30()));
            s.set(JAVA_LONG, o + 16, packFloats(m.m01(), m.m11()));
            s.set(JAVA_LONG, o + 24, packFloats(m.m21(), m.m31()));
            s.set(JAVA_LONG, o + 32, packFloats(m.m02(), m.m12()));
            s.set(JAVA_LONG, o + 40, packFloats(m.m22(), m.m32()));
            s.set(JAVA_LONG, o + 48, packFloats(m.m03(), m.m13()));
            s.set(JAVA_LONG, o + 56, packFloats(m.m23(), m.m33()));
        }
        public static void put4x3Transposed(Matrix4f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m10()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m20(), m.m30()));
            s.set(JAVA_LONG, o + 16, packFloats(m.m01(), m.m11()));
            s.set(JAVA_LONG, o + 24, packFloats(m.m21(), m.m31()));
            s.set(JAVA_LONG, o + 32, packFloats(m.m02(), m.m12()));
            s.set(JAVA_LONG, o + 40, packFloats(m.m22(), m.m32()));
        }
        public static void putTransposed(Matrix4x3f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m10()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m20(), m.m30()));
            s.set(JAVA_LONG, o + 16, packFloats(m.m01(), m.m11()));
            s.set(JAVA_LONG, o + 24, packFloats(m.m21(), m.m31()));
            s.set(JAVA_LONG, o + 32, packFloats(m.m02(), m.m12()));
            s.set(JAVA_LONG, o + 40, packFloats(m.m22(), m.m32()));
        }
        public static void putTransposed(Matrix3f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m10()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m20(), m.m01()));
            s.set(JAVA_LONG, o + 16, packFloats(m.m11(), m.m21()));
            s.set(JAVA_LONG, o + 24, packFloats(m.m02(), m.m12()));
            s.set(JAVA_FLOAT, o + 32, m.m22());
        }
        public static void putTransposed(Matrix3x2f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats(m.m00(), m.m10()));
            s.set(JAVA_LONG, o + 8,  packFloats(m.m20(), m.m01()));
            s.set(JAVA_LONG, o + 16, packFloats(m.m11(), m.m21()));
        }
        public static void putTransposed(Matrix2f m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,     packFloats(m.m00(), m.m10()));
            s.set(JAVA_LONG, o + 8, packFloats(m.m01(), m.m11()));
        }
        public static void putTransposed(Matrix4d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m10()); s.set(JAVA_DOUBLE, o + 16, m.m20()); s.set(JAVA_DOUBLE, o + 24, m.m30());
            s.set(JAVA_DOUBLE, o + 32, m.m01()); s.set(JAVA_DOUBLE, o + 40, m.m11()); s.set(JAVA_DOUBLE, o + 48, m.m21()); s.set(JAVA_DOUBLE, o + 56, m.m31());
            s.set(JAVA_DOUBLE, o + 64, m.m02()); s.set(JAVA_DOUBLE, o + 72, m.m12()); s.set(JAVA_DOUBLE, o + 80, m.m22()); s.set(JAVA_DOUBLE, o + 88, m.m32());
            s.set(JAVA_DOUBLE, o + 96, m.m03()); s.set(JAVA_DOUBLE, o + 104, m.m13()); s.set(JAVA_DOUBLE, o + 112, m.m23()); s.set(JAVA_DOUBLE, o + 120, m.m33());
        }
        public static void put4x3Transposed(Matrix4d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m10()); s.set(JAVA_DOUBLE, o + 16, m.m20()); s.set(JAVA_DOUBLE, o + 24, m.m30());
            s.set(JAVA_DOUBLE, o + 32, m.m01()); s.set(JAVA_DOUBLE, o + 40, m.m11()); s.set(JAVA_DOUBLE, o + 48, m.m21()); s.set(JAVA_DOUBLE, o + 56, m.m31());
            s.set(JAVA_DOUBLE, o + 64, m.m02()); s.set(JAVA_DOUBLE, o + 72, m.m12()); s.set(JAVA_DOUBLE, o + 80, m.m22()); s.set(JAVA_DOUBLE, o + 88, m.m32());
        }
        public static void putTransposed(Matrix4x3d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m10()); s.set(JAVA_DOUBLE, o + 16, m.m20()); s.set(JAVA_DOUBLE, o + 24, m.m30());
            s.set(JAVA_DOUBLE, o + 32, m.m01()); s.set(JAVA_DOUBLE, o + 40, m.m11()); s.set(JAVA_DOUBLE, o + 48, m.m21()); s.set(JAVA_DOUBLE, o + 56, m.m31());
            s.set(JAVA_DOUBLE, o + 64, m.m02()); s.set(JAVA_DOUBLE, o + 72, m.m12()); s.set(JAVA_DOUBLE, o + 80, m.m22()); s.set(JAVA_DOUBLE, o + 88, m.m32());
        }
        public static void putTransposed(Matrix3d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m10()); s.set(JAVA_DOUBLE, o + 16, m.m20());
            s.set(JAVA_DOUBLE, o + 24, m.m01()); s.set(JAVA_DOUBLE, o + 32, m.m11()); s.set(JAVA_DOUBLE, o + 40, m.m21());
            s.set(JAVA_DOUBLE, o + 48, m.m02()); s.set(JAVA_DOUBLE, o + 56, m.m12()); s.set(JAVA_DOUBLE, o + 64, m.m22());
        }
        public static void putTransposed(Matrix3x2d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m10()); s.set(JAVA_DOUBLE, o + 16, m.m20());
            s.set(JAVA_DOUBLE, o + 24, m.m01()); s.set(JAVA_DOUBLE, o + 32, m.m11()); s.set(JAVA_DOUBLE, o + 40, m.m21());
        }
        public static void putTransposed(Matrix2d m, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, m.m00()); s.set(JAVA_DOUBLE, o + 8, m.m10());
            s.set(JAVA_DOUBLE, o + 16, m.m01()); s.set(JAVA_DOUBLE, o + 24, m.m11());
        }

        // ============ Float-truncated put helpers (putf) ============

        public static void putf(Matrix4d m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats((float)m.m00(), (float)m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats((float)m.m02(), (float)m.m03()));
            s.set(JAVA_LONG, o + 16, packFloats((float)m.m10(), (float)m.m11()));
            s.set(JAVA_LONG, o + 24, packFloats((float)m.m12(), (float)m.m13()));
            s.set(JAVA_LONG, o + 32, packFloats((float)m.m20(), (float)m.m21()));
            s.set(JAVA_LONG, o + 40, packFloats((float)m.m22(), (float)m.m23()));
            s.set(JAVA_LONG, o + 48, packFloats((float)m.m30(), (float)m.m31()));
            s.set(JAVA_LONG, o + 56, packFloats((float)m.m32(), (float)m.m33()));
        }
        public static void putf(Matrix4x3d m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats((float)m.m00(), (float)m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats((float)m.m02(), (float)m.m10()));
            s.set(JAVA_LONG, o + 16, packFloats((float)m.m11(), (float)m.m12()));
            s.set(JAVA_LONG, o + 24, packFloats((float)m.m20(), (float)m.m21()));
            s.set(JAVA_LONG, o + 32, packFloats((float)m.m22(), (float)m.m30()));
            s.set(JAVA_LONG, o + 40, packFloats((float)m.m31(), (float)m.m32()));
        }
        public static void putf(Matrix3d m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats((float)m.m00(), (float)m.m01()));
            s.set(JAVA_LONG, o + 8,  packFloats((float)m.m02(), (float)m.m10()));
            s.set(JAVA_LONG, o + 16, packFloats((float)m.m11(), (float)m.m12()));
            s.set(JAVA_LONG, o + 24, packFloats((float)m.m20(), (float)m.m21()));
            s.set(JAVA_FLOAT, o + 32, (float)m.m22());
        }
        public static void putf(Matrix2d m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,     packFloats((float)m.m00(), (float)m.m01()));
            s.set(JAVA_LONG, o + 8, packFloats((float)m.m10(), (float)m.m11()));
        }

        // ============ Float-truncated transposed (putfTransposed) ============

        public static void putfTransposed(Matrix4d m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats((float)m.m00(), (float)m.m10()));
            s.set(JAVA_LONG, o + 8,  packFloats((float)m.m20(), (float)m.m30()));
            s.set(JAVA_LONG, o + 16, packFloats((float)m.m01(), (float)m.m11()));
            s.set(JAVA_LONG, o + 24, packFloats((float)m.m21(), (float)m.m31()));
            s.set(JAVA_LONG, o + 32, packFloats((float)m.m02(), (float)m.m12()));
            s.set(JAVA_LONG, o + 40, packFloats((float)m.m22(), (float)m.m32()));
            s.set(JAVA_LONG, o + 48, packFloats((float)m.m03(), (float)m.m13()));
            s.set(JAVA_LONG, o + 56, packFloats((float)m.m23(), (float)m.m33()));
        }
        public static void putfTransposed(Matrix4x3d m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats((float)m.m00(), (float)m.m10()));
            s.set(JAVA_LONG, o + 8,  packFloats((float)m.m20(), (float)m.m30()));
            s.set(JAVA_LONG, o + 16, packFloats((float)m.m01(), (float)m.m11()));
            s.set(JAVA_LONG, o + 24, packFloats((float)m.m21(), (float)m.m31()));
            s.set(JAVA_LONG, o + 32, packFloats((float)m.m02(), (float)m.m12()));
            s.set(JAVA_LONG, o + 40, packFloats((float)m.m22(), (float)m.m32()));
        }
        public static void putfTransposed(Matrix3d m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats((float)m.m00(), (float)m.m10()));
            s.set(JAVA_LONG, o + 8,  packFloats((float)m.m20(), (float)m.m01()));
            s.set(JAVA_LONG, o + 16, packFloats((float)m.m11(), (float)m.m21()));
            s.set(JAVA_LONG, o + 24, packFloats((float)m.m02(), (float)m.m12()));
            s.set(JAVA_FLOAT, o + 32, (float)m.m22());
        }
        public static void putfTransposed(Matrix3x2d m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,      packFloats((float)m.m00(), (float)m.m10()));
            s.set(JAVA_LONG, o + 8,  packFloats((float)m.m20(), (float)m.m01()));
            s.set(JAVA_LONG, o + 16, packFloats((float)m.m11(), (float)m.m21()));
        }
        public static void putfTransposed(Matrix2d m, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,     packFloats((float)m.m00(), (float)m.m10()));
            s.set(JAVA_LONG, o + 8, packFloats((float)m.m01(), (float)m.m11()));
        }

        // ============ Vector put helpers ============

        public static void put(Vector4d src, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, src.x); s.set(JAVA_DOUBLE, o + 8, src.y); s.set(JAVA_DOUBLE, o + 16, src.z); s.set(JAVA_DOUBLE, o + 24, src.w);
        }
        public static void putf(Vector4d src, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,     packFloats((float)src.x, (float)src.y));
            s.set(JAVA_LONG, o + 8, packFloats((float)src.z, (float)src.w));
        }
        public static void put(Vector4f src, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,     packFloats(src.x, src.y));
            s.set(JAVA_LONG, o + 8, packFloats(src.z, src.w));
        }
        public static void put(Vector4i src, MemorySegment s, long o) {
            s.set(JAVA_LONG, o,     packInts(src.x, src.y));
            s.set(JAVA_LONG, o + 8, packInts(src.z, src.w));
        }
        public static void put(Vector4L src, MemorySegment s, long o) {
            s.set(JAVA_LONG, o, src.x); s.set(JAVA_LONG, o + 8, src.y); s.set(JAVA_LONG, o + 16, src.z); s.set(JAVA_LONG, o + 24, src.w);
        }
        public static void put(Vector3f src, MemorySegment s, long o) {
            s.set(JAVA_LONG, o, packFloats(src.x, src.y));
            s.set(JAVA_FLOAT, o + 8, src.z);
        }
        public static void put(Vector3d src, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, src.x); s.set(JAVA_DOUBLE, o + 8, src.y); s.set(JAVA_DOUBLE, o + 16, src.z);
        }
        public static void putf(Vector3d src, MemorySegment s, long o) {
            s.set(JAVA_LONG, o, packFloats((float)src.x, (float)src.y));
            s.set(JAVA_FLOAT, o + 8, (float)src.z);
        }
        public static void put(Vector3i src, MemorySegment s, long o) {
            s.set(JAVA_LONG, o, packInts(src.x, src.y));
            s.set(JAVA_INT, o + 8, src.z);
        }
        public static void put(Vector3L src, MemorySegment s, long o) {
            s.set(JAVA_LONG, o, src.x); s.set(JAVA_LONG, o + 8, src.y); s.set(JAVA_LONG, o + 16, src.z);
        }
        public static void put(Vector2f src, MemorySegment s, long o) {
            s.set(JAVA_LONG, o, packFloats(src.x, src.y));
        }
        public static void put(Vector2d src, MemorySegment s, long o) {
            s.set(JAVA_DOUBLE, o, src.x); s.set(JAVA_DOUBLE, o + 8, src.y);
        }
        public static void put(Vector2i src, MemorySegment s, long o) {
            s.set(JAVA_LONG, o, packInts(src.x, src.y));
        }
        public static void put(Vector2L src, MemorySegment s, long o) {
            s.set(JAVA_LONG, o, src.x); s.set(JAVA_LONG, o + 8, src.y);
        }

        // ============ Matrix get helpers ============

        public static void get(Matrix4f m, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o),      l1 = s.get(JAVA_LONG, o + 8);
            long l2 = s.get(JAVA_LONG, o + 16), l3 = s.get(JAVA_LONG, o + 24);
            long l4 = s.get(JAVA_LONG, o + 32), l5 = s.get(JAVA_LONG, o + 40);
            long l6 = s.get(JAVA_LONG, o + 48), l7 = s.get(JAVA_LONG, o + 56);
            m._m00(unpackLo(l0))._m01(unpackHi(l0))._m02(unpackLo(l1))._m03(unpackHi(l1))
             ._m10(unpackLo(l2))._m11(unpackHi(l2))._m12(unpackLo(l3))._m13(unpackHi(l3))
             ._m20(unpackLo(l4))._m21(unpackHi(l4))._m22(unpackLo(l5))._m23(unpackHi(l5))
             ._m30(unpackLo(l6))._m31(unpackHi(l6))._m32(unpackLo(l7))._m33(unpackHi(l7));
        }
        public static void get(Matrix4x3f m, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o),      l1 = s.get(JAVA_LONG, o + 8);
            long l2 = s.get(JAVA_LONG, o + 16), l3 = s.get(JAVA_LONG, o + 24);
            long l4 = s.get(JAVA_LONG, o + 32), l5 = s.get(JAVA_LONG, o + 40);
            m._m00(unpackLo(l0))._m01(unpackHi(l0))._m02(unpackLo(l1))
             ._m10(unpackHi(l1))._m11(unpackLo(l2))._m12(unpackHi(l2))
             ._m20(unpackLo(l3))._m21(unpackHi(l3))._m22(unpackLo(l4))
             ._m30(unpackHi(l4))._m31(unpackLo(l5))._m32(unpackHi(l5));
        }
        public static void get(Matrix4d m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_DOUBLE, o))._m01(s.get(JAVA_DOUBLE, o + 8))._m02(s.get(JAVA_DOUBLE, o + 16))._m03(s.get(JAVA_DOUBLE, o + 24))
             ._m10(s.get(JAVA_DOUBLE, o + 32))._m11(s.get(JAVA_DOUBLE, o + 40))._m12(s.get(JAVA_DOUBLE, o + 48))._m13(s.get(JAVA_DOUBLE, o + 56))
             ._m20(s.get(JAVA_DOUBLE, o + 64))._m21(s.get(JAVA_DOUBLE, o + 72))._m22(s.get(JAVA_DOUBLE, o + 80))._m23(s.get(JAVA_DOUBLE, o + 88))
             ._m30(s.get(JAVA_DOUBLE, o + 96))._m31(s.get(JAVA_DOUBLE, o + 104))._m32(s.get(JAVA_DOUBLE, o + 112))._m33(s.get(JAVA_DOUBLE, o + 120));
        }
        public static void get(Matrix4x3d m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_DOUBLE, o))._m01(s.get(JAVA_DOUBLE, o + 8))._m02(s.get(JAVA_DOUBLE, o + 16))
             ._m10(s.get(JAVA_DOUBLE, o + 24))._m11(s.get(JAVA_DOUBLE, o + 32))._m12(s.get(JAVA_DOUBLE, o + 40))
             ._m20(s.get(JAVA_DOUBLE, o + 48))._m21(s.get(JAVA_DOUBLE, o + 56))._m22(s.get(JAVA_DOUBLE, o + 64))
             ._m30(s.get(JAVA_DOUBLE, o + 72))._m31(s.get(JAVA_DOUBLE, o + 80))._m32(s.get(JAVA_DOUBLE, o + 88));
        }
        public static void get(Matrix3f m, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o),      l1 = s.get(JAVA_LONG, o + 8);
            long l2 = s.get(JAVA_LONG, o + 16), l3 = s.get(JAVA_LONG, o + 24);
            m._m00(unpackLo(l0))._m01(unpackHi(l0))._m02(unpackLo(l1))
             ._m10(unpackHi(l1))._m11(unpackLo(l2))._m12(unpackHi(l2))
             ._m20(unpackLo(l3))._m21(unpackHi(l3))._m22(s.get(JAVA_FLOAT, o + 32));
        }
        public static void get(Matrix3d m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_DOUBLE, o))._m01(s.get(JAVA_DOUBLE, o + 8))._m02(s.get(JAVA_DOUBLE, o + 16))
             ._m10(s.get(JAVA_DOUBLE, o + 24))._m11(s.get(JAVA_DOUBLE, o + 32))._m12(s.get(JAVA_DOUBLE, o + 40))
             ._m20(s.get(JAVA_DOUBLE, o + 48))._m21(s.get(JAVA_DOUBLE, o + 56))._m22(s.get(JAVA_DOUBLE, o + 64));
        }
        public static void get(Matrix3x2f m, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o), l1 = s.get(JAVA_LONG, o + 8), l2 = s.get(JAVA_LONG, o + 16);
            m._m00(unpackLo(l0))._m01(unpackHi(l0))
             ._m10(unpackLo(l1))._m11(unpackHi(l1))
             ._m20(unpackLo(l2))._m21(unpackHi(l2));
        }
        public static void get(Matrix3x2d m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_DOUBLE, o))._m01(s.get(JAVA_DOUBLE, o + 8))
             ._m10(s.get(JAVA_DOUBLE, o + 16))._m11(s.get(JAVA_DOUBLE, o + 24))
             ._m20(s.get(JAVA_DOUBLE, o + 32))._m21(s.get(JAVA_DOUBLE, o + 40));
        }
        public static void get(Matrix2f m, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o), l1 = s.get(JAVA_LONG, o + 8);
            m._m00(unpackLo(l0))._m01(unpackHi(l0))
             ._m10(unpackLo(l1))._m11(unpackHi(l1));
        }
        public static void get(Matrix2d m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_DOUBLE, o))._m01(s.get(JAVA_DOUBLE, o + 8))
             ._m10(s.get(JAVA_DOUBLE, o + 16))._m11(s.get(JAVA_DOUBLE, o + 24));
        }
        public static void getf(Matrix4d m, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o),      l1 = s.get(JAVA_LONG, o + 8);
            long l2 = s.get(JAVA_LONG, o + 16), l3 = s.get(JAVA_LONG, o + 24);
            long l4 = s.get(JAVA_LONG, o + 32), l5 = s.get(JAVA_LONG, o + 40);
            long l6 = s.get(JAVA_LONG, o + 48), l7 = s.get(JAVA_LONG, o + 56);
            m._m00(unpackLo(l0))._m01(unpackHi(l0))._m02(unpackLo(l1))._m03(unpackHi(l1))
             ._m10(unpackLo(l2))._m11(unpackHi(l2))._m12(unpackLo(l3))._m13(unpackHi(l3))
             ._m20(unpackLo(l4))._m21(unpackHi(l4))._m22(unpackLo(l5))._m23(unpackHi(l5))
             ._m30(unpackLo(l6))._m31(unpackHi(l6))._m32(unpackLo(l7))._m33(unpackHi(l7));
        }
        public static void getf(Matrix4x3d m, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o),      l1 = s.get(JAVA_LONG, o + 8);
            long l2 = s.get(JAVA_LONG, o + 16), l3 = s.get(JAVA_LONG, o + 24);
            long l4 = s.get(JAVA_LONG, o + 32), l5 = s.get(JAVA_LONG, o + 40);
            m._m00(unpackLo(l0))._m01(unpackHi(l0))._m02(unpackLo(l1))
             ._m10(unpackHi(l1))._m11(unpackLo(l2))._m12(unpackHi(l2))
             ._m20(unpackLo(l3))._m21(unpackHi(l3))._m22(unpackLo(l4))
             ._m30(unpackHi(l4))._m31(unpackLo(l5))._m32(unpackHi(l5));
        }
        public static void getf(Matrix3d m, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o),      l1 = s.get(JAVA_LONG, o + 8);
            long l2 = s.get(JAVA_LONG, o + 16), l3 = s.get(JAVA_LONG, o + 24);
            m._m00(unpackLo(l0))._m01(unpackHi(l0))._m02(unpackLo(l1))
             ._m10(unpackHi(l1))._m11(unpackLo(l2))._m12(unpackHi(l2))
             ._m20(unpackLo(l3))._m21(unpackHi(l3))._m22(s.get(JAVA_FLOAT, o + 32));
        }
        public static void getf(Matrix2d m, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o), l1 = s.get(JAVA_LONG, o + 8);
            m._m00(unpackLo(l0))._m01(unpackHi(l0))
             ._m10(unpackLo(l1))._m11(unpackHi(l1));
        }

        // ============ Transposed get helpers ============

        public static void getTransposed(Matrix4f m, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o),      l1 = s.get(JAVA_LONG, o + 8);
            long l2 = s.get(JAVA_LONG, o + 16), l3 = s.get(JAVA_LONG, o + 24);
            long l4 = s.get(JAVA_LONG, o + 32), l5 = s.get(JAVA_LONG, o + 40);
            long l6 = s.get(JAVA_LONG, o + 48), l7 = s.get(JAVA_LONG, o + 56);
            m._m00(unpackLo(l0))._m10(unpackHi(l0))._m20(unpackLo(l1))._m30(unpackHi(l1))
             ._m01(unpackLo(l2))._m11(unpackHi(l2))._m21(unpackLo(l3))._m31(unpackHi(l3))
             ._m02(unpackLo(l4))._m12(unpackHi(l4))._m22(unpackLo(l5))._m32(unpackHi(l5))
             ._m03(unpackLo(l6))._m13(unpackHi(l6))._m23(unpackLo(l7))._m33(unpackHi(l7));
        }

        public static void getTransposed(Matrix3f m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_FLOAT, o))
             ._m10(s.get(JAVA_FLOAT, o + 4))
             ._m20(s.get(JAVA_FLOAT, o + 8))
             ._m01(s.get(JAVA_FLOAT, o + 12))
             ._m11(s.get(JAVA_FLOAT, o + 16))
             ._m21(s.get(JAVA_FLOAT, o + 20))
             ._m02(s.get(JAVA_FLOAT, o + 24))
             ._m12(s.get(JAVA_FLOAT, o + 28))
             ._m22(s.get(JAVA_FLOAT, o + 32));
        }

        public static void getTransposed(Matrix4x3f m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_FLOAT, o))
             ._m10(s.get(JAVA_FLOAT, o + 4))
             ._m20(s.get(JAVA_FLOAT, o + 8))
             ._m30(s.get(JAVA_FLOAT, o + 12))
             ._m01(s.get(JAVA_FLOAT, o + 16))
             ._m11(s.get(JAVA_FLOAT, o + 20))
             ._m21(s.get(JAVA_FLOAT, o + 24))
             ._m31(s.get(JAVA_FLOAT, o + 28))
             ._m02(s.get(JAVA_FLOAT, o + 32))
             ._m12(s.get(JAVA_FLOAT, o + 36))
             ._m22(s.get(JAVA_FLOAT, o + 40))
             ._m32(s.get(JAVA_FLOAT, o + 44));
        }

        public static void getTransposed(Matrix3x2f m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_FLOAT, o))
             ._m10(s.get(JAVA_FLOAT, o + 4))
             ._m20(s.get(JAVA_FLOAT, o + 8))
             ._m01(s.get(JAVA_FLOAT, o + 12))
             ._m11(s.get(JAVA_FLOAT, o + 16))
             ._m21(s.get(JAVA_FLOAT, o + 20));
        }

        public static void getTransposed(Matrix2f m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_FLOAT, o))
             ._m10(s.get(JAVA_FLOAT, o + 4))
             ._m01(s.get(JAVA_FLOAT, o + 8))
             ._m11(s.get(JAVA_FLOAT, o + 12));
        }

        public static void getTransposed(Matrix2d m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_DOUBLE, o))
             ._m10(s.get(JAVA_DOUBLE, o + 8))
             ._m01(s.get(JAVA_DOUBLE, o + 16))
             ._m11(s.get(JAVA_DOUBLE, o + 24));
        }

        public static void getTransposed(Matrix4x3d m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_DOUBLE, o))
             ._m10(s.get(JAVA_DOUBLE, o + 8))
             ._m20(s.get(JAVA_DOUBLE, o + 16))
             ._m30(s.get(JAVA_DOUBLE, o + 24))
             ._m01(s.get(JAVA_DOUBLE, o + 32))
             ._m11(s.get(JAVA_DOUBLE, o + 40))
             ._m21(s.get(JAVA_DOUBLE, o + 48))
             ._m31(s.get(JAVA_DOUBLE, o + 56))
             ._m02(s.get(JAVA_DOUBLE, o + 64))
             ._m12(s.get(JAVA_DOUBLE, o + 72))
             ._m22(s.get(JAVA_DOUBLE, o + 80))
             ._m32(s.get(JAVA_DOUBLE, o + 88));
        }

        public static void getTransposed(Matrix3x2d m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_DOUBLE, o))
             ._m10(s.get(JAVA_DOUBLE, o + 8))
             ._m20(s.get(JAVA_DOUBLE, o + 16))
             ._m01(s.get(JAVA_DOUBLE, o + 24))
             ._m11(s.get(JAVA_DOUBLE, o + 32))
             ._m21(s.get(JAVA_DOUBLE, o + 40));
        }

        public static void getTransposed(Matrix3d m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_DOUBLE, o))
             ._m10(s.get(JAVA_DOUBLE, o + 8))
             ._m20(s.get(JAVA_DOUBLE, o + 16))
             ._m01(s.get(JAVA_DOUBLE, o + 24))
             ._m11(s.get(JAVA_DOUBLE, o + 32))
             ._m21(s.get(JAVA_DOUBLE, o + 40))
             ._m02(s.get(JAVA_DOUBLE, o + 48))
             ._m12(s.get(JAVA_DOUBLE, o + 56))
             ._m22(s.get(JAVA_DOUBLE, o + 64));
        }

        public static void getTransposed(Matrix4d m, MemorySegment s, long o) {
            m._m00(s.get(JAVA_DOUBLE, o))
             ._m10(s.get(JAVA_DOUBLE, o + 8))
             ._m20(s.get(JAVA_DOUBLE, o + 16))
             ._m30(s.get(JAVA_DOUBLE, o + 24))
             ._m01(s.get(JAVA_DOUBLE, o + 32))
             ._m11(s.get(JAVA_DOUBLE, o + 40))
             ._m21(s.get(JAVA_DOUBLE, o + 48))
             ._m31(s.get(JAVA_DOUBLE, o + 56))
             ._m02(s.get(JAVA_DOUBLE, o + 64))
             ._m12(s.get(JAVA_DOUBLE, o + 72))
             ._m22(s.get(JAVA_DOUBLE, o + 80))
             ._m32(s.get(JAVA_DOUBLE, o + 88))
             ._m03(s.get(JAVA_DOUBLE, o + 96))
             ._m13(s.get(JAVA_DOUBLE, o + 104))
             ._m23(s.get(JAVA_DOUBLE, o + 112))
             ._m33(s.get(JAVA_DOUBLE, o + 120));
        }

        // ============ Vector get helpers ============

        public static void get(Vector4d dst, MemorySegment s, long o) {
            dst.x = s.get(JAVA_DOUBLE, o); dst.y = s.get(JAVA_DOUBLE, o + 8); dst.z = s.get(JAVA_DOUBLE, o + 16); dst.w = s.get(JAVA_DOUBLE, o + 24);
        }
        public static void get(Vector4f dst, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o), l1 = s.get(JAVA_LONG, o + 8);
            dst.x = unpackLo(l0); dst.y = unpackHi(l0); dst.z = unpackLo(l1); dst.w = unpackHi(l1);
        }
        public static void get(Vector4i dst, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o), l1 = s.get(JAVA_LONG, o + 8);
            dst.x = firstInt(l0); dst.y = secondInt(l0); dst.z = firstInt(l1); dst.w = secondInt(l1);
        }
        public static void get(Vector4L dst, MemorySegment s, long o) {
            dst.x = s.get(JAVA_LONG, o); dst.y = s.get(JAVA_LONG, o + 8); dst.z = s.get(JAVA_LONG, o + 16); dst.w = s.get(JAVA_LONG, o + 24);
        }
        public static void get(Vector3f dst, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o);
            dst.x = unpackLo(l0); dst.y = unpackHi(l0); dst.z = s.get(JAVA_FLOAT, o + 8);
        }
        public static void get(Vector3d dst, MemorySegment s, long o) {
            dst.x = s.get(JAVA_DOUBLE, o); dst.y = s.get(JAVA_DOUBLE, o + 8); dst.z = s.get(JAVA_DOUBLE, o + 16);
        }
        public static void get(Vector3i dst, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o);
            dst.x = firstInt(l0); dst.y = secondInt(l0); dst.z = s.get(JAVA_INT, o + 8);
        }
        public static void get(Vector3L dst, MemorySegment s, long o) {
            dst.x = s.get(JAVA_LONG, o); dst.y = s.get(JAVA_LONG, o + 8); dst.z = s.get(JAVA_LONG, o + 16);
        }
        public static void get(Vector2f dst, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o);
            dst.x = unpackLo(l0); dst.y = unpackHi(l0);
        }
        public static void get(Vector2d dst, MemorySegment s, long o) {
            dst.x = s.get(JAVA_DOUBLE, o); dst.y = s.get(JAVA_DOUBLE, o + 8);
        }
        public static void get(Vector2i dst, MemorySegment s, long o) {
            long l0 = s.get(JAVA_LONG, o);
            dst.x = firstInt(l0); dst.y = secondInt(l0);
        }
        public static void get(Vector2L dst, MemorySegment s, long o) {
            dst.x = s.get(JAVA_LONG, o); dst.y = s.get(JAVA_LONG, o + 8);
        }

        // ============ Quaternion static helpers ============

        public static void putMatrix3f(Quaternionf q, MemorySegment s, long o) {
            // Same expressions as the NIO path and in the same order, so every backend writes identical bytes.
            float w2 = q.w * q.w, x2 = q.x * q.x, y2 = q.y * q.y, z2 = q.z * q.z;
            float zw = q.z * q.w, xy = q.x * q.y, xz = q.x * q.z, yw = q.y * q.w, yz = q.y * q.z, xw = q.x * q.w;
            float m00 = w2 + x2 - z2 - y2, m01 = xy + zw + zw + xy, m02 = xz - yw + xz - yw;
            float m10 = -zw + xy - zw + xy, m11 = y2 - z2 + w2 - x2, m12 = yz + yz + xw + xw;
            float m20 = yw + xz + xz + yw, m21 = yz + yz - xw - xw, m22 = z2 - y2 - x2 + w2;
            s.set(JAVA_LONG, o + 0, packFloats(m00, m01));
            s.set(JAVA_LONG, o + 8, packFloats(m02, m10));
            s.set(JAVA_LONG, o + 16, packFloats(m11, m12));
            s.set(JAVA_LONG, o + 24, packFloats(m20, m21));
            s.set(JAVA_FLOAT, o + 32, m22);
        }
        public static void putMatrix4f(Quaternionf q, MemorySegment s, long o) {
            // Same expressions as the NIO path and in the same order, so every backend writes identical bytes.
            float w2 = q.w * q.w, x2 = q.x * q.x, y2 = q.y * q.y, z2 = q.z * q.z;
            float zw = q.z * q.w, xy = q.x * q.y, xz = q.x * q.z, yw = q.y * q.w, yz = q.y * q.z, xw = q.x * q.w;
            float m00 = w2 + x2 - z2 - y2, m01 = xy + zw + zw + xy, m02 = xz - yw + xz - yw;
            float m10 = -zw + xy - zw + xy, m11 = y2 - z2 + w2 - x2, m12 = yz + yz + xw + xw;
            float m20 = yw + xz + xz + yw, m21 = yz + yz - xw - xw, m22 = z2 - y2 - x2 + w2;
            s.set(JAVA_LONG, o + 0, packFloats(m00, m01));
            s.set(JAVA_LONG, o + 8, packFloats(m02, 0.0f));
            s.set(JAVA_LONG, o + 16, packFloats(m10, m11));
            s.set(JAVA_LONG, o + 24, packFloats(m12, 0.0f));
            s.set(JAVA_LONG, o + 32, packFloats(m20, m21));
            s.set(JAVA_LONG, o + 40, packFloats(m22, 0.0f));
            s.set(JAVA_LONG, o + 48, packFloats(0.0f, 0.0f));
            s.set(JAVA_LONG, o + 56, packFloats(0.0f, 1.0f));
        }
        public static void putMatrix4x3f(Quaternionf q, MemorySegment s, long o) {
            // Same expressions as the NIO path and in the same order, so every backend writes identical bytes.
            float w2 = q.w * q.w, x2 = q.x * q.x, y2 = q.y * q.y, z2 = q.z * q.z;
            float zw = q.z * q.w, xy = q.x * q.y, xz = q.x * q.z, yw = q.y * q.w, yz = q.y * q.z, xw = q.x * q.w;
            float m00 = w2 + x2 - z2 - y2, m01 = xy + zw + zw + xy, m02 = xz - yw + xz - yw;
            float m10 = -zw + xy - zw + xy, m11 = y2 - z2 + w2 - x2, m12 = yz + yz + xw + xw;
            float m20 = yw + xz + xz + yw, m21 = yz + yz - xw - xw, m22 = z2 - y2 - x2 + w2;
            s.set(JAVA_LONG, o + 0, packFloats(m00, m01));
            s.set(JAVA_LONG, o + 8, packFloats(m02, m10));
            s.set(JAVA_LONG, o + 16, packFloats(m11, m12));
            s.set(JAVA_LONG, o + 24, packFloats(m20, m21));
            s.set(JAVA_LONG, o + 32, packFloats(m22, 0.0f));
            s.set(JAVA_LONG, o + 40, packFloats(0.0f, 0.0f));
        }

        // ============ Buffer override methods ============

        // --- Quaternion buffer overrides ---
        public void putMatrix3f(Quaternionf q, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putMatrix3f(q, bufferSegment(dest).asSlice(offset, 36), 0); else super.putMatrix3f(q, offset, dest);
        }
        public void putMatrix3f(Quaternionf q, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putMatrix3f(q, bufferSegment(dest).asSlice(((long) offset << 2), 36), 0); else super.putMatrix3f(q, offset, dest);
        }
        public void putMatrix4f(Quaternionf q, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putMatrix4f(q, bufferSegment(dest).asSlice(offset, 64), 0); else super.putMatrix4f(q, offset, dest);
        }
        public void putMatrix4f(Quaternionf q, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putMatrix4f(q, bufferSegment(dest).asSlice(((long) offset << 2), 64), 0); else super.putMatrix4f(q, offset, dest);
        }
        public void putMatrix4x3f(Quaternionf q, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putMatrix4x3f(q, bufferSegment(dest).asSlice(offset, 48), 0); else super.putMatrix4x3f(q, offset, dest);
        }
        public void putMatrix4x3f(Quaternionf q, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putMatrix4x3f(q, bufferSegment(dest).asSlice(((long) offset << 2), 48), 0); else super.putMatrix4x3f(q, offset, dest);
        }

        // --- Matrix4f buffer put ---
        public void put(Matrix4f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice((long) offset << 2, 64), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix4f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(offset, 64), 0); else super.put(m, offset, dest);
        }
        public void put4x3(Matrix4f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put4x3(m, bufferSegment(dest).asSlice(((long) offset << 2), 48), 0); else super.put4x3(m, offset, dest);
        }
        public void put4x3(Matrix4f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put4x3(m, bufferSegment(dest).asSlice(offset, 48), 0); else super.put4x3(m, offset, dest);
        }
        public void put3x4(Matrix4f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put3x4(m, bufferSegment(dest).asSlice(((long) offset << 2), 48), 0); else super.put3x4(m, offset, dest);
        }
        public void put3x4(Matrix4f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put3x4(m, bufferSegment(dest).asSlice(offset, 48), 0); else super.put3x4(m, offset, dest);
        }
        // --- Matrix4x3f buffer put ---
        public void put(Matrix4x3f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(((long) offset << 2), 48), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix4x3f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(offset, 48), 0); else super.put(m, offset, dest);
        }
        public void put4x4(Matrix4x3f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put4x4(m, bufferSegment(dest).asSlice(((long) offset << 2), 64), 0); else super.put4x4(m, offset, dest);
        }
        public void put4x4(Matrix4x3f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put4x4(m, bufferSegment(dest).asSlice(offset, 64), 0); else super.put4x4(m, offset, dest);
        }
        public void put3x4(Matrix4x3f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put3x4(m, bufferSegment(dest).asSlice(((long) offset << 2), 48), 0); else super.put3x4(m, offset, dest);
        }
        public void put3x4(Matrix4x3f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put3x4(m, bufferSegment(dest).asSlice(offset, 48), 0); else super.put3x4(m, offset, dest);
        }
        // --- Matrix4x3d 4x4 buffer put ---
        public void put4x4(Matrix4x3d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) put4x4(m, bufferSegment(dest).asSlice(((long) offset << 3), 128), 0); else super.put4x4(m, offset, dest);
        }
        public void put4x4(Matrix4x3d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put4x4(m, bufferSegment(dest).asSlice(offset, 128), 0); else super.put4x4(m, offset, dest);
        }
        // --- Matrix3x2f/3x2d 4x4/3x3 buffer put ---
        public void put4x4(Matrix3x2f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put4x4(m, bufferSegment(dest).asSlice(((long) offset << 2), 64), 0); else super.put4x4(m, offset, dest);
        }
        public void put4x4(Matrix3x2f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put4x4(m, bufferSegment(dest).asSlice(offset, 64), 0); else super.put4x4(m, offset, dest);
        }
        public void put4x4(Matrix3x2d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) put4x4(m, bufferSegment(dest).asSlice(((long) offset << 3), 128), 0); else super.put4x4(m, offset, dest);
        }
        public void put4x4(Matrix3x2d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put4x4(m, bufferSegment(dest).asSlice(offset, 128), 0); else super.put4x4(m, offset, dest);
        }
        public void put3x3(Matrix3x2f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put3x3(m, bufferSegment(dest).asSlice(((long) offset << 2), 36), 0); else super.put3x3(m, offset, dest);
        }
        public void put3x3(Matrix3x2f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put3x3(m, bufferSegment(dest).asSlice(offset, 36), 0); else super.put3x3(m, offset, dest);
        }
        public void put3x3(Matrix3x2d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) put3x3(m, bufferSegment(dest).asSlice(((long) offset << 3), 72), 0); else super.put3x3(m, offset, dest);
        }
        public void put3x3(Matrix3x2d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put3x3(m, bufferSegment(dest).asSlice(offset, 72), 0); else super.put3x3(m, offset, dest);
        }
        // --- Transposed buffer put ---
        public void putTransposed(Matrix4f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(((long) offset << 2), 64), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix4f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(offset, 64), 0); else super.putTransposed(m, offset, dest);
        }
        public void put4x3Transposed(Matrix4f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put4x3Transposed(m, bufferSegment(dest).asSlice(((long) offset << 2), 48), 0); else super.put4x3Transposed(m, offset, dest);
        }
        public void put4x3Transposed(Matrix4f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put4x3Transposed(m, bufferSegment(dest).asSlice(offset, 48), 0); else super.put4x3Transposed(m, offset, dest);
        }
        public void putTransposed(Matrix4x3f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(((long) offset << 2), 48), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix4x3f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(offset, 48), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix3f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(((long) offset << 2), 36), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix3f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(offset, 36), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix3x2f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(((long) offset << 2), 24), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix2f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(((long) offset << 2), 16), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix2f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(offset, 16), 0); else super.putTransposed(m, offset, dest);
        }
        // --- Matrix4d/4x3d buffer put ---
        public void put(Matrix4d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(((long) offset << 3), 128), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix4d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(offset, 128), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix4x3d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(((long) offset << 3), 96), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix4x3d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(offset, 96), 0); else super.put(m, offset, dest);
        }
        public void putf(Matrix4d m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putf(m, bufferSegment(dest).asSlice(((long) offset << 2), 64), 0); else super.putf(m, offset, dest);
        }
        public void putf(Matrix4d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putf(m, bufferSegment(dest).asSlice(offset, 64), 0); else super.putf(m, offset, dest);
        }
        public void putf(Matrix4x3d m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putf(m, bufferSegment(dest).asSlice(((long) offset << 2), 48), 0); else super.putf(m, offset, dest);
        }
        public void putf(Matrix4x3d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putf(m, bufferSegment(dest).asSlice(offset, 48), 0); else super.putf(m, offset, dest);
        }
        // --- Double transposed buffer put ---
        public void putTransposed(Matrix4d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(((long) offset << 3), 128), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix4d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(offset, 128), 0); else super.putTransposed(m, offset, dest);
        }
        public void put4x3Transposed(Matrix4d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) put4x3Transposed(m, bufferSegment(dest).asSlice(((long) offset << 3), 96), 0); else super.put4x3Transposed(m, offset, dest);
        }
        public void put4x3Transposed(Matrix4d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put4x3Transposed(m, bufferSegment(dest).asSlice(offset, 96), 0); else super.put4x3Transposed(m, offset, dest);
        }
        public void putTransposed(Matrix4x3d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(((long) offset << 3), 96), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix4x3d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(offset, 96), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix3d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(((long) offset << 3), 72), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix3d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(offset, 72), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix3x2d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(((long) offset << 3), 48), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix3x2d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(offset, 48), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix2d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(((long) offset << 3), 32), 0); else super.putTransposed(m, offset, dest);
        }
        public void putTransposed(Matrix2d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putTransposed(m, bufferSegment(dest).asSlice(offset, 32), 0); else super.putTransposed(m, offset, dest);
        }
        // --- Float-truncated transposed buffer put ---
        public void putfTransposed(Matrix4d m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putfTransposed(m, bufferSegment(dest).asSlice(((long) offset << 2), 64), 0); else super.putfTransposed(m, offset, dest);
        }
        public void putfTransposed(Matrix4d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putfTransposed(m, bufferSegment(dest).asSlice(offset, 64), 0); else super.putfTransposed(m, offset, dest);
        }
        public void putfTransposed(Matrix4x3d m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putfTransposed(m, bufferSegment(dest).asSlice(((long) offset << 2), 48), 0); else super.putfTransposed(m, offset, dest);
        }
        public void putfTransposed(Matrix4x3d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putfTransposed(m, bufferSegment(dest).asSlice(offset, 48), 0); else super.putfTransposed(m, offset, dest);
        }
        public void putfTransposed(Matrix3d m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putfTransposed(m, bufferSegment(dest).asSlice(((long) offset << 2), 36), 0); else super.putfTransposed(m, offset, dest);
        }
        public void putfTransposed(Matrix3d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putfTransposed(m, bufferSegment(dest).asSlice(offset, 36), 0); else super.putfTransposed(m, offset, dest);
        }
        public void putfTransposed(Matrix3x2d m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putfTransposed(m, bufferSegment(dest).asSlice(((long) offset << 2), 24), 0); else super.putfTransposed(m, offset, dest);
        }
        public void putfTransposed(Matrix3x2d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putfTransposed(m, bufferSegment(dest).asSlice(offset, 24), 0); else super.putfTransposed(m, offset, dest);
        }
        public void putfTransposed(Matrix2d m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putfTransposed(m, bufferSegment(dest).asSlice(((long) offset << 2), 16), 0); else super.putfTransposed(m, offset, dest);
        }
        public void putfTransposed(Matrix2d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putfTransposed(m, bufferSegment(dest).asSlice(offset, 16), 0); else super.putfTransposed(m, offset, dest);
        }
        // --- Matrix3f/3d/3x2f/3x2d/2f/2d buffer put ---
        public void put(Matrix3f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(((long) offset << 2), 36), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix3f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(offset, 36), 0); else super.put(m, offset, dest);
        }
        public void put3x4(Matrix3f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put3x4(m, bufferSegment(dest).asSlice(((long) offset << 2), 48), 0); else super.put3x4(m, offset, dest);
        }
        public void put3x4(Matrix3f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put3x4(m, bufferSegment(dest).asSlice(offset, 48), 0); else super.put3x4(m, offset, dest);
        }
        public void put(Matrix3d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(((long) offset << 3), 72), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix3d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(offset, 72), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix3x2f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(((long) offset << 2), 24), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix3x2f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(offset, 24), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix3x2d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(((long) offset << 3), 48), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix3x2d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(offset, 48), 0); else super.put(m, offset, dest);
        }
        public void putf(Matrix3d m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putf(m, bufferSegment(dest).asSlice(((long) offset << 2), 36), 0); else super.putf(m, offset, dest);
        }
        public void putf(Matrix3d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putf(m, bufferSegment(dest).asSlice(offset, 36), 0); else super.putf(m, offset, dest);
        }
        public void put(Matrix2f m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(((long) offset << 2), 16), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix2f m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(offset, 16), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix2d m, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(((long) offset << 3), 32), 0); else super.put(m, offset, dest);
        }
        public void put(Matrix2d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(m, bufferSegment(dest).asSlice(offset, 32), 0); else super.put(m, offset, dest);
        }
        public void putf(Matrix2d m, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putf(m, bufferSegment(dest).asSlice(((long) offset << 2), 16), 0); else super.putf(m, offset, dest);
        }
        public void putf(Matrix2d m, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putf(m, bufferSegment(dest).asSlice(offset, 16), 0); else super.putf(m, offset, dest);
        }
        // --- Vector buffer put ---
        public void put(Vector4d src, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(((long) offset << 3), 32), 0); else super.put(src, offset, dest);
        }
        public void put(Vector4d src, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putf(src, bufferSegment(dest).asSlice(((long) offset << 2), 16), 0); else super.put(src, offset, dest);
        }
        public void put(Vector4d src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(offset, 32), 0); else super.put(src, offset, dest);
        }
        public void putf(Vector4d src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putf(src, bufferSegment(dest).asSlice(offset, 16), 0); else super.putf(src, offset, dest);
        }
        public void put(Vector4f src, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(((long) offset << 2), 16), 0); else super.put(src, offset, dest);
        }
        public void put(Vector4f src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(offset, 16), 0); else super.put(src, offset, dest);
        }
        public void put(Vector4i src, int offset, IntBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(((long) offset << 2), 16), 0); else super.put(src, offset, dest);
        }
        public void put(Vector4i src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(offset, 16), 0); else super.put(src, offset, dest);
        }
        public void put(Vector4L src, int offset, LongBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(((long) offset << 3), 32), 0); else super.put(src, offset, dest);
        }
        public void put(Vector4L src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(offset, 32), 0); else super.put(src, offset, dest);
        }
        public void put(Vector3f src, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(((long) offset << 2), 12), 0); else super.put(src, offset, dest);
        }
        public void put(Vector3f src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(offset, 12), 0); else super.put(src, offset, dest);
        }
        public void put(Vector3d src, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(((long) offset << 3), 24), 0); else super.put(src, offset, dest);
        }
        public void put(Vector3d src, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) putf(src, bufferSegment(dest).asSlice(((long) offset << 2), 12), 0); else super.put(src, offset, dest);
        }
        public void put(Vector3d src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(offset, 24), 0); else super.put(src, offset, dest);
        }
        public void putf(Vector3d src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) putf(src, bufferSegment(dest).asSlice(offset, 12), 0); else super.putf(src, offset, dest);
        }
        public void put(Vector3i src, int offset, IntBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(((long) offset << 2), 12), 0); else super.put(src, offset, dest);
        }
        public void put(Vector3i src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(offset, 12), 0); else super.put(src, offset, dest);
        }
        public void put(Vector3L src, int offset, LongBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(((long) offset << 3), 24), 0); else super.put(src, offset, dest);
        }
        public void put(Vector3L src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(offset, 24), 0); else super.put(src, offset, dest);
        }
        public void put(Vector2f src, int offset, FloatBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(((long) offset << 2), 8), 0); else super.put(src, offset, dest);
        }
        public void put(Vector2f src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(offset, 8), 0); else super.put(src, offset, dest);
        }
        public void put(Vector2d src, int offset, DoubleBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(((long) offset << 3), 16), 0); else super.put(src, offset, dest);
        }
        public void put(Vector2d src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(offset, 16), 0); else super.put(src, offset, dest);
        }
        public void put(Vector2i src, int offset, IntBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(((long) offset << 2), 8), 0); else super.put(src, offset, dest);
        }
        public void put(Vector2i src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(offset, 8), 0); else super.put(src, offset, dest);
        }
        public void put(Vector2L src, int offset, LongBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(((long) offset << 3), 16), 0); else super.put(src, offset, dest);
        }
        public void put(Vector2L src, int offset, ByteBuffer dest) {
            if (writableDirect(dest)) put(src, bufferSegment(dest).asSlice(offset, 16), 0); else super.put(src, offset, dest);
        }

        // --- Matrix buffer get ---
        public void get(Matrix4f m, int offset, FloatBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(((long) offset << 2), 64), 0); else super.get(m, offset, src);
        }
        public void get(Matrix4f m, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(offset, 64), 0); else super.get(m, offset, src);
        }
        public void getTransposed(Matrix4f m, int offset, FloatBuffer src) {
            if (readableDirect(src)) getTransposed(m, bufferSegment(src).asSlice(((long) offset << 2), 64), 0); else super.getTransposed(m, offset, src);
        }
        public void getTransposed(Matrix4f m, int offset, ByteBuffer src) {
            if (readableDirect(src)) getTransposed(m, bufferSegment(src).asSlice(offset, 64), 0); else super.getTransposed(m, offset, src);
        }
        public void get(Matrix4x3f m, int offset, FloatBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(((long) offset << 2), 48), 0); else super.get(m, offset, src);
        }
        public void get(Matrix4x3f m, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(offset, 48), 0); else super.get(m, offset, src);
        }
        public void get(Matrix4d m, int offset, DoubleBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(((long) offset << 3), 128), 0); else super.get(m, offset, src);
        }
        public void get(Matrix4d m, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(offset, 128), 0); else super.get(m, offset, src);
        }
        public void get(Matrix4x3d m, int offset, DoubleBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(((long) offset << 3), 96), 0); else super.get(m, offset, src);
        }
        public void get(Matrix4x3d m, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(offset, 96), 0); else super.get(m, offset, src);
        }
        public void getf(Matrix4d m, int offset, FloatBuffer src) {
            if (readableDirect(src)) getf(m, bufferSegment(src).asSlice(((long) offset << 2), 64), 0); else super.getf(m, offset, src);
        }
        public void getf(Matrix4d m, int offset, ByteBuffer src) {
            if (readableDirect(src)) getf(m, bufferSegment(src).asSlice(offset, 64), 0); else super.getf(m, offset, src);
        }
        public void getf(Matrix4x3d m, int offset, FloatBuffer src) {
            if (readableDirect(src)) getf(m, bufferSegment(src).asSlice(((long) offset << 2), 48), 0); else super.getf(m, offset, src);
        }
        public void getf(Matrix4x3d m, int offset, ByteBuffer src) {
            if (readableDirect(src)) getf(m, bufferSegment(src).asSlice(offset, 48), 0); else super.getf(m, offset, src);
        }
        public void get(Matrix3f m, int offset, FloatBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(((long) offset << 2), 36), 0); else super.get(m, offset, src);
        }
        public void get(Matrix3f m, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(offset, 36), 0); else super.get(m, offset, src);
        }
        public void get(Matrix3d m, int offset, DoubleBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(((long) offset << 3), 72), 0); else super.get(m, offset, src);
        }
        public void get(Matrix3d m, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(offset, 72), 0); else super.get(m, offset, src);
        }
        public void get(Matrix3x2f m, int offset, FloatBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(((long) offset << 2), 24), 0); else super.get(m, offset, src);
        }
        public void get(Matrix3x2f m, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(offset, 24), 0); else super.get(m, offset, src);
        }
        public void get(Matrix3x2d m, int offset, DoubleBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(((long) offset << 3), 48), 0); else super.get(m, offset, src);
        }
        public void get(Matrix3x2d m, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(offset, 48), 0); else super.get(m, offset, src);
        }
        public void getf(Matrix3d m, int offset, FloatBuffer src) {
            if (readableDirect(src)) getf(m, bufferSegment(src).asSlice(((long) offset << 2), 36), 0); else super.getf(m, offset, src);
        }
        public void getf(Matrix3d m, int offset, ByteBuffer src) {
            if (readableDirect(src)) getf(m, bufferSegment(src).asSlice(offset, 36), 0); else super.getf(m, offset, src);
        }
        public void get(Matrix2f m, int offset, FloatBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(((long) offset << 2), 16), 0); else super.get(m, offset, src);
        }
        public void get(Matrix2f m, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(offset, 16), 0); else super.get(m, offset, src);
        }
        public void get(Matrix2d m, int offset, DoubleBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(((long) offset << 3), 32), 0); else super.get(m, offset, src);
        }
        public void get(Matrix2d m, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(m, bufferSegment(src).asSlice(offset, 32), 0); else super.get(m, offset, src);
        }
        public void getf(Matrix2d m, int offset, FloatBuffer src) {
            if (readableDirect(src)) getf(m, bufferSegment(src).asSlice(((long) offset << 2), 16), 0); else super.getf(m, offset, src);
        }
        public void getf(Matrix2d m, int offset, ByteBuffer src) {
            if (readableDirect(src)) getf(m, bufferSegment(src).asSlice(offset, 16), 0); else super.getf(m, offset, src);
        }
        // --- Vector buffer get ---
        public void get(Vector4d dst, int offset, DoubleBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(((long) offset << 3), 32), 0); else super.get(dst, offset, src);
        }
        public void get(Vector4d dst, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(offset, 32), 0); else super.get(dst, offset, src);
        }
        public void get(Vector4f dst, int offset, FloatBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(((long) offset << 2), 16), 0); else super.get(dst, offset, src);
        }
        public void get(Vector4f dst, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(offset, 16), 0); else super.get(dst, offset, src);
        }
        public void get(Vector4i dst, int offset, IntBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(((long) offset << 2), 16), 0); else super.get(dst, offset, src);
        }
        public void get(Vector4i dst, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(offset, 16), 0); else super.get(dst, offset, src);
        }
        public void get(Vector4L dst, int offset, LongBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(((long) offset << 3), 32), 0); else super.get(dst, offset, src);
        }
        public void get(Vector4L dst, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(offset, 32), 0); else super.get(dst, offset, src);
        }
        public void get(Vector3f dst, int offset, FloatBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(((long) offset << 2), 12), 0); else super.get(dst, offset, src);
        }
        public void get(Vector3f dst, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(offset, 12), 0); else super.get(dst, offset, src);
        }
        public void get(Vector3d dst, int offset, DoubleBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(((long) offset << 3), 24), 0); else super.get(dst, offset, src);
        }
        public void get(Vector3d dst, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(offset, 24), 0); else super.get(dst, offset, src);
        }
        public void get(Vector3i dst, int offset, IntBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(((long) offset << 2), 12), 0); else super.get(dst, offset, src);
        }
        public void get(Vector3i dst, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(offset, 12), 0); else super.get(dst, offset, src);
        }
        public void get(Vector3L dst, int offset, LongBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(((long) offset << 3), 24), 0); else super.get(dst, offset, src);
        }
        public void get(Vector3L dst, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(offset, 24), 0); else super.get(dst, offset, src);
        }
        public void get(Vector2f dst, int offset, FloatBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(((long) offset << 2), 8), 0); else super.get(dst, offset, src);
        }
        public void get(Vector2f dst, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(offset, 8), 0); else super.get(dst, offset, src);
        }
        public void get(Vector2d dst, int offset, DoubleBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(((long) offset << 3), 16), 0); else super.get(dst, offset, src);
        }
        public void get(Vector2d dst, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(offset, 16), 0); else super.get(dst, offset, src);
        }
        public void get(Vector2i dst, int offset, IntBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(((long) offset << 2), 8), 0); else super.get(dst, offset, src);
        }
        public void get(Vector2i dst, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(offset, 8), 0); else super.get(dst, offset, src);
        }
        public void get(Vector2L dst, int offset, LongBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(((long) offset << 3), 16), 0); else super.get(dst, offset, src);
        }
        public void get(Vector2L dst, int offset, ByteBuffer src) {
            if (readableDirect(src)) get(dst, bufferSegment(src).asSlice(offset, 16), 0); else super.get(dst, offset, src);
        }
    }
//#endif

//#ifdef __HAS_UNSAFE__
    public static class MemUtilUnsafe extends MemUtilNIO {
        /**
         * Bounds check for the raw-address fast path. Always on: the address path bypasses NIO's own
         * index checks, so a bad offset would otherwise corrupt memory outside the buffer. Mirrors the
         * NIO fallback, which checks absolute indices against {@code limit()}.
         */
        private static void checkBounds(int offset, int limit, int size) {
            if (offset < 0 || size > limit - offset)
                throw new IndexOutOfBoundsException("offset=" + offset + ", size=" + size + ", limit=" + limit);
        }

        public static final sun.misc.Unsafe UNSAFE;

        public static final long ADDRESS;
        public static final long Matrix2f_m00;
        public static final long Matrix3f_m00;
        public static final long Matrix3d_m00;
        public static final long Matrix4f_m00;
        public static final long Matrix4d_m00;
        public static final long Matrix4x3f_m00;
        public static final long Matrix3x2f_m00;
        public static final long Vector4f_x;
        public static final long Vector4i_x;
        public static final long Vector3f_x;
        public static final long Vector3i_x;
        public static final long Vector2f_x;
        public static final long Vector2i_x;
        public static final long floatArrayOffset;

        static {
            UNSAFE = getUnsafeInstance();
            try {
                ADDRESS = findBufferAddress();
                Matrix4f_m00 = checkMatrix4f();
                Matrix4d_m00 = checkMatrix4d();
                Matrix4x3f_m00 = checkMatrix4x3f();
                Matrix3f_m00 = checkMatrix3f();
                Matrix3d_m00 = checkMatrix3d();
                Matrix3x2f_m00 = checkMatrix3x2f();
                Matrix2f_m00 = checkMatrix2f();
                Vector4f_x = checkVector4f();
                Vector4i_x = checkVector4i();
                Vector3f_x = checkVector3f();
                Vector3i_x = checkVector3i();
                Vector2f_x = checkVector2f();
                Vector2i_x = checkVector2i();
                floatArrayOffset = UNSAFE.arrayBaseOffset(float[].class);
                // Under --sun-misc-unsafe-memory-access=deny the lookups above can succeed while every memory
                // access throws. Probe one access so that selection falls back to another backend (review H-9).
                float[] probe = new float[1];
                UNSAFE.putFloat(probe, floatArrayOffset, 1.0f);
                if (UNSAFE.getFloat(probe, floatArrayOffset) != 1.0f)
                    throw new UnsupportedOperationException("sun.misc.Unsafe memory access is not functional");
                // Check if we can use object field offset/address put/get methods
                sun.misc.Unsafe.class.getDeclaredMethod("getLong", new Class[] {Object.class, long.class});
                sun.misc.Unsafe.class.getDeclaredMethod("putLong", new Class[] {Object.class, long.class, long.class});
            } catch (NoSuchFieldException e) {
                throw new UnsupportedOperationException(e);
            } catch (NoSuchMethodException e) {
                throw new UnsupportedOperationException(e);
            }
        }

        private static long findBufferAddress() {
            try {
                return UNSAFE.objectFieldOffset(getDeclaredField(Buffer.class, "address")); //$NON-NLS-1$
            } catch (Exception e) {
                throw new UnsupportedOperationException(e);
            }
        }

        private static long checkMatrix4f() throws NoSuchFieldException, SecurityException {
            Field f = Matrix4f.class.getDeclaredField("m00");
            long Matrix4f_m00 = UNSAFE.objectFieldOffset(f);
            // Validate expected field offsets
            for (int i = 1; i < 16; i++) {
                int c = i >>> 2;
                int r = i & 3;
                f = Matrix4f.class.getDeclaredField("m" + c + r);
                long offset = UNSAFE.objectFieldOffset(f);
                if (offset != Matrix4f_m00 + (i << 2))
                    throw new UnsupportedOperationException("Unexpected Matrix4f element offset");
            }
            return Matrix4f_m00;
        }

        private static long checkMatrix4d() throws NoSuchFieldException, SecurityException {
            Field f = Matrix4d.class.getDeclaredField("m00");
            long Matrix4d_m00 = UNSAFE.objectFieldOffset(f);
            // Validate expected field offsets
            for (int i = 1; i < 16; i++) {
                int c = i >>> 2;
                int r = i & 3;
                f = Matrix4d.class.getDeclaredField("m" + c + r);
                long offset = UNSAFE.objectFieldOffset(f);
                if (offset != Matrix4d_m00 + (i << 3))
                    throw new UnsupportedOperationException("Unexpected Matrix4d element offset");
            }
            return Matrix4d_m00;
        }

        private static long checkMatrix4x3f() throws NoSuchFieldException, SecurityException {
            Field f = Matrix4x3f.class.getDeclaredField("m00");
            long Matrix4x3f_m00 = UNSAFE.objectFieldOffset(f);
            // Validate expected field offsets
            for (int i = 1; i < 12; i++) {
                int c = i / 3;
                int r = i % 3;
                f = Matrix4x3f.class.getDeclaredField("m" + c + r);
                long offset = UNSAFE.objectFieldOffset(f);
                if (offset != Matrix4x3f_m00 + (i << 2))
                    throw new UnsupportedOperationException("Unexpected Matrix4x3f element offset");
            }
            return Matrix4x3f_m00;
        }

        private static long checkMatrix3f() throws NoSuchFieldException, SecurityException {
            Field f = Matrix3f.class.getDeclaredField("m00");
            long Matrix3f_m00 = UNSAFE.objectFieldOffset(f);
            // Validate expected field offsets
            for (int i = 1; i < 9; i++) {
                int c = i / 3;
                int r = i % 3;
                f = Matrix3f.class.getDeclaredField("m" + c + r);
                long offset = UNSAFE.objectFieldOffset(f);
                if (offset != Matrix3f_m00 + (i << 2))
                    throw new UnsupportedOperationException("Unexpected Matrix3f element offset");
            }
            return Matrix3f_m00;
        }

        private static long checkMatrix3d() throws NoSuchFieldException, SecurityException {
            Field f = Matrix3d.class.getDeclaredField("m00");
            long Matrix3d_m00 = UNSAFE.objectFieldOffset(f);
            // Validate expected field offsets
            for (int i = 1; i < 9; i++) {
                int c = i / 3;
                int r = i % 3;
                f = Matrix3d.class.getDeclaredField("m" + c + r);
                long offset = UNSAFE.objectFieldOffset(f);
                if (offset != Matrix3d_m00 + (i << 3))
                    throw new UnsupportedOperationException("Unexpected Matrix3d element offset");
            }
            return Matrix3d_m00;
        }

        private static long checkMatrix3x2f() throws NoSuchFieldException, SecurityException {
            Field f = Matrix3x2f.class.getDeclaredField("m00");
            long Matrix3x2f_m00 = UNSAFE.objectFieldOffset(f);
            // Validate expected field offsets
            for (int i = 1; i < 6; i++) {
                int c = i / 2;
                int r = i % 2;
                f = Matrix3x2f.class.getDeclaredField("m" + c + r);
                long offset = UNSAFE.objectFieldOffset(f);
                if (offset != Matrix3x2f_m00 + (i << 2))
                    throw new UnsupportedOperationException("Unexpected Matrix3x2f element offset");
            }
            return Matrix3x2f_m00;
        }

        private static long checkMatrix2f() throws NoSuchFieldException, SecurityException {
            Field f = Matrix2f.class.getDeclaredField("m00");
            long Matrix2f_m00 = UNSAFE.objectFieldOffset(f);
            // Validate expected field offsets
            for (int i = 1; i < 4; i++) {
                int c = i / 2;
                int r = i % 2;
                f = Matrix2f.class.getDeclaredField("m" + c + r);
                long offset = UNSAFE.objectFieldOffset(f);
                if (offset != Matrix2f_m00 + (i << 2))
                    throw new UnsupportedOperationException("Unexpected Matrix2f element offset");
            }
            return Matrix2f_m00;
        }

        private static long checkVector4f() throws NoSuchFieldException, SecurityException {
            Field f = Vector4f.class.getDeclaredField("x");
            long Vector4f_x = UNSAFE.objectFieldOffset(f);
            // Validate expected field offsets
            String[] names = {"y", "z", "w"};
            for (int i = 1; i < 4; i++) {
                f = Vector4f.class.getDeclaredField(names[i-1]);
                long offset = UNSAFE.objectFieldOffset(f);
                if (offset != Vector4f_x + (i << 2))
                    throw new UnsupportedOperationException("Unexpected Vector4f element offset");
            }
            return Vector4f_x;
        }

        private static long checkVector4i() throws NoSuchFieldException, SecurityException {
            Field f = Vector4i.class.getDeclaredField("x");
            long Vector4i_x = UNSAFE.objectFieldOffset(f);
            // Validate expected field offsets
            String[] names = {"y", "z", "w"};
            for (int i = 1; i < 4; i++) {
                f = Vector4i.class.getDeclaredField(names[i-1]);
                long offset = UNSAFE.objectFieldOffset(f);
                if (offset != Vector4i_x + (i << 2))
                    throw new UnsupportedOperationException("Unexpected Vector4i element offset");
            }
            return Vector4i_x;
        }

        private static long checkVector3f() throws NoSuchFieldException, SecurityException {
            Field f = Vector3f.class.getDeclaredField("x");
            long Vector3f_x = UNSAFE.objectFieldOffset(f);
            // Validate expected field offsets
            String[] names = {"y", "z"};
            for (int i = 1; i < 3; i++) {
                f = Vector3f.class.getDeclaredField(names[i-1]);
                long offset = UNSAFE.objectFieldOffset(f);
                if (offset != Vector3f_x + (i << 2))
                    throw new UnsupportedOperationException("Unexpected Vector3f element offset");
            }
            return Vector3f_x;
        }

        private static long checkVector3i() throws NoSuchFieldException, SecurityException {
            Field f = Vector3i.class.getDeclaredField("x");
            long Vector3i_x = UNSAFE.objectFieldOffset(f);
            // Validate expected field offsets
            String[] names = {"y", "z"};
            for (int i = 1; i < 3; i++) {
                f = Vector3i.class.getDeclaredField(names[i-1]);
                long offset = UNSAFE.objectFieldOffset(f);
                if (offset != Vector3i_x + (i << 2))
                    throw new UnsupportedOperationException("Unexpected Vector3i element offset");
            }
            return Vector3i_x;
        }

        private static long checkVector2f() throws NoSuchFieldException, SecurityException {
            Field f = Vector2f.class.getDeclaredField("x");
            long Vector2f_x = UNSAFE.objectFieldOffset(f);
            // Validate expected field offsets
            f = Vector2f.class.getDeclaredField("y");
            long offset = UNSAFE.objectFieldOffset(f);
            if (offset != Vector2f_x + (1 << 2))
                throw new UnsupportedOperationException("Unexpected Vector2f element offset");
            return Vector2f_x;
        }

        private static long checkVector2i() throws NoSuchFieldException, SecurityException {
            Field f = Vector2i.class.getDeclaredField("x");
            long Vector2i_x = UNSAFE.objectFieldOffset(f);
            // Validate expected field offsets
            f = Vector2i.class.getDeclaredField("y");
            long offset = UNSAFE.objectFieldOffset(f);
            if (offset != Vector2i_x + (1 << 2))
                throw new UnsupportedOperationException("Unexpected Vector2i element offset");
            return Vector2i_x;
        }

        private static java.lang.reflect.Field getDeclaredField(Class root, String fieldName) throws NoSuchFieldException {
            Class type = root;
            do {
                try {
                    java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                    return field;
                } catch (NoSuchFieldException e) {
                    type = type.getSuperclass();
                } catch (SecurityException e) {
                    type = type.getSuperclass();
                }
            } while (type != null);
            throw new NoSuchFieldException(fieldName + " does not exist in " + root.getName() + " or any of its superclasses."); //$NON-NLS-1$ //$NON-NLS-2$
        }

        public static sun.misc.Unsafe getUnsafeInstance() throws SecurityException {
            java.lang.reflect.Field[] fields = sun.misc.Unsafe.class.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                java.lang.reflect.Field field = fields[i];
                if (!field.getType().equals(sun.misc.Unsafe.class))
                    continue;
                int modifiers = field.getModifiers();
                if (!(java.lang.reflect.Modifier.isStatic(modifiers) && java.lang.reflect.Modifier.isFinal(modifiers)))
                    continue;
                field.setAccessible(true);
                try {
                    return (sun.misc.Unsafe) field.get(null);
                } catch (IllegalAccessException e) {
                    /* Ignore */
                }
                break;
            }
            throw new UnsupportedOperationException();
        }

        public static void put(Matrix4f m, long destAddr) {
            for (int i = 0; i < 8; i++) {
                UNSAFE.putLong(null, destAddr + (i << 3), UNSAFE.getLong(m, Matrix4f_m00 + (i << 3)));
            }
        }

        public static void put4x3(Matrix4f m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            for (int i = 0; i < 4; i++) {
                u.putLong(null, destAddr + 12 * i, u.getLong(m, Matrix4f_m00 + (i << 4)));
            }
            u.putFloat(null, destAddr +  8, m.m02());
            u.putFloat(null, destAddr + 20, m.m12());
            u.putFloat(null, destAddr + 32, m.m22());
            u.putFloat(null, destAddr + 44, m.m32());
        }

        public static void put3x4(Matrix4f m, long destAddr) {
            for (int i = 0; i < 6; i++) {
                UNSAFE.putLong(null, destAddr + (i << 3), UNSAFE.getLong(m, Matrix4f_m00 + (i << 3)));
            }
        }

        public static void put(Matrix4x3f m, long destAddr) {
            for (int i = 0; i < 6; i++) {
                UNSAFE.putLong(null, destAddr + (i << 3), UNSAFE.getLong(m, Matrix4x3f_m00 + (i << 3)));
            }
        }

        public static void put4x4(Matrix4x3f m, long destAddr) {
            for (int i = 0; i < 4; i++) {
                UNSAFE.putLong(null, destAddr + (i << 4), UNSAFE.getLong(m, Matrix4x3f_m00 + 12 * i));
                long lng = pairInts(UNSAFE.getInt(m, Matrix4x3f_m00 + 8 + 12 * i), 0);
                UNSAFE.putLong(null, destAddr + 8 + (i << 4), lng);
            }
            UNSAFE.putFloat(null, destAddr + 60, 1.0f);
        }

        public static void put3x4(Matrix4x3f m, long destAddr) {
            for (int i = 0; i < 3; i++) {
                UNSAFE.putLong(null, destAddr + (i << 4), UNSAFE.getLong(m, Matrix4x3f_m00 + 12 * i));
                UNSAFE.putFloat(null, destAddr + (i << 4) + 8, UNSAFE.getFloat(m, Matrix4x3f_m00 + 8 + 12 * i));
                UNSAFE.putFloat(null, destAddr + (i << 4) + 12, 0.0f);
            }
        }

        public static void put4x4(Matrix4x3d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putDouble(null, destAddr,       m.m00());
            u.putDouble(null, destAddr + 8,   m.m01());
            u.putDouble(null, destAddr + 16,  m.m02());
            u.putDouble(null, destAddr + 24,  0.0);
            u.putDouble(null, destAddr + 32,  m.m10());
            u.putDouble(null, destAddr + 40,  m.m11());
            u.putDouble(null, destAddr + 48,  m.m12());
            u.putDouble(null, destAddr + 56,  0.0);
            u.putDouble(null, destAddr + 64,  m.m20());
            u.putDouble(null, destAddr + 72,  m.m21());
            u.putDouble(null, destAddr + 80,  m.m22());
            u.putDouble(null, destAddr + 88,  0.0);
            u.putDouble(null, destAddr + 96,  m.m30());
            u.putDouble(null, destAddr + 104, m.m31());
            u.putDouble(null, destAddr + 112, m.m32());
            u.putDouble(null, destAddr + 120, 1.0);
        }

        public static void put4x4(Matrix3x2f m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putLong(null, destAddr,    u.getLong(m, Matrix3x2f_m00));
            u.putLong(null, destAddr+8,  0L);
            u.putLong(null, destAddr+16, u.getLong(m, Matrix3x2f_m00+8));
            u.putLong(null, destAddr+24, 0L);
            u.putLong(null, destAddr+32, 0L);
            u.putLong(null, destAddr+40, pairFloats(1.0f, 0.0f));
            u.putLong(null, destAddr+48, u.getLong(m, Matrix3x2f_m00+16));
            u.putLong(null, destAddr+56, pairFloats(0.0f, 1.0f));
        }

        public static void put4x4(Matrix3x2d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putDouble(null, destAddr,       m.m00());
            u.putDouble(null, destAddr + 8,   m.m01());
            u.putDouble(null, destAddr + 16,  0.0);
            u.putDouble(null, destAddr + 24,  0.0);
            u.putDouble(null, destAddr + 32,  m.m10());
            u.putDouble(null, destAddr + 40,  m.m11());
            u.putDouble(null, destAddr + 48,  0.0);
            u.putDouble(null, destAddr + 56,  0.0);
            u.putDouble(null, destAddr + 64,  0.0);
            u.putDouble(null, destAddr + 72,  0.0);
            u.putDouble(null, destAddr + 80,  1.0);
            u.putDouble(null, destAddr + 88,  0.0);
            u.putDouble(null, destAddr + 96,  m.m20());
            u.putDouble(null, destAddr + 104, m.m21());
            u.putDouble(null, destAddr + 112, 0.0);
            u.putDouble(null, destAddr + 120, 1.0);
        }

        public static void put3x3(Matrix3x2f m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putLong( null, destAddr,    u.getLong(m, Matrix3x2f_m00));
            u.putInt(  null, destAddr+8,  0);
            u.putLong( null, destAddr+12, u.getLong(m, Matrix3x2f_m00+8));
            u.putInt(  null, destAddr+20, 0);
            u.putLong( null, destAddr+24, u.getLong(m, Matrix3x2f_m00+16));
            u.putFloat(null, destAddr+32, 1.0f);
        }

        public static void put3x3(Matrix3x2d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putDouble(null, destAddr,      m.m00());
            u.putDouble(null, destAddr + 8,  m.m01());
            u.putDouble(null, destAddr + 16, 0.0);
            u.putDouble(null, destAddr + 24, m.m10());
            u.putDouble(null, destAddr + 32, m.m11());
            u.putDouble(null, destAddr + 40, 0.0);
            u.putDouble(null, destAddr + 48, m.m20());
            u.putDouble(null, destAddr + 56, m.m21());
            u.putDouble(null, destAddr + 64, 1.0);
        }

        public static void putTransposed(Matrix4f m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, destAddr,      m.m00());
            u.putFloat(null, destAddr + 4,  m.m10());
            u.putFloat(null, destAddr + 8,  m.m20());
            u.putFloat(null, destAddr + 12, m.m30());
            u.putFloat(null, destAddr + 16, m.m01());
            u.putFloat(null, destAddr + 20, m.m11());
            u.putFloat(null, destAddr + 24, m.m21());
            u.putFloat(null, destAddr + 28, m.m31());
            u.putFloat(null, destAddr + 32, m.m02());
            u.putFloat(null, destAddr + 36, m.m12());
            u.putFloat(null, destAddr + 40, m.m22());
            u.putFloat(null, destAddr + 44, m.m32());
            u.putFloat(null, destAddr + 48, m.m03());
            u.putFloat(null, destAddr + 52, m.m13());
            u.putFloat(null, destAddr + 56, m.m23());
            u.putFloat(null, destAddr + 60, m.m33());
        }

        public static void put4x3Transposed(Matrix4f m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, destAddr,      m.m00());
            u.putFloat(null, destAddr + 4,  m.m10());
            u.putFloat(null, destAddr + 8,  m.m20());
            u.putFloat(null, destAddr + 12, m.m30());
            u.putFloat(null, destAddr + 16, m.m01());
            u.putFloat(null, destAddr + 20, m.m11());
            u.putFloat(null, destAddr + 24, m.m21());
            u.putFloat(null, destAddr + 28, m.m31());
            u.putFloat(null, destAddr + 32, m.m02());
            u.putFloat(null, destAddr + 36, m.m12());
            u.putFloat(null, destAddr + 40, m.m22());
            u.putFloat(null, destAddr + 44, m.m32());
        }

        public static void putTransposed(Matrix4x3f m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, destAddr,      m.m00());
            u.putFloat(null, destAddr + 4,  m.m10());
            u.putFloat(null, destAddr + 8,  m.m20());
            u.putFloat(null, destAddr + 12, m.m30());
            u.putFloat(null, destAddr + 16, m.m01());
            u.putFloat(null, destAddr + 20, m.m11());
            u.putFloat(null, destAddr + 24, m.m21());
            u.putFloat(null, destAddr + 28, m.m31());
            u.putFloat(null, destAddr + 32, m.m02());
            u.putFloat(null, destAddr + 36, m.m12());
            u.putFloat(null, destAddr + 40, m.m22());
            u.putFloat(null, destAddr + 44, m.m32());
        }

        public static void putTransposed(Matrix3f m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, destAddr,      m.m00());
            u.putFloat(null, destAddr + 4,  m.m10());
            u.putFloat(null, destAddr + 8,  m.m20());
            u.putFloat(null, destAddr + 12, m.m01());
            u.putFloat(null, destAddr + 16, m.m11());
            u.putFloat(null, destAddr + 20, m.m21());
            u.putFloat(null, destAddr + 24, m.m02());
            u.putFloat(null, destAddr + 28, m.m12());
            u.putFloat(null, destAddr + 32, m.m22());
        }

        public static void putTransposed(Matrix3x2f m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, destAddr,      m.m00());
            u.putFloat(null, destAddr + 4,  m.m10());
            u.putFloat(null, destAddr + 8,  m.m20());
            u.putFloat(null, destAddr + 12, m.m01());
            u.putFloat(null, destAddr + 16, m.m11());
            u.putFloat(null, destAddr + 20, m.m21());
        }

        public static void putTransposed(Matrix2f m, long destAddr) {
            UNSAFE.putFloat(null, destAddr,      m.m00());
            UNSAFE.putFloat(null, destAddr + 4,  m.m10());
            UNSAFE.putFloat(null, destAddr + 8,  m.m01());
            UNSAFE.putFloat(null, destAddr + 12, m.m11());
        }

        public static void put(Matrix4d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putDouble(null, destAddr,       m.m00());
            u.putDouble(null, destAddr + 8,   m.m01());
            u.putDouble(null, destAddr + 16,  m.m02());
            u.putDouble(null, destAddr + 24,  m.m03());
            u.putDouble(null, destAddr + 32,  m.m10());
            u.putDouble(null, destAddr + 40,  m.m11());
            u.putDouble(null, destAddr + 48,  m.m12());
            u.putDouble(null, destAddr + 56,  m.m13());
            u.putDouble(null, destAddr + 64,  m.m20());
            u.putDouble(null, destAddr + 72,  m.m21());
            u.putDouble(null, destAddr + 80,  m.m22());
            u.putDouble(null, destAddr + 88,  m.m23());
            u.putDouble(null, destAddr + 96,  m.m30());
            u.putDouble(null, destAddr + 104, m.m31());
            u.putDouble(null, destAddr + 112, m.m32());
            u.putDouble(null, destAddr + 120, m.m33());
        }

        public static void put(Matrix4x3d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putDouble(null, destAddr,      m.m00());
            u.putDouble(null, destAddr + 8,  m.m01());
            u.putDouble(null, destAddr + 16, m.m02());
            u.putDouble(null, destAddr + 24, m.m10());
            u.putDouble(null, destAddr + 32, m.m11());
            u.putDouble(null, destAddr + 40, m.m12());
            u.putDouble(null, destAddr + 48, m.m20());
            u.putDouble(null, destAddr + 56, m.m21());
            u.putDouble(null, destAddr + 64, m.m22());
            u.putDouble(null, destAddr + 72, m.m30());
            u.putDouble(null, destAddr + 80, m.m31());
            u.putDouble(null, destAddr + 88, m.m32());
        }

        public static void putTransposed(Matrix4d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putDouble(null, destAddr,       m.m00());
            u.putDouble(null, destAddr + 8,   m.m10());
            u.putDouble(null, destAddr + 16,  m.m20());
            u.putDouble(null, destAddr + 24,  m.m30());
            u.putDouble(null, destAddr + 32,  m.m01());
            u.putDouble(null, destAddr + 40,  m.m11());
            u.putDouble(null, destAddr + 48,  m.m21());
            u.putDouble(null, destAddr + 56,  m.m31());
            u.putDouble(null, destAddr + 64,  m.m02());
            u.putDouble(null, destAddr + 72,  m.m12());
            u.putDouble(null, destAddr + 80,  m.m22());
            u.putDouble(null, destAddr + 88,  m.m32());
            u.putDouble(null, destAddr + 96,  m.m03());
            u.putDouble(null, destAddr + 104, m.m13());
            u.putDouble(null, destAddr + 112, m.m23());
            u.putDouble(null, destAddr + 120, m.m33());
        }

        public static void putfTransposed(Matrix4d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, destAddr,      (float)m.m00());
            u.putFloat(null, destAddr + 4,  (float)m.m10());
            u.putFloat(null, destAddr + 8,  (float)m.m20());
            u.putFloat(null, destAddr + 12, (float)m.m30());
            u.putFloat(null, destAddr + 16, (float)m.m01());
            u.putFloat(null, destAddr + 20, (float)m.m11());
            u.putFloat(null, destAddr + 24, (float)m.m21());
            u.putFloat(null, destAddr + 28, (float)m.m31());
            u.putFloat(null, destAddr + 32, (float)m.m02());
            u.putFloat(null, destAddr + 36, (float)m.m12());
            u.putFloat(null, destAddr + 40, (float)m.m22());
            u.putFloat(null, destAddr + 44, (float)m.m32());
            u.putFloat(null, destAddr + 48, (float)m.m03());
            u.putFloat(null, destAddr + 52, (float)m.m13());
            u.putFloat(null, destAddr + 56, (float)m.m23());
            u.putFloat(null, destAddr + 60, (float)m.m33());
        }

        public static void put4x3Transposed(Matrix4d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putDouble(null, destAddr,      m.m00());
            u.putDouble(null, destAddr + 8,  m.m10());
            u.putDouble(null, destAddr + 16, m.m20());
            u.putDouble(null, destAddr + 24, m.m30());
            u.putDouble(null, destAddr + 32, m.m01());
            u.putDouble(null, destAddr + 40, m.m11());
            u.putDouble(null, destAddr + 48, m.m21());
            u.putDouble(null, destAddr + 56, m.m31());
            u.putDouble(null, destAddr + 64, m.m02());
            u.putDouble(null, destAddr + 72, m.m12());
            u.putDouble(null, destAddr + 80, m.m22());
            u.putDouble(null, destAddr + 88, m.m32());
        }

        public static void putTransposed(Matrix4x3d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putDouble(null, destAddr,      m.m00());
            u.putDouble(null, destAddr + 8,  m.m10());
            u.putDouble(null, destAddr + 16, m.m20());
            u.putDouble(null, destAddr + 24, m.m30());
            u.putDouble(null, destAddr + 32, m.m01());
            u.putDouble(null, destAddr + 40, m.m11());
            u.putDouble(null, destAddr + 48, m.m21());
            u.putDouble(null, destAddr + 56, m.m31());
            u.putDouble(null, destAddr + 64, m.m02());
            u.putDouble(null, destAddr + 72, m.m12());
            u.putDouble(null, destAddr + 80, m.m22());
            u.putDouble(null, destAddr + 88, m.m32());
        }

        public static void putTransposed(Matrix3d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putDouble(null, destAddr,      m.m00());
            u.putDouble(null, destAddr + 8,  m.m10());
            u.putDouble(null, destAddr + 16, m.m20());
            u.putDouble(null, destAddr + 24, m.m01());
            u.putDouble(null, destAddr + 32, m.m11());
            u.putDouble(null, destAddr + 40, m.m21());
            u.putDouble(null, destAddr + 48, m.m02());
            u.putDouble(null, destAddr + 56, m.m12());
            u.putDouble(null, destAddr + 64, m.m22());
        }

        public static void putTransposed(Matrix3x2d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putDouble(null, destAddr,      m.m00());
            u.putDouble(null, destAddr + 8,  m.m10());
            u.putDouble(null, destAddr + 16, m.m20());
            u.putDouble(null, destAddr + 24, m.m01());
            u.putDouble(null, destAddr + 32, m.m11());
            u.putDouble(null, destAddr + 40, m.m21());
        }

        public static void putTransposed(Matrix2d m, long destAddr) {
            // was m00, m10, m10, m10
            UNSAFE.putDouble(null, destAddr,      m.m00());
            UNSAFE.putDouble(null, destAddr + 8,  m.m10());
            UNSAFE.putDouble(null, destAddr + 16, m.m01());
            UNSAFE.putDouble(null, destAddr + 24, m.m11());
        }

        public static void putfTransposed(Matrix4x3d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, destAddr,      (float)m.m00());
            u.putFloat(null, destAddr + 4,  (float)m.m10());
            u.putFloat(null, destAddr + 8,  (float)m.m20());
            u.putFloat(null, destAddr + 12, (float)m.m30());
            u.putFloat(null, destAddr + 16, (float)m.m01());
            u.putFloat(null, destAddr + 20, (float)m.m11());
            u.putFloat(null, destAddr + 24, (float)m.m21());
            u.putFloat(null, destAddr + 28, (float)m.m31());
            u.putFloat(null, destAddr + 32, (float)m.m02());
            u.putFloat(null, destAddr + 36, (float)m.m12());
            u.putFloat(null, destAddr + 40, (float)m.m22());
            u.putFloat(null, destAddr + 44, (float)m.m32());
        }

        public static void putfTransposed(Matrix3d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, destAddr,      (float)m.m00());
            u.putFloat(null, destAddr + 4,  (float)m.m10());
            u.putFloat(null, destAddr + 8,  (float)m.m20());
            u.putFloat(null, destAddr + 12, (float)m.m01());
            u.putFloat(null, destAddr + 16, (float)m.m11());
            u.putFloat(null, destAddr + 20, (float)m.m21());
            u.putFloat(null, destAddr + 24, (float)m.m02());
            u.putFloat(null, destAddr + 28, (float)m.m12());
            u.putFloat(null, destAddr + 32, (float)m.m22());
        }

        public static void putfTransposed(Matrix3x2d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, destAddr,      (float)m.m00());
            u.putFloat(null, destAddr + 4,  (float)m.m10());
            u.putFloat(null, destAddr + 8,  (float)m.m20());
            u.putFloat(null, destAddr + 12, (float)m.m01());
            u.putFloat(null, destAddr + 16, (float)m.m11());
            u.putFloat(null, destAddr + 20, (float)m.m21());
        }

        public static void putfTransposed(Matrix2d m, long destAddr) {
            // was m00 four times
            UNSAFE.putFloat(null, destAddr,      (float)m.m00());
            UNSAFE.putFloat(null, destAddr + 4,  (float)m.m10());
            UNSAFE.putFloat(null, destAddr + 8,  (float)m.m01());
            UNSAFE.putFloat(null, destAddr + 12, (float)m.m11());
        }

        public static void putf(Matrix4d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, destAddr,      (float)m.m00());
            u.putFloat(null, destAddr + 4,  (float)m.m01());
            u.putFloat(null, destAddr + 8,  (float)m.m02());
            u.putFloat(null, destAddr + 12, (float)m.m03());
            u.putFloat(null, destAddr + 16, (float)m.m10());
            u.putFloat(null, destAddr + 20, (float)m.m11());
            u.putFloat(null, destAddr + 24, (float)m.m12());
            u.putFloat(null, destAddr + 28, (float)m.m13());
            u.putFloat(null, destAddr + 32, (float)m.m20());
            u.putFloat(null, destAddr + 36, (float)m.m21());
            u.putFloat(null, destAddr + 40, (float)m.m22());
            u.putFloat(null, destAddr + 44, (float)m.m23());
            u.putFloat(null, destAddr + 48, (float)m.m30());
            u.putFloat(null, destAddr + 52, (float)m.m31());
            u.putFloat(null, destAddr + 56, (float)m.m32());
            u.putFloat(null, destAddr + 60, (float)m.m33());
        }

        public static void putf(Matrix4x3d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, destAddr,      (float)m.m00());
            u.putFloat(null, destAddr + 4,  (float)m.m01());
            u.putFloat(null, destAddr + 8,  (float)m.m02());
            u.putFloat(null, destAddr + 12, (float)m.m10());
            u.putFloat(null, destAddr + 16, (float)m.m11());
            u.putFloat(null, destAddr + 20, (float)m.m12());
            u.putFloat(null, destAddr + 24, (float)m.m20());
            u.putFloat(null, destAddr + 28, (float)m.m21());
            u.putFloat(null, destAddr + 32, (float)m.m22());
            u.putFloat(null, destAddr + 36, (float)m.m30());
            u.putFloat(null, destAddr + 40, (float)m.m31());
            u.putFloat(null, destAddr + 44, (float)m.m32());
        }

        public static void put(Matrix3f m, long destAddr) {
            for (int i = 0; i < 4; i++) {
                UNSAFE.putLong(null, destAddr + (i << 3), UNSAFE.getLong(m, Matrix3f_m00 + (i << 3)));
            }
            UNSAFE.putFloat(null, destAddr + 32, m.m22());
        }

        public static void put3x4(Matrix3f m, long destAddr) {
            for (int i = 0; i < 3; i++) {
                UNSAFE.putLong(null, destAddr + (i << 4), UNSAFE.getLong(m, Matrix3f_m00 + 12 * i));
                UNSAFE.putFloat(null, destAddr + (i << 4) + 8, UNSAFE.getFloat(m, Matrix3f_m00 + 8 + 12 * i));
                UNSAFE.putFloat(null, destAddr + (i << 4) + 12, 0.0f);
            }
        }

        public static void put(Matrix3d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putDouble(null, destAddr,      m.m00());
            u.putDouble(null, destAddr + 8,  m.m01());
            u.putDouble(null, destAddr + 16, m.m02());
            u.putDouble(null, destAddr + 24, m.m10());
            u.putDouble(null, destAddr + 32, m.m11());
            u.putDouble(null, destAddr + 40, m.m12());
            u.putDouble(null, destAddr + 48, m.m20());
            u.putDouble(null, destAddr + 56, m.m21());
            u.putDouble(null, destAddr + 64, m.m22());
        }

        public static void put(Matrix3x2f m, long destAddr) {
            for (int i = 0; i < 3; i++) {
                UNSAFE.putLong(null, destAddr + (i << 3), UNSAFE.getLong(m, Matrix3x2f_m00 + (i << 3)));
            }
        }

        public static void put(Matrix3x2d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putDouble(null, destAddr,      m.m00());
            u.putDouble(null, destAddr + 8,  m.m01());
            u.putDouble(null, destAddr + 16, m.m10());
            u.putDouble(null, destAddr + 24, m.m11());
            u.putDouble(null, destAddr + 32, m.m20());
            u.putDouble(null, destAddr + 40, m.m21());
        }

        public static void putf(Matrix3d m, long destAddr) {
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, destAddr,      (float)m.m00());
            u.putFloat(null, destAddr + 4,  (float)m.m01());
            u.putFloat(null, destAddr + 8,  (float)m.m02());
            u.putFloat(null, destAddr + 12, (float)m.m10());
            u.putFloat(null, destAddr + 16, (float)m.m11());
            u.putFloat(null, destAddr + 20, (float)m.m12());
            u.putFloat(null, destAddr + 24, (float)m.m20());
            u.putFloat(null, destAddr + 28, (float)m.m21());
            u.putFloat(null, destAddr + 32, (float)m.m22());
        }

        public static void put(Matrix2f m, long destAddr) {
            UNSAFE.putLong(null, destAddr,     UNSAFE.getLong(m, Matrix2f_m00));
            UNSAFE.putLong(null, destAddr + 8, UNSAFE.getLong(m, Matrix2f_m00 + 8));
        }

        public static void put(Matrix2d m, long destAddr) {
            UNSAFE.putDouble(null, destAddr,      m.m00());
            UNSAFE.putDouble(null, destAddr + 8,  m.m01());
            UNSAFE.putDouble(null, destAddr + 16, m.m10());
            UNSAFE.putDouble(null, destAddr + 24, m.m11());
        }

        public static void putf(Matrix2d m, long destAddr) {
            UNSAFE.putFloat(null, destAddr,      (float)m.m00());
            UNSAFE.putFloat(null, destAddr + 4,  (float)m.m01());
            UNSAFE.putFloat(null, destAddr + 8,  (float)m.m10());
            UNSAFE.putFloat(null, destAddr + 12, (float)m.m11());
        }

        public static void put(Vector4d src, long destAddr) {
            UNSAFE.putDouble(null, destAddr,    src.x);
            UNSAFE.putDouble(null, destAddr+8,  src.y);
            UNSAFE.putDouble(null, destAddr+16, src.z);
            UNSAFE.putDouble(null, destAddr+24, src.w);
        }

        public static void putf(Vector4d src, long destAddr) {
            UNSAFE.putFloat(null, destAddr,    (float) src.x);
            UNSAFE.putFloat(null, destAddr+4,  (float) src.y);
            UNSAFE.putFloat(null, destAddr+8,  (float) src.z);
            UNSAFE.putFloat(null, destAddr+12, (float) src.w);
        }

        public static void put(Vector4f src, long destAddr) {
            UNSAFE.putLong(null, destAddr, UNSAFE.getLong(src, Vector4f_x));
            UNSAFE.putLong(null, destAddr+8, UNSAFE.getLong(src, Vector4f_x+8));
        }

        public static void put(Vector4i src, long destAddr) {
            UNSAFE.putLong(null, destAddr, UNSAFE.getLong(src, Vector4i_x));
            UNSAFE.putLong(null, destAddr+8, UNSAFE.getLong(src, Vector4i_x+8));
        }

        public static void put(Vector4L src, long destAddr) {
            UNSAFE.putLong(null, destAddr, src.x);
            UNSAFE.putLong(null, destAddr+8, src.y);
            UNSAFE.putLong(null, destAddr+16, src.z);
            UNSAFE.putLong(null, destAddr+24, src.w);
        }

        public static void put(Vector3f src, long destAddr) {
            UNSAFE.putLong(null, destAddr, UNSAFE.getLong(src, Vector3f_x));
            UNSAFE.putFloat(null, destAddr+8, src.z);
        }

        public static void put(Vector3d src, long destAddr) {
            UNSAFE.putDouble(null, destAddr,    src.x);
            UNSAFE.putDouble(null, destAddr+8,  src.y);
            UNSAFE.putDouble(null, destAddr+16, src.z);
        }

        public static void putf(Vector3d src, long destAddr) {
            UNSAFE.putFloat(null, destAddr,   (float) src.x);
            UNSAFE.putFloat(null, destAddr+4, (float) src.y);
            UNSAFE.putFloat(null, destAddr+8, (float) src.z);
        }

        public static void put(Vector3i src, long destAddr) {
            UNSAFE.putLong(null, destAddr, UNSAFE.getLong(src, Vector3i_x));
            UNSAFE.putInt(null, destAddr+8, src.z);
        }

        public static void put(Vector3L src, long destAddr) {
            UNSAFE.putLong(null, destAddr, src.x);
            UNSAFE.putLong(null, destAddr+8, src.y);
            UNSAFE.putLong(null, destAddr+16, src.z);
        }

        public static void put(Vector2f src, long destAddr) {
            UNSAFE.putLong(null, destAddr, UNSAFE.getLong(src, Vector2f_x));
        }

        public static void put(Vector2d src, long destAddr) {
            UNSAFE.putDouble(null, destAddr,   src.x);
            UNSAFE.putDouble(null, destAddr+8, src.y);
        }

        public static void put(Vector2i src, long destAddr) {
            UNSAFE.putLong(null, destAddr, UNSAFE.getLong(src, Vector2i_x));
        }

        public static void put(Vector2L src, long destAddr) {
            UNSAFE.putLong(null, destAddr,   src.x);
            UNSAFE.putLong(null, destAddr+8, src.y);
        }

        public static void get(Matrix4f m, long srcAddr) {
            for (int i = 0; i < 8; i++) {
                UNSAFE.putLong(m, Matrix4f_m00 + (i << 3), UNSAFE.getLong(srcAddr + (i << 3)));
            }
        }

        public static void getTransposed(Matrix4f m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getFloat(srcAddr))
             ._m10(u.getFloat(srcAddr + 4))
             ._m20(u.getFloat(srcAddr + 8))
             ._m30(u.getFloat(srcAddr + 12))
             ._m01(u.getFloat(srcAddr + 16))
             ._m11(u.getFloat(srcAddr + 20))
             ._m21(u.getFloat(srcAddr + 24))
             ._m31(u.getFloat(srcAddr + 28))
             ._m02(u.getFloat(srcAddr + 32))
             ._m12(u.getFloat(srcAddr + 36))
             ._m22(u.getFloat(srcAddr + 40))
             ._m32(u.getFloat(srcAddr + 44))
             ._m03(u.getFloat(srcAddr + 48))
             ._m13(u.getFloat(srcAddr + 52))
             ._m23(u.getFloat(srcAddr + 56))
             ._m33(u.getFloat(srcAddr + 60));
        }

        public static void getTransposed(Matrix3f m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getFloat(srcAddr))
             ._m10(u.getFloat(srcAddr + 4))
             ._m20(u.getFloat(srcAddr + 8))
             ._m01(u.getFloat(srcAddr + 12))
             ._m11(u.getFloat(srcAddr + 16))
             ._m21(u.getFloat(srcAddr + 20))
             ._m02(u.getFloat(srcAddr + 24))
             ._m12(u.getFloat(srcAddr + 28))
             ._m22(u.getFloat(srcAddr + 32));
        }

        public static void getTransposed(Matrix4x3f m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getFloat(srcAddr))
             ._m10(u.getFloat(srcAddr + 4))
             ._m20(u.getFloat(srcAddr + 8))
             ._m30(u.getFloat(srcAddr + 12))
             ._m01(u.getFloat(srcAddr + 16))
             ._m11(u.getFloat(srcAddr + 20))
             ._m21(u.getFloat(srcAddr + 24))
             ._m31(u.getFloat(srcAddr + 28))
             ._m02(u.getFloat(srcAddr + 32))
             ._m12(u.getFloat(srcAddr + 36))
             ._m22(u.getFloat(srcAddr + 40))
             ._m32(u.getFloat(srcAddr + 44));
        }

        public static void getTransposed(Matrix3x2f m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getFloat(srcAddr))
             ._m10(u.getFloat(srcAddr + 4))
             ._m20(u.getFloat(srcAddr + 8))
             ._m01(u.getFloat(srcAddr + 12))
             ._m11(u.getFloat(srcAddr + 16))
             ._m21(u.getFloat(srcAddr + 20));
        }

        public static void getTransposed(Matrix2f m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getFloat(srcAddr))
             ._m10(u.getFloat(srcAddr + 4))
             ._m01(u.getFloat(srcAddr + 8))
             ._m11(u.getFloat(srcAddr + 12));
        }

        public static void getTransposed(Matrix2d m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getDouble(srcAddr))
             ._m10(u.getDouble(srcAddr + 8))
             ._m01(u.getDouble(srcAddr + 16))
             ._m11(u.getDouble(srcAddr + 24));
        }

        public static void getTransposed(Matrix4x3d m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getDouble(srcAddr))
             ._m10(u.getDouble(srcAddr + 8))
             ._m20(u.getDouble(srcAddr + 16))
             ._m30(u.getDouble(srcAddr + 24))
             ._m01(u.getDouble(srcAddr + 32))
             ._m11(u.getDouble(srcAddr + 40))
             ._m21(u.getDouble(srcAddr + 48))
             ._m31(u.getDouble(srcAddr + 56))
             ._m02(u.getDouble(srcAddr + 64))
             ._m12(u.getDouble(srcAddr + 72))
             ._m22(u.getDouble(srcAddr + 80))
             ._m32(u.getDouble(srcAddr + 88));
        }

        public static void getTransposed(Matrix3x2d m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getDouble(srcAddr))
             ._m10(u.getDouble(srcAddr + 8))
             ._m20(u.getDouble(srcAddr + 16))
             ._m01(u.getDouble(srcAddr + 24))
             ._m11(u.getDouble(srcAddr + 32))
             ._m21(u.getDouble(srcAddr + 40));
        }

        public static void getTransposed(Matrix3d m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getDouble(srcAddr))
             ._m10(u.getDouble(srcAddr + 8))
             ._m20(u.getDouble(srcAddr + 16))
             ._m01(u.getDouble(srcAddr + 24))
             ._m11(u.getDouble(srcAddr + 32))
             ._m21(u.getDouble(srcAddr + 40))
             ._m02(u.getDouble(srcAddr + 48))
             ._m12(u.getDouble(srcAddr + 56))
             ._m22(u.getDouble(srcAddr + 64));
        }

        public static void getTransposed(Matrix4d m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getDouble(srcAddr))
             ._m10(u.getDouble(srcAddr + 8))
             ._m20(u.getDouble(srcAddr + 16))
             ._m30(u.getDouble(srcAddr + 24))
             ._m01(u.getDouble(srcAddr + 32))
             ._m11(u.getDouble(srcAddr + 40))
             ._m21(u.getDouble(srcAddr + 48))
             ._m31(u.getDouble(srcAddr + 56))
             ._m02(u.getDouble(srcAddr + 64))
             ._m12(u.getDouble(srcAddr + 72))
             ._m22(u.getDouble(srcAddr + 80))
             ._m32(u.getDouble(srcAddr + 88))
             ._m03(u.getDouble(srcAddr + 96))
             ._m13(u.getDouble(srcAddr + 104))
             ._m23(u.getDouble(srcAddr + 112))
             ._m33(u.getDouble(srcAddr + 120));
        }

        public static void get(Matrix4x3f m, long srcAddr) {
            for (int i = 0; i < 6; i++) {
                UNSAFE.putLong(m, Matrix4x3f_m00 + (i << 3), UNSAFE.getLong(srcAddr + (i << 3)));
            }
        }

        public static void get(Matrix4d m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getDouble(null, srcAddr))
            ._m01(u.getDouble(null, srcAddr+8))
            ._m02(u.getDouble(null, srcAddr+16))
            ._m03(u.getDouble(null, srcAddr+24))
            ._m10(u.getDouble(null, srcAddr+32))
            ._m11(u.getDouble(null, srcAddr+40))
            ._m12(u.getDouble(null, srcAddr+48))
            ._m13(u.getDouble(null, srcAddr+56))
            ._m20(u.getDouble(null, srcAddr+64))
            ._m21(u.getDouble(null, srcAddr+72))
            ._m22(u.getDouble(null, srcAddr+80))
            ._m23(u.getDouble(null, srcAddr+88))
            ._m30(u.getDouble(null, srcAddr+96))
            ._m31(u.getDouble(null, srcAddr+104))
            ._m32(u.getDouble(null, srcAddr+112))
            ._m33(u.getDouble(null, srcAddr+120));
        }

        public static void get(Matrix4x3d m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getDouble(null, srcAddr))
            ._m01(u.getDouble(null, srcAddr+8))
            ._m02(u.getDouble(null, srcAddr+16))
            ._m10(u.getDouble(null, srcAddr+24))
            ._m11(u.getDouble(null, srcAddr+32))
            ._m12(u.getDouble(null, srcAddr+40))
            ._m20(u.getDouble(null, srcAddr+48))
            ._m21(u.getDouble(null, srcAddr+56))
            ._m22(u.getDouble(null, srcAddr+64))
            ._m30(u.getDouble(null, srcAddr+72))
            ._m31(u.getDouble(null, srcAddr+80))
            ._m32(u.getDouble(null, srcAddr+88));
        }

        public static void getf(Matrix4d m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getFloat(null, srcAddr))
            ._m01(u.getFloat(null, srcAddr+4))
            ._m02(u.getFloat(null, srcAddr+8))
            ._m03(u.getFloat(null, srcAddr+12))
            ._m10(u.getFloat(null, srcAddr+16))
            ._m11(u.getFloat(null, srcAddr+20))
            ._m12(u.getFloat(null, srcAddr+24))
            ._m13(u.getFloat(null, srcAddr+28))
            ._m20(u.getFloat(null, srcAddr+32))
            ._m21(u.getFloat(null, srcAddr+36))
            ._m22(u.getFloat(null, srcAddr+40))
            ._m23(u.getFloat(null, srcAddr+44))
            ._m30(u.getFloat(null, srcAddr+48))
            ._m31(u.getFloat(null, srcAddr+52))
            ._m32(u.getFloat(null, srcAddr+56))
            ._m33(u.getFloat(null, srcAddr+60));
        }

        public static void getf(Matrix4x3d m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getFloat(null, srcAddr))
            ._m01(u.getFloat(null, srcAddr+4))
            ._m02(u.getFloat(null, srcAddr+8))
            ._m10(u.getFloat(null, srcAddr+12))
            ._m11(u.getFloat(null, srcAddr+16))
            ._m12(u.getFloat(null, srcAddr+20))
            ._m20(u.getFloat(null, srcAddr+24))
            ._m21(u.getFloat(null, srcAddr+28))
            ._m22(u.getFloat(null, srcAddr+32))
            ._m30(u.getFloat(null, srcAddr+36))
            ._m31(u.getFloat(null, srcAddr+40))
            ._m32(u.getFloat(null, srcAddr+44));
        }

        public static void get(Matrix3f m, long srcAddr) {
            for (int i = 0; i < 4; i++) {
                UNSAFE.putLong(m, Matrix3f_m00 + (i << 3), UNSAFE.getLong(null, srcAddr + (i << 3)));
            }
            m._m22(UNSAFE.getFloat(null, srcAddr+32));
        }

        public static void get(Matrix3d m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getDouble(null, srcAddr))
            ._m01(u.getDouble(null, srcAddr+8))
            ._m02(u.getDouble(null, srcAddr+16))
            ._m10(u.getDouble(null, srcAddr+24))
            ._m11(u.getDouble(null, srcAddr+32))
            ._m12(u.getDouble(null, srcAddr+40))
            ._m20(u.getDouble(null, srcAddr+48))
            ._m21(u.getDouble(null, srcAddr+56))
            ._m22(u.getDouble(null, srcAddr+64));
        }

        public static void get(Matrix3x2f m, long srcAddr) {
            for (int i = 0; i < 3; i++) {
                UNSAFE.putLong(m, Matrix3x2f_m00 + (i << 3), UNSAFE.getLong(null, srcAddr + (i << 3)));
            }
        }

        public static void get(Matrix3x2d m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getDouble(null, srcAddr))
            ._m01(u.getDouble(null, srcAddr+8))
            ._m10(u.getDouble(null, srcAddr+16))
            ._m11(u.getDouble(null, srcAddr+24))
            ._m20(u.getDouble(null, srcAddr+32))
            ._m21(u.getDouble(null, srcAddr+40));
        }

        public static void getf(Matrix3d m, long srcAddr) {
            sun.misc.Unsafe u = UNSAFE;
            m._m00(u.getFloat(null, srcAddr))
            ._m01(u.getFloat(null, srcAddr+4))
            ._m02(u.getFloat(null, srcAddr+8))
            ._m10(u.getFloat(null, srcAddr+12))
            ._m11(u.getFloat(null, srcAddr+16))
            ._m12(u.getFloat(null, srcAddr+20))
            ._m20(u.getFloat(null, srcAddr+24))
            ._m21(u.getFloat(null, srcAddr+28))
            ._m22(u.getFloat(null, srcAddr+32));
        }

        public static void get(Matrix2f m, long srcAddr) {
            UNSAFE.putLong(m, Matrix2f_m00,     UNSAFE.getLong(null, srcAddr));
            UNSAFE.putLong(m, Matrix2f_m00 + 8, UNSAFE.getLong(null, srcAddr + 8));
        }

        public static void get(Matrix2d m, long srcAddr) {
            m._m00(UNSAFE.getDouble(null, srcAddr))
            ._m01(UNSAFE.getDouble(null, srcAddr+8))
            ._m10(UNSAFE.getDouble(null, srcAddr+16))
            ._m11(UNSAFE.getDouble(null, srcAddr+24));
        }

        public static void getf(Matrix2d m, long srcAddr) {
            m._m00(UNSAFE.getFloat(null, srcAddr))
            ._m01(UNSAFE.getFloat(null, srcAddr+4))
            ._m10(UNSAFE.getFloat(null, srcAddr+8))
            ._m11(UNSAFE.getFloat(null, srcAddr+12));
        }

        public static void get(Vector4d dst, long srcAddr) {
            dst.x = UNSAFE.getDouble(null, srcAddr);
            dst.y = UNSAFE.getDouble(null, srcAddr+8);
            dst.z = UNSAFE.getDouble(null, srcAddr+16);
            dst.w = UNSAFE.getDouble(null, srcAddr+24);
        }

        public static void get(Vector4f dst, long srcAddr) {
            dst.x = UNSAFE.getFloat(null, srcAddr);
            dst.y = UNSAFE.getFloat(null, srcAddr+4);
            dst.z = UNSAFE.getFloat(null, srcAddr+8);
            dst.w = UNSAFE.getFloat(null, srcAddr+12);
        }

        public static void get(Vector4i dst, long srcAddr) {
            dst.x = UNSAFE.getInt(null, srcAddr);
            dst.y = UNSAFE.getInt(null, srcAddr+4);
            dst.z = UNSAFE.getInt(null, srcAddr+8);
            dst.w = UNSAFE.getInt(null, srcAddr+12);
        }

        public static void get(Vector4L dst, long srcAddr) {
            dst.x = UNSAFE.getLong(null, srcAddr);
            dst.y = UNSAFE.getLong(null, srcAddr+8);
            dst.z = UNSAFE.getLong(null, srcAddr+16);
            dst.w = UNSAFE.getLong(null, srcAddr+24);
        }

        public static void get(Vector3f dst, long srcAddr) {
            dst.x = UNSAFE.getFloat(null, srcAddr);
            dst.y = UNSAFE.getFloat(null, srcAddr+4);
            dst.z = UNSAFE.getFloat(null, srcAddr+8);
        }

        public static void get(Vector3d dst, long srcAddr) {
            dst.x = UNSAFE.getDouble(null, srcAddr);
            dst.y = UNSAFE.getDouble(null, srcAddr+8);
            dst.z = UNSAFE.getDouble(null, srcAddr+16);
        }

        public static void get(Vector3i dst, long srcAddr) {
            dst.x = UNSAFE.getInt(null, srcAddr);
            dst.y = UNSAFE.getInt(null, srcAddr+4);
            dst.z = UNSAFE.getInt(null, srcAddr+8);
        }

        public static void get(Vector3L dst, long srcAddr) {
            dst.x = UNSAFE.getLong(null, srcAddr);
            dst.y = UNSAFE.getLong(null, srcAddr+8);
            dst.z = UNSAFE.getLong(null, srcAddr+16);
        }

        public static void get(Vector2f dst, long srcAddr) {
            dst.x = UNSAFE.getFloat(null, srcAddr);
            dst.y = UNSAFE.getFloat(null, srcAddr+4);
        }

        public static void get(Vector2d dst, long srcAddr) {
            dst.x = UNSAFE.getDouble(null, srcAddr);
            dst.y = UNSAFE.getDouble(null, srcAddr+8);
        }

        public static void get(Vector2i dst, long srcAddr) {
            dst.x = UNSAFE.getInt(null, srcAddr);
            dst.y = UNSAFE.getInt(null, srcAddr+4);
        }

        public static void get(Vector2L dst, long srcAddr) {
            dst.x = UNSAFE.getLong(null, srcAddr);
            dst.y = UNSAFE.getLong(null, srcAddr+8);
        }

        public static void putMatrix3f(Quaternionf q, long addr) {
            // Same expressions as the NIO path and in the same order, so every backend writes identical bytes.
            float w2 = q.w * q.w, x2 = q.x * q.x, y2 = q.y * q.y, z2 = q.z * q.z;
            float zw = q.z * q.w, xy = q.x * q.y, xz = q.x * q.z, yw = q.y * q.w, yz = q.y * q.z, xw = q.x * q.w;
            float m00 = w2 + x2 - z2 - y2, m01 = xy + zw + zw + xy, m02 = xz - yw + xz - yw;
            float m10 = -zw + xy - zw + xy, m11 = y2 - z2 + w2 - x2, m12 = yz + yz + xw + xw;
            float m20 = yw + xz + xz + yw, m21 = yz + yz - xw - xw, m22 = z2 - y2 - x2 + w2;
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, addr + 0, m00);
            u.putFloat(null, addr + 4, m01);
            u.putFloat(null, addr + 8, m02);
            u.putFloat(null, addr + 12, m10);
            u.putFloat(null, addr + 16, m11);
            u.putFloat(null, addr + 20, m12);
            u.putFloat(null, addr + 24, m20);
            u.putFloat(null, addr + 28, m21);
            u.putFloat(null, addr + 32, m22);
        }

        public static void putMatrix4f(Quaternionf q, long addr) {
            // Same expressions as the NIO path and in the same order, so every backend writes identical bytes.
            float w2 = q.w * q.w, x2 = q.x * q.x, y2 = q.y * q.y, z2 = q.z * q.z;
            float zw = q.z * q.w, xy = q.x * q.y, xz = q.x * q.z, yw = q.y * q.w, yz = q.y * q.z, xw = q.x * q.w;
            float m00 = w2 + x2 - z2 - y2, m01 = xy + zw + zw + xy, m02 = xz - yw + xz - yw;
            float m10 = -zw + xy - zw + xy, m11 = y2 - z2 + w2 - x2, m12 = yz + yz + xw + xw;
            float m20 = yw + xz + xz + yw, m21 = yz + yz - xw - xw, m22 = z2 - y2 - x2 + w2;
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, addr + 0, m00);
            u.putFloat(null, addr + 4, m01);
            u.putFloat(null, addr + 8, m02);
            u.putFloat(null, addr + 12, 0.0f);
            u.putFloat(null, addr + 16, m10);
            u.putFloat(null, addr + 20, m11);
            u.putFloat(null, addr + 24, m12);
            u.putFloat(null, addr + 28, 0.0f);
            u.putFloat(null, addr + 32, m20);
            u.putFloat(null, addr + 36, m21);
            u.putFloat(null, addr + 40, m22);
            u.putFloat(null, addr + 44, 0.0f);
            u.putFloat(null, addr + 48, 0.0f);
            u.putFloat(null, addr + 52, 0.0f);
            u.putFloat(null, addr + 56, 0.0f);
            u.putFloat(null, addr + 60, 1.0f);
        }

        public static void putMatrix4x3f(Quaternionf q, long addr) {
            // Same expressions as the NIO path and in the same order, so every backend writes identical bytes.
            float w2 = q.w * q.w, x2 = q.x * q.x, y2 = q.y * q.y, z2 = q.z * q.z;
            float zw = q.z * q.w, xy = q.x * q.y, xz = q.x * q.z, yw = q.y * q.w, yz = q.y * q.z, xw = q.x * q.w;
            float m00 = w2 + x2 - z2 - y2, m01 = xy + zw + zw + xy, m02 = xz - yw + xz - yw;
            float m10 = -zw + xy - zw + xy, m11 = y2 - z2 + w2 - x2, m12 = yz + yz + xw + xw;
            float m20 = yw + xz + xz + yw, m21 = yz + yz - xw - xw, m22 = z2 - y2 - x2 + w2;
            sun.misc.Unsafe u = UNSAFE;
            u.putFloat(null, addr + 0, m00);
            u.putFloat(null, addr + 4, m01);
            u.putFloat(null, addr + 8, m02);
            u.putFloat(null, addr + 12, m10);
            u.putFloat(null, addr + 16, m11);
            u.putFloat(null, addr + 20, m12);
            u.putFloat(null, addr + 24, m20);
            u.putFloat(null, addr + 28, m21);
            u.putFloat(null, addr + 32, m22);
            u.putFloat(null, addr + 36, 0.0f);
            u.putFloat(null, addr + 40, 0.0f);
            u.putFloat(null, addr + 44, 0.0f);
        }

        public void putMatrix3f(Quaternionf q, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9 << 2);
                putMatrix3f(q, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putMatrix3f(q, offset, dest);
        }

        public void putMatrix3f(Quaternionf q, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9);
                putMatrix3f(q, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putMatrix3f(q, offset, dest);
        }

        public void putMatrix4f(Quaternionf q, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16 << 2);
                putMatrix4f(q, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putMatrix4f(q, offset, dest);
        }

        public void putMatrix4f(Quaternionf q, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16);
                putMatrix4f(q, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putMatrix4f(q, offset, dest);
        }

        public void putMatrix4x3f(Quaternionf q, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12 << 2);
                putMatrix4x3f(q, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putMatrix4x3f(q, offset, dest);
        }

        public void putMatrix4x3f(Quaternionf q, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12);
                putMatrix4x3f(q, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putMatrix4x3f(q, offset, dest);
        }

        public void put(Matrix4f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16);
                put(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix4f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16 << 2);
                put(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(m, offset, dest);
        }

        public void put4x3(Matrix4f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12);
                put4x3(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put4x3(m, offset, dest);
        }

        public void put4x3(Matrix4f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12 << 2);
                put4x3(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put4x3(m, offset, dest);
        }

        public void put3x4(Matrix4f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12);
                put3x4(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put3x4(m, offset, dest);
        }

        public void put3x4(Matrix4f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12 << 2);
                put3x4(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put3x4(m, offset, dest);
        }

        public void put(Matrix4x3f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12);
                put(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix4x3f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12 << 2);
                put(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(m, offset, dest);
        }

        public void put4x4(Matrix4x3f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16);
                put4x4(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put4x4(m, offset, dest);
        }

        public void put4x4(Matrix4x3f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16 << 2);
                put4x4(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put4x4(m, offset, dest);
        }

        public void put3x4(Matrix4x3f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12);
                put3x4(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put3x4(m, offset, dest);
        }

        public void put3x4(Matrix4x3f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12 << 2);
                put3x4(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put3x4(m, offset, dest);
        }

        public void put4x4(Matrix4x3d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16);
                put4x4(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.put4x4(m, offset, dest);
        }

        public void put4x4(Matrix4x3d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16 << 3);
                put4x4(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put4x4(m, offset, dest);
        }

        public void put4x4(Matrix3x2f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16);
                put4x4(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put4x4(m, offset, dest);
        }

        public void put4x4(Matrix3x2f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16 << 2);
                put4x4(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put4x4(m, offset, dest);
        }

        public void put4x4(Matrix3x2d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16);
                put4x4(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.put4x4(m, offset, dest);
        }

        public void put4x4(Matrix3x2d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16 << 3);
                put4x4(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put4x4(m, offset, dest);
        }

        public void put3x3(Matrix3x2f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9);
                put3x3(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put3x3(m, offset, dest);
        }

        public void put3x3(Matrix3x2f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9 << 2);
                put3x3(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put3x3(m, offset, dest);
        }

        public void put3x3(Matrix3x2d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9);
                put3x3(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.put3x3(m, offset, dest);
        }

        public void put3x3(Matrix3x2d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9 << 3);
                put3x3(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put3x3(m, offset, dest);
        }

        public void putTransposed(Matrix4f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix4f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16 << 2);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putTransposed(m, offset, dest);
        }

        public void put4x3Transposed(Matrix4f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12);
                put4x3Transposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put4x3Transposed(m, offset, dest);
        }

        public void put4x3Transposed(Matrix4f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12 << 2);
                put4x3Transposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put4x3Transposed(m, offset, dest);
        }

        public void putTransposed(Matrix4x3f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix4x3f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12 << 2);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix3f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix3f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9 << 2);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix3x2f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 6);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix2f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix2f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4 << 2);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putTransposed(m, offset, dest);
        }

        public void put(Matrix4d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16);
                put(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix4d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16 << 3);
                put(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix4x3d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12);
                put(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix4x3d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12 << 3);
                put(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(m, offset, dest);
        }

        public void putf(Matrix4d m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16);
                putf(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putf(m, offset, dest);
        }

        public void putf(Matrix4d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16 << 2);
                putf(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putf(m, offset, dest);
        }

        public void putf(Matrix4x3d m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12);
                putf(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putf(m, offset, dest);
        }

        public void putf(Matrix4x3d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12 << 2);
                putf(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putf(m, offset, dest);
        }

        public void putTransposed(Matrix4d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix4d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16 << 3);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putTransposed(m, offset, dest);
        }

        public void put4x3Transposed(Matrix4d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12);
                put4x3Transposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.put4x3Transposed(m, offset, dest);
        }

        public void put4x3Transposed(Matrix4d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12 << 3);
                put4x3Transposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put4x3Transposed(m, offset, dest);
        }

        public void putTransposed(Matrix4x3d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix4x3d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12 << 3);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix3d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix3d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9 << 3);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix3x2d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 6);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix3x2d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 6 << 3);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix2d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putTransposed(Matrix2d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4 << 3);
                putTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putTransposed(m, offset, dest);
        }

        public void putfTransposed(Matrix4d m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16);
                putfTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putfTransposed(m, offset, dest);
        }

        public void putfTransposed(Matrix4d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 16 << 2);
                putfTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putfTransposed(m, offset, dest);
        }

        public void putfTransposed(Matrix4x3d m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12);
                putfTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putfTransposed(m, offset, dest);
        }

        public void putfTransposed(Matrix4x3d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12 << 2);
                putfTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putfTransposed(m, offset, dest);
        }

        public void putfTransposed(Matrix3d m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9);
                putfTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putfTransposed(m, offset, dest);
        }

        public void putfTransposed(Matrix3d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9 << 2);
                putfTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putfTransposed(m, offset, dest);
        }

        public void putfTransposed(Matrix3x2d m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 6);
                putfTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putfTransposed(m, offset, dest);
        }

        public void putfTransposed(Matrix3x2d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 6 << 2);
                putfTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putfTransposed(m, offset, dest);
        }

        public void putfTransposed(Matrix2d m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4);
                putfTransposed(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putfTransposed(m, offset, dest);
        }

        public void putfTransposed(Matrix2d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4 << 2);
                putfTransposed(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putfTransposed(m, offset, dest);
        }

        public void put(Matrix3f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9);
                put(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix3f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9 << 2);
                put(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(m, offset, dest);
        }

        public void put3x4(Matrix3f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12);
                put3x4(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put3x4(m, offset, dest);
        }

        public void put3x4(Matrix3f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 12 << 2);
                put3x4(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put3x4(m, offset, dest);
        }

        public void put(Matrix3d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9);
                put(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix3d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9 << 3);
                put(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix3x2f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 6);
                put(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix3x2f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 6 << 2);
                put(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix3x2d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 6);
                put(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix3x2d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 6 << 3);
                put(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(m, offset, dest);
        }

        public void putf(Matrix3d m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9);
                putf(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putf(m, offset, dest);
        }

        public void putf(Matrix3d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 9 << 2);
                putf(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putf(m, offset, dest);
        }

        public void put(Matrix2f m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4);
                put(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix2f m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4 << 2);
                put(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix2d m, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4);
                put(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.put(m, offset, dest);
        }

        public void put(Matrix2d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4 << 3);
                put(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(m, offset, dest);
        }

        public void putf(Matrix2d m, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4);
                putf(m, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.putf(m, offset, dest);
        }

        public void putf(Matrix2d m, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4 << 2);
                putf(m, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putf(m, offset, dest);
        }

        public void put(Vector4d src, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4);
                put(src, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector4d src, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4);
                putf(src, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector4d src, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4 << 3);
                put(src, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(src, offset, dest);
        }

        public void putf(Vector4d src, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4 << 2);
                putf(src, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putf(src, offset, dest);
        }

        public void put(Vector4f src, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4);
                put(src, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector4f src, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4 << 2);
                put(src, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector4i src, int offset, IntBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4);
                put(src, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector4i src, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 4 << 2);
                put(src, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector3f src, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 3);
                put(src, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector3f src, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 3 << 2);
                put(src, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector3d src, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 3);
                put(src, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector3d src, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 3);
                putf(src, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector3d src, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 3 << 3);
                put(src, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(src, offset, dest);
        }

        public void putf(Vector3d src, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 3 << 2);
                putf(src, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.putf(src, offset, dest);
        }

        public void put(Vector3i src, int offset, IntBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 3);
                put(src, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector3i src, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 3 << 2);
                put(src, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector2f src, int offset, FloatBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 2);
                put(src, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector2f src, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 2 << 2);
                put(src, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector2d src, int offset, DoubleBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 2);
                put(src, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector2d src, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 2 << 3);
                put(src, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector2i src, int offset, IntBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 2);
                put(src, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 2));
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector2i src, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 2 << 2);
                put(src, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector2L src, int offset, LongBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 2);
                put(src, UNSAFE.getLong(dest, ADDRESS) + ((long) offset << 3));
            } else
                super.put(src, offset, dest);
        }

        public void put(Vector2L src, int offset, ByteBuffer dest) {
            if (dest.isDirect() && !dest.isReadOnly() && dest.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, dest.limit(), 2 << 3);
                put(src, UNSAFE.getLong(dest, ADDRESS) + offset);
            } else
                super.put(src, offset, dest);
        }

        public void get(Matrix4f m, int offset, FloatBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 16);
                get(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix4f m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 16 << 2);
                get(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(m, offset, src);
        }

        public float get(Matrix4f m, int column, int row) {
            return UNSAFE.getFloat(m, Matrix4f_m00 + ((long) column << 4) + ((long) row << 2));
        }

        public Matrix4f set(Matrix4f m, int column, int row, float value) {
            UNSAFE.putFloat(m, Matrix4f_m00 + ((long) column << 4) + ((long) row << 2), value);
            return m;
        }

        public double get(Matrix4d m, int column, int row) {
            return UNSAFE.getDouble(m, Matrix4d_m00 + ((long) column << 5) + ((long) row << 3));
        }

        public Matrix4d set(Matrix4d m, int column, int row, double value) {
            UNSAFE.putDouble(m, Matrix4d_m00 + ((long) column << 5) + ((long) row << 3), value);
            return m;
        }

        public float get(Matrix3f m, int column, int row) {
            return UNSAFE.getFloat(m, Matrix3f_m00 + ((long) column * (3<<2)) + ((long) row << 2));
        }

        public Matrix3f set(Matrix3f m, int column, int row, float value) {
            UNSAFE.putFloat(m, Matrix3f_m00 + ((long) column * (3<<2)) + ((long) row << 2), value);
            return m;
        }

        public double get(Matrix3d m, int column, int row) {
            return UNSAFE.getDouble(m, Matrix3d_m00 + ((long) column * (3<<3)) + ((long) row << 3));
        }

        public Matrix3d set(Matrix3d m, int column, int row, double value) {
            UNSAFE.putDouble(m, Matrix3d_m00 + ((long) column * (3<<3)) + ((long) row << 3), value);
            return m;
        }

        public void get(Matrix4x3f m, int offset, FloatBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 12);
                get(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix4x3f m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 12 << 2);
                get(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix4d m, int offset, DoubleBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 16);
                get(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 3));
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix4d m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 16 << 3);
                get(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix4x3d m, int offset, DoubleBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 12);
                get(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 3));
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix4x3d m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 12 << 3);
                get(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(m, offset, src);
        }

        public void getf(Matrix4d m, int offset, FloatBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 16);
                getf(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.getf(m, offset, src);
        }

        public void getf(Matrix4d m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 16 << 2);
                getf(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.getf(m, offset, src);
        }

        public void getf(Matrix4x3d m, int offset, FloatBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 12);
                getf(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.getf(m, offset, src);
        }

        public void getf(Matrix4x3d m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 12 << 2);
                getf(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.getf(m, offset, src);
        }

        public void get(Matrix3f m, int offset, FloatBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 9);
                get(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix3f m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 9 << 2);
                get(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix3d m, int offset, DoubleBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 9);
                get(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 3));
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix3d m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 9 << 3);
                get(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix3x2f m, int offset, FloatBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 6);
                get(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix3x2f m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 6 << 2);
                get(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix3x2d m, int offset, DoubleBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 6);
                get(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 3));
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix3x2d m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 6 << 3);
                get(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(m, offset, src);
        }

        public void getf(Matrix3d m, int offset, FloatBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 9);
                getf(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.getf(m, offset, src);
        }

        public void getf(Matrix3d m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 9 << 2);
                getf(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.getf(m, offset, src);
        }

        public void get(Matrix2f m, int offset, FloatBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 4);
                get(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix2f m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 4 << 2);
                get(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix2d m, int offset, DoubleBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 4);
                get(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 3));
            } else
                super.get(m, offset, src);
        }

        public void get(Matrix2d m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 4 << 3);
                get(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(m, offset, src);
        }

        public void getf(Matrix2d m, int offset, FloatBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 4);
                getf(m, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.getf(m, offset, src);
        }

        public void getf(Matrix2d m, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 4 << 2);
                getf(m, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.getf(m, offset, src);
        }

        public void get(Vector4d dst, int offset, DoubleBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 4);
                get(dst, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 3));
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector4d dst, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 4 << 3);
                get(dst, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector4f dst, int offset, FloatBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 4);
                get(dst, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector4f dst, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 4 << 2);
                get(dst, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector4i dst, int offset, IntBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 4);
                get(dst, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector4i dst, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 4 << 2);
                get(dst, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector3f dst, int offset, FloatBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 3);
                get(dst, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector3f dst, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 3 << 2);
                get(dst, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector3d dst, int offset, DoubleBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 3);
                get(dst, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 3));
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector3d dst, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 3 << 3);
                get(dst, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector3i dst, int offset, IntBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 3);
                get(dst, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector3i dst, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 3 << 2);
                get(dst, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector2f dst, int offset, FloatBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 2);
                get(dst, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector2f dst, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 2 << 2);
                get(dst, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector2d dst, int offset, DoubleBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 2);
                get(dst, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 3));
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector2d dst, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 2 << 3);
                get(dst, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector2i dst, int offset, IntBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 2);
                get(dst, UNSAFE.getLong(src, ADDRESS) + ((long) offset << 2));
            } else
                super.get(dst, offset, src);
        }

        public void get(Vector2i dst, int offset, ByteBuffer src) {
            if (src.isDirect() && src.order() == ByteOrder.nativeOrder()) {
                checkBounds(offset, src.limit(), 2 << 2);
                get(dst, UNSAFE.getLong(src, ADDRESS) + offset);
            } else
                super.get(dst, offset, src);
        }
    }
//#endif
}
