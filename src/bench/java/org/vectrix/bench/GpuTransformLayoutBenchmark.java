/*
 * The MIT License
 *
 * Copyright (c) 2026 JOML
 */
package org.vectrix.bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.vectrix.affine.PackedAffineArray;
import org.vectrix.affine.PackedAffineKernels;
import org.vectrix.affine.Transformf;
import org.vectrix.core.Matrix4f;
import org.vectrix.gpu.GpuTransformLayout;
import org.vectrix.gpu.GpuTransformWriteKernels;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class GpuTransformLayoutBenchmark extends ThroughputBenchmark {
    @Param({"64", "1024", "16384"})
    public int count;

    @Param({"matrix4f", "packedAffine", "std140Like", "std430Like", "instanceCompact"})
    public String layout;

    @Param({"SEQUENTIAL", "RANDOM"})
    public String accessPattern;

    @Param({"matrix", "packedAffine"})
    public String sourceRep;

    private static final int STD140_FLOAT_STRIDE = 20;
    private static final int STD430_FLOAT_STRIDE = 16;

    private Transformf[] transforms;
    private Matrix4f[] matrices;
    private PackedAffineArray packed;
    private int[] order;

    private float[] matrixOut;
    private float[] packedOut;
    private float[] std140Out;
    private float[] std430Out;
    private ByteBuffer compactOut;

    private GpuTransformLayout compactLayout;

    @Setup
    public void setup() {
        transforms = new Transformf[count];
        matrices = new Matrix4f[count];
        packed = new PackedAffineArray(count);
        order = new int[count];

        matrixOut = new float[count * 16];
        packedOut = new float[count * 12];
        std140Out = new float[count * STD140_FLOAT_STRIDE];
        std430Out = new float[count * STD430_FLOAT_STRIDE];

        compactLayout = GpuTransformLayout.compactTRS();
        compactOut = ByteBuffer.allocate(compactLayout.requiredBytes(count)).order(ByteOrder.LITTLE_ENDIAN);

        SplittableRandom rnd = new SplittableRandom(31415926L);
        for (int i = 0; i < count; i++) {
            Transformf t = new Transformf();
            t.translation.set((float) rnd.nextDouble(-200.0, 200.0), (float) rnd.nextDouble(-200.0, 200.0), (float) rnd.nextDouble(-200.0, 200.0));
            t.rotation.identity().rotateXYZ((float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0));
            t.scale.set((float) rnd.nextDouble(0.5, 2.5), (float) rnd.nextDouble(0.5, 2.5), (float) rnd.nextDouble(0.5, 2.5));
            transforms[i] = t;
            matrices[i] = new Matrix4f().translationRotateScale(t.translation, t.rotation, t.scale);
            order[i] = i;
        }
        PackedAffineKernels.trsToPackedAffineBatch(transforms, packed, count);
        if ("RANDOM".equals(accessPattern)) {
            shuffle(order, rnd.split());
        }
    }

    @Benchmark
    public int writeTransformLayoutPath() {
        if ("instanceCompact".equals(layout)) {
            GpuTransformWriteKernels.writeCompactTrs(compactLayout, transforms, order, compactOut, count);
            return compactOut.getInt(0);
        }
        if ("packedAffine".equals(layout) && "packedAffine".equals(sourceRep)) {
            GpuTransformWriteKernels.writePackedAffine(packed, order, packedOut, count);
            return Float.floatToRawIntBits(packedOut[0]);
        }
        if ("matrix4f".equals(layout) && "packedAffine".equals(sourceRep)) {
            GpuTransformWriteKernels.writeMatrix4fFromPackedAffine(packed, order, matrixOut, count);
            return Float.floatToRawIntBits(matrixOut[0]);
        }

        float[] rawPacked = packed.raw();
        int checksum = 0;
        for (int i = 0; i < count; i++) {
            int idx = order[i];
            switch (layout) {
                case "matrix4f": {
                    int dst = i << 4;
                    if ("matrix".equals(sourceRep)) {
                        Matrix4f m = matrices[idx];
                        matrixOut[dst] = m.m00();
                        matrixOut[dst + 1] = m.m01();
                        matrixOut[dst + 2] = m.m02();
                        matrixOut[dst + 3] = m.m03();
                        matrixOut[dst + 4] = m.m10();
                        matrixOut[dst + 5] = m.m11();
                        matrixOut[dst + 6] = m.m12();
                        matrixOut[dst + 7] = m.m13();
                        matrixOut[dst + 8] = m.m20();
                        matrixOut[dst + 9] = m.m21();
                        matrixOut[dst + 10] = m.m22();
                        matrixOut[dst + 11] = m.m23();
                        matrixOut[dst + 12] = m.m30();
                        matrixOut[dst + 13] = m.m31();
                        matrixOut[dst + 14] = m.m32();
                        matrixOut[dst + 15] = m.m33();
                    } else {
                        int src = idx * 12;
                        matrixOut[dst] = rawPacked[src];
                        matrixOut[dst + 1] = rawPacked[src + 1];
                        matrixOut[dst + 2] = rawPacked[src + 2];
                        matrixOut[dst + 3] = rawPacked[src + 3];
                        matrixOut[dst + 4] = rawPacked[src + 4];
                        matrixOut[dst + 5] = rawPacked[src + 5];
                        matrixOut[dst + 6] = rawPacked[src + 6];
                        matrixOut[dst + 7] = rawPacked[src + 7];
                        matrixOut[dst + 8] = rawPacked[src + 8];
                        matrixOut[dst + 9] = rawPacked[src + 9];
                        matrixOut[dst + 10] = rawPacked[src + 10];
                        matrixOut[dst + 11] = rawPacked[src + 11];
                        matrixOut[dst + 12] = 0.0f;
                        matrixOut[dst + 13] = 0.0f;
                        matrixOut[dst + 14] = 0.0f;
                        matrixOut[dst + 15] = 1.0f;
                    }
                    checksum += Float.floatToRawIntBits(matrixOut[dst]);
                    break;
                }
                case "packedAffine": {
                    int dst = i * 12;
                    Matrix4f m = matrices[idx];
                    packedOut[dst] = m.m00();
                    packedOut[dst + 1] = m.m01();
                    packedOut[dst + 2] = m.m02();
                    packedOut[dst + 3] = m.m03();
                    packedOut[dst + 4] = m.m10();
                    packedOut[dst + 5] = m.m11();
                    packedOut[dst + 6] = m.m12();
                    packedOut[dst + 7] = m.m13();
                    packedOut[dst + 8] = m.m20();
                    packedOut[dst + 9] = m.m21();
                    packedOut[dst + 10] = m.m22();
                    packedOut[dst + 11] = m.m23();
                    checksum += Float.floatToRawIntBits(packedOut[dst]);
                    break;
                }
                case "std140Like": {
                    int dst = i * STD140_FLOAT_STRIDE;
                    if ("matrix".equals(sourceRep)) {
                        Matrix4f m = matrices[idx];
                        std140Out[dst] = m.m00();
                        std140Out[dst + 1] = m.m01();
                        std140Out[dst + 2] = m.m02();
                        std140Out[dst + 3] = m.m03();
                        std140Out[dst + 4] = m.m10();
                        std140Out[dst + 5] = m.m11();
                        std140Out[dst + 6] = m.m12();
                        std140Out[dst + 7] = m.m13();
                        std140Out[dst + 8] = m.m20();
                        std140Out[dst + 9] = m.m21();
                        std140Out[dst + 10] = m.m22();
                        std140Out[dst + 11] = m.m23();
                    } else {
                        int src = idx * 12;
                        System.arraycopy(rawPacked, src, std140Out, dst, 12);
                    }
                    std140Out[dst + 16] = 1.0f;
                    checksum += Float.floatToRawIntBits(std140Out[dst]);
                    break;
                }
                case "std430Like": {
                    int dst = i * STD430_FLOAT_STRIDE;
                    if ("matrix".equals(sourceRep)) {
                        Matrix4f m = matrices[idx];
                        std430Out[dst] = m.m00();
                        std430Out[dst + 1] = m.m01();
                        std430Out[dst + 2] = m.m02();
                        std430Out[dst + 3] = m.m03();
                        std430Out[dst + 4] = m.m10();
                        std430Out[dst + 5] = m.m11();
                        std430Out[dst + 6] = m.m12();
                        std430Out[dst + 7] = m.m13();
                        std430Out[dst + 8] = m.m20();
                        std430Out[dst + 9] = m.m21();
                        std430Out[dst + 10] = m.m22();
                        std430Out[dst + 11] = m.m23();
                        std430Out[dst + 15] = 1.0f;
                    } else {
                        int src = idx * 12;
                        System.arraycopy(rawPacked, src, std430Out, dst, 12);
                        std430Out[dst + 15] = 1.0f;
                    }
                    checksum += Float.floatToRawIntBits(std430Out[dst]);
                    break;
                }
                case "instanceCompact":
                default:
                    checksum += 0;
                    break;
            }
        }
        return checksum;
    }

    private static void shuffle(int[] a, SplittableRandom rnd) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }
}
