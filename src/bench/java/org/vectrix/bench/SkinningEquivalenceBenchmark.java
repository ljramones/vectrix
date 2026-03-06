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
import org.vectrix.affine.RigidTransformf;
import org.vectrix.core.Matrix4f;
import org.vectrix.soa.TransformSoA;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SkinningEquivalenceBenchmark extends ThroughputBenchmark {
    @Param({"64", "1024", "16384"})
    public int vertices;

    @Param({"32", "128", "512"})
    public int paletteSize;

    @Param({"legacyLbs", "kernelLbs"})
    public String path;

    @Param({"fullWrite", "blackholeOnly"})
    public String writeMode;

    private Matrix4f[] matrixPalette;
    private TransformSoA rigidPalette;

    private int[] jointIndices;
    private float[] jointWeights;
    private float[] inX;
    private float[] inY;
    private float[] inZ;
    private float[] outX;
    private float[] outY;
    private float[] outZ;

    @Setup
    public void setup() {
        matrixPalette = new Matrix4f[paletteSize];
        rigidPalette = new TransformSoA(paletteSize);

        jointIndices = new int[vertices * 4];
        jointWeights = new float[vertices * 4];

        inX = new float[vertices];
        inY = new float[vertices];
        inZ = new float[vertices];

        outX = new float[vertices];
        outY = new float[vertices];
        outZ = new float[vertices];

        SplittableRandom rnd = new SplittableRandom(4242L);

        RigidTransformf rt = new RigidTransformf();
        for (int j = 0; j < paletteSize; j++) {
            rt.translation.set((float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0));
            rt.rotation.identity().rotateXYZ((float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0));

            matrixPalette[j] = new Matrix4f().translationRotate(rt.translation, rt.rotation);
            rigidPalette.tx[j] = rt.translation.x;
            rigidPalette.ty[j] = rt.translation.y;
            rigidPalette.tz[j] = rt.translation.z;
            rigidPalette.qx[j] = rt.rotation.x;
            rigidPalette.qy[j] = rt.rotation.y;
            rigidPalette.qz[j] = rt.rotation.z;
            rigidPalette.qw[j] = rt.rotation.w;
        }

        for (int i = 0; i < vertices; i++) {
            inX[i] = (float) rnd.nextDouble(-1.0, 1.0);
            inY[i] = (float) rnd.nextDouble(-1.0, 1.0);
            inZ[i] = (float) rnd.nextDouble(-1.0, 1.0);

            int base = i << 2;
            int j0 = rnd.nextInt(paletteSize);
            int j1 = rnd.nextInt(paletteSize);
            int j2 = rnd.nextInt(paletteSize);
            int j3 = rnd.nextInt(paletteSize);
            jointIndices[base] = j0;
            jointIndices[base + 1] = j1;
            jointIndices[base + 2] = j2;
            jointIndices[base + 3] = j3;

            float w0 = rnd.nextFloat();
            float w1 = rnd.nextFloat();
            float w2 = rnd.nextFloat();
            float w3 = rnd.nextFloat();
            float inv = 1.0f / (w0 + w1 + w2 + w3);
            jointWeights[base] = w0 * inv;
            jointWeights[base + 1] = w1 * inv;
            jointWeights[base + 2] = w2 * inv;
            jointWeights[base + 3] = w3 * inv;
        }
    }

    @Benchmark
    public float skinningEquivalent() {
        if ("kernelLbs".equals(path)) {
            return kernelLoop();
        }
        return legacyLoop();
    }

    private float legacyLoop() {
        float acc = 0.0f;
        boolean write = "fullWrite".equals(writeMode);
        for (int i = 0; i < vertices; i++) {
            int base = i << 2;
            float px = inX[i];
            float py = inY[i];
            float pz = inZ[i];

            float ox = 0.0f;
            float oy = 0.0f;
            float oz = 0.0f;

            for (int k = 0; k < 4; k++) {
                int j = jointIndices[base + k];
                float w = jointWeights[base + k];
                Matrix4f m = matrixPalette[j];
                float tx = m.m00() * px + m.m10() * py + m.m20() * pz + m.m30();
                float ty = m.m01() * px + m.m11() * py + m.m21() * pz + m.m31();
                float tz = m.m02() * px + m.m12() * py + m.m22() * pz + m.m32();
                ox += tx * w;
                oy += ty * w;
                oz += tz * w;
            }

            if (write) {
                outX[i] = ox;
                outY[i] = oy;
                outZ[i] = oz;
            } else {
                acc += ox + oy + oz;
            }
        }
        return write ? outX[vertices - 1] + outY[vertices - 1] + outZ[vertices - 1] : acc;
    }

    private float kernelLoop() {
        float acc = 0.0f;
        boolean write = "fullWrite".equals(writeMode);
        for (int i = 0; i < vertices; i++) {
            int base = i << 2;
            int j0 = jointIndices[base];
            int j1 = jointIndices[base + 1];
            int j2 = jointIndices[base + 2];
            int j3 = jointIndices[base + 3];
            float w0 = jointWeights[base];
            float w1 = jointWeights[base + 1];
            float w2 = jointWeights[base + 2];
            float w3 = jointWeights[base + 3];
            float x = inX[i];
            float y = inY[i];
            float z = inZ[i];

            float ox = influence(j0, w0, x, y, z);
            float oy = influenceY(j0, w0, x, y, z);
            float oz = influenceZ(j0, w0, x, y, z);

            ox += influence(j1, w1, x, y, z);
            oy += influenceY(j1, w1, x, y, z);
            oz += influenceZ(j1, w1, x, y, z);

            ox += influence(j2, w2, x, y, z);
            oy += influenceY(j2, w2, x, y, z);
            oz += influenceZ(j2, w2, x, y, z);

            ox += influence(j3, w3, x, y, z);
            oy += influenceY(j3, w3, x, y, z);
            oz += influenceZ(j3, w3, x, y, z);

            if (write) {
                outX[i] = ox;
                outY[i] = oy;
                outZ[i] = oz;
            } else {
                acc += ox + oy + oz;
            }
        }
        return write ? outX[vertices - 1] + outY[vertices - 1] + outZ[vertices - 1] : acc;
    }

    private float influence(int j, float w, float x, float y, float z) {
        float rx = rigidPalette.qx[j], ry = rigidPalette.qy[j], rz = rigidPalette.qz[j], rw = rigidPalette.qw[j];
        float tx2 = 2.0f * (ry * z - rz * y);
        float ty2 = 2.0f * (rz * x - rx * z);
        float tz2 = 2.0f * (rx * y - ry * x);
        float v = x + rw * tx2 + (ry * tz2 - rz * ty2) + rigidPalette.tx[j];
        return v * w;
    }

    private float influenceY(int j, float w, float x, float y, float z) {
        float rx = rigidPalette.qx[j], ry = rigidPalette.qy[j], rz = rigidPalette.qz[j], rw = rigidPalette.qw[j];
        float tx2 = 2.0f * (ry * z - rz * y);
        float ty2 = 2.0f * (rz * x - rx * z);
        float tz2 = 2.0f * (rx * y - ry * x);
        float v = y + rw * ty2 + (rz * tx2 - rx * tz2) + rigidPalette.ty[j];
        return v * w;
    }

    private float influenceZ(int j, float w, float x, float y, float z) {
        float rx = rigidPalette.qx[j], ry = rigidPalette.qy[j], rz = rigidPalette.qz[j], rw = rigidPalette.qw[j];
        float tx2 = 2.0f * (ry * z - rz * y);
        float ty2 = 2.0f * (rz * x - rx * z);
        float tz2 = 2.0f * (rx * y - ry * x);
        float v = z + rw * tz2 + (rx * ty2 - ry * tx2) + rigidPalette.tz[j];
        return v * w;
    }
}
