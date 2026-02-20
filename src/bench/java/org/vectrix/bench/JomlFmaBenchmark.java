/*
 * The MIT License
 *
 * Copyright (c) 2022-2024 JOML
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
package org.vectrix.bench;

import org.vectrix.Matrix4f;
import org.vectrix.Vector3f;
import org.vectrix.Vector4f;
import org.openjdk.jmh.annotations.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.TimeUnit;

/**
 * FMA-enabled JMH benchmarks for JOML++ hot paths.
 * <p>
 * Same benchmarks as {@link JomlBenchmark} but with {@code -Djoml.useMathFma=true}
 * to enable {@link java.lang.Math#fma} intrinsics. Compare results against
 * {@link JomlBenchmark} to measure FMA impact.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@BenchmarkMode(Mode.AverageTime)
@Fork(value = 2, jvmArgsAppend = {"-Djoml.useMathFma=true", "--enable-native-access=ALL-UNNAMED"})
public class JomlFmaBenchmark {

    // Matrices
    private final Matrix4f m4a = new Matrix4f();
    private final Matrix4f m4b = new Matrix4f();
    private final Matrix4f m4dest = new Matrix4f();

    // Affine matrices
    private final Matrix4f m4affineA = new Matrix4f();
    private final Matrix4f m4affineB = new Matrix4f();
    private final Matrix4f m4affineDest = new Matrix4f();

    // Vectors
    private final Vector3f v3a = new Vector3f();
    private final Vector3f v3b = new Vector3f();
    private final Vector3f v3dest = new Vector3f();
    private final Vector4f v4a = new Vector4f();
    private final Vector4f v4b = new Vector4f();
    private final Vector4f v4dest = new Vector4f();

    // Buffers
    private ByteBuffer bb;
    private FloatBuffer fb;

    // Math input
    private float angle;

    @Setup
    public void setup() {
        // Non-trivial general matrix (perspective-like)
        m4a.set(
            1.5f, 0.1f, 0.2f, 0.0f,
            0.1f, 2.0f, 0.3f, 0.0f,
            0.2f, 0.3f, -1.1f, -1.0f,
            0.5f, 0.6f, -2.2f, 0.0f
        );
        m4b.set(
            0.9f, 0.2f, 0.1f, 0.0f,
            0.2f, 1.8f, 0.4f, 0.0f,
            0.1f, 0.4f, -1.3f, -1.0f,
            0.3f, 0.7f, -1.8f, 0.0f
        );

        // Affine matrices: rotation + translation (bottom row = 0,0,0,1)
        m4affineA.translation(1.0f, 2.0f, 3.0f).rotate(0.5f, 0.0f, 1.0f, 0.0f);
        m4affineB.translation(-1.0f, 0.5f, -2.0f).rotate(1.2f, 1.0f, 0.0f, 0.0f);

        // Non-zero vectors
        v3a.set(1.0f, 2.0f, 3.0f);
        v3b.set(4.0f, 5.0f, 6.0f);
        v4a.set(1.0f, 2.0f, 3.0f, 1.0f);
        v4b.set(4.0f, 5.0f, 6.0f, 1.0f);

        // Direct buffers for memory transfer
        bb = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder());
        fb = bb.asFloatBuffer();

        angle = 1.23f;
    }

    // --- Matrix operations ---

    @Benchmark
    public Matrix4f matrix4f_mul() {
        return m4a.mul(m4b, m4dest);
    }

    @Benchmark
    public Matrix4f matrix4f_mulAffine() {
        return m4affineA.mulAffine(m4affineB, m4affineDest);
    }

    @Benchmark
    public Matrix4f matrix4f_invert() {
        return m4a.invert(m4dest);
    }

    @Benchmark
    public Matrix4f matrix4f_invertAffine() {
        return m4affineA.invertAffine(m4affineDest);
    }

    @Benchmark
    public Matrix4f matrix4f_transpose() {
        return m4a.transpose(m4dest);
    }

    // --- Vector operations ---

    @Benchmark
    public float vector4f_dot() {
        return v4a.dot(v4b);
    }

    @Benchmark
    public Vector3f vector3f_normalize() {
        return v3a.normalize(v3dest);
    }

    @Benchmark
    public Vector4f vector4f_normalize() {
        return v4a.normalize(v4dest);
    }

    @Benchmark
    public Vector3f vector3f_cross() {
        return v3a.cross(v3b, v3dest);
    }

    @Benchmark
    public Vector4f vector4f_transform() {
        return v4a.mul(m4a, v4dest);
    }

    // --- Memory transfer ---

    @Benchmark
    public FloatBuffer matrix4f_getFloatBuffer() {
        return m4a.get(fb);
    }

    @Benchmark
    public ByteBuffer matrix4f_getByteBuffer() {
        return m4a.get(bb);
    }

    // --- Math operations ---

    @Benchmark
    public float math_sin() {
        return org.vectrix.Math.sin(angle);
    }

    @Benchmark
    public float math_cos() {
        return org.vectrix.Math.cos(angle);
    }
}
