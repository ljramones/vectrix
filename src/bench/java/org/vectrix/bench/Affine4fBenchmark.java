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
package org.vectrix.bench;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.vectrix.affine.Affine4f;
import org.vectrix.affine.Transformf;
import org.vectrix.core.Matrix4x3f;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class Affine4fBenchmark {
    @Param({"64", "256", "4096"})
    public int size;

    private Transformf[] source;
    private Affine4f[] affine;
    private Affine4f[] affineOut;
    private Matrix4x3f[] matrix;
    private Matrix4x3f[] matrixOut;

    @Setup
    public void setup() {
        source = new Transformf[size];
        affine = new Affine4f[size];
        affineOut = new Affine4f[size];
        matrix = new Matrix4x3f[size];
        matrixOut = new Matrix4x3f[size];
        SplittableRandom rnd = new SplittableRandom(123L);
        for (int i = 0; i < size; i++) {
            Transformf t = randomTransform(rnd);
            source[i] = t;
            affine[i] = t.toAffine4fFast(new Affine4f());
            affineOut[i] = new Affine4f();
            matrix[i] = t.toAffineMat4Fast(new Matrix4x3f());
            matrixOut[i] = new Matrix4x3f();
        }
    }

    @Benchmark
    public Affine4f[] trsToAffine() {
        for (int i = 0; i < size; i++) {
            source[i].toAffine4fFast(affineOut[i]);
        }
        return affineOut;
    }

    @Benchmark
    public Matrix4x3f[] trsToMatrix4x3() {
        for (int i = 0; i < size; i++) {
            source[i].toAffineMat4Fast(matrixOut[i]);
        }
        return matrixOut;
    }

    @Benchmark
    public Affine4f[] mulAffineChain() {
        for (int i = 1; i < size; i++) {
            affineOut[i].set(affine[i - 1]).mul(affine[i], affineOut[i]);
        }
        return affineOut;
    }

    @Benchmark
    public Matrix4x3f[] mulMatrix4x3Chain() {
        for (int i = 1; i < size; i++) {
            matrix[i - 1].mul(matrix[i], matrixOut[i]);
        }
        return matrixOut;
    }

    private static Transformf randomTransform(SplittableRandom rnd) {
        Transformf t = new Transformf();
        t.translation.set((float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0));
        t.rotation.identity().rotateAxis((float) rnd.nextDouble(0.0, java.lang.Math.PI * 2.0),
                (float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0)).normalize();
        t.scale.set((float) rnd.nextDouble(0.5, 2.5), (float) rnd.nextDouble(0.5, 2.5), (float) rnd.nextDouble(0.5, 2.5));
        return t;
    }
}
