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

/**
 * Helpers for SNORM/UNORM packing used by vertex formats.
 */
public final class PackedNorm {
    private PackedNorm() {
    }

    public static int packUnorm8(float v) {
        int q = (int) java.lang.Math.round(clamp(v, 0.0f, 1.0f) * 255.0f);
        return q & 0xFF;
    }

    public static float unpackUnorm8(int packed) {
        return (packed & 0xFF) * (1.0f / 255.0f);
    }

    public static int packUnorm16(float v) {
        int q = (int) java.lang.Math.round(clamp(v, 0.0f, 1.0f) * 65535.0f);
        return q & 0xFFFF;
    }

    public static float unpackUnorm16(int packed) {
        return (packed & 0xFFFF) * (1.0f / 65535.0f);
    }

    public static int packSnorm8(float v) {
        float clamped = clamp(v, -1.0f, 1.0f);
        int q = (int) java.lang.Math.round(clamped * 127.0f);
        return q & 0xFF;
    }

    public static float unpackSnorm8(int packed) {
        int s = (byte) (packed & 0xFF);
        return clamp(s * (1.0f / 127.0f), -1.0f, 1.0f);
    }

    public static int packSnorm16(float v) {
        float clamped = clamp(v, -1.0f, 1.0f);
        int q = (int) java.lang.Math.round(clamped * 32767.0f);
        return q & 0xFFFF;
    }

    public static float unpackSnorm16(int packed) {
        int s = (short) (packed & 0xFFFF);
        return clamp(s * (1.0f / 32767.0f), -1.0f, 1.0f);
    }

    public static int packUnorm8x4(float x, float y, float z, float w) {
        return packUnorm8(x) | (packUnorm8(y) << 8) | (packUnorm8(z) << 16) | (packUnorm8(w) << 24);
    }

    public static int packSnorm8x4(float x, float y, float z, float w) {
        return packSnorm8(x) | (packSnorm8(y) << 8) | (packSnorm8(z) << 16) | (packSnorm8(w) << 24);
    }

    private static float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
