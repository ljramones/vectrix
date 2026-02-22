/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.vectrix.affine.DualQuatTransformf;
import org.vectrix.affine.RigidTransformf;
import org.vectrix.core.Quaternionf;
import org.vectrix.core.Vector3f;
import org.vectrix.soa.DualQuatSoA;
import org.vectrix.soa.SkinningKernels;
import org.vectrix.soa.TransformSoA;

class SkinningKernelsTest {
    @Test
    void lbsPackedAndSoAProduceSameResult() {
        Fixture f = Fixture.create(32, 13);
        float[] packedX = new float[f.vertices];
        float[] packedY = new float[f.vertices];
        float[] packedZ = new float[f.vertices];
        float[] soaX = new float[f.vertices];
        float[] soaY = new float[f.vertices];
        float[] soaZ = new float[f.vertices];

        SkinningKernels.skinLbs4(f.joints, f.jointIndices, f.jointWeights, f.inX, f.inY, f.inZ, packedX, packedY, packedZ, f.vertices);
        SkinningKernels.skinLbs4SoA(f.joints, f.j0, f.j1, f.j2, f.j3, f.w0, f.w1, f.w2, f.w3, f.inX, f.inY, f.inZ, soaX, soaY, soaZ, f.vertices);

        for (int i = 0; i < f.vertices; i++) {
            assertEquals(packedX[i], soaX[i], 1E-6f);
            assertEquals(packedY[i], soaY[i], 1E-6f);
            assertEquals(packedZ[i], soaZ[i], 1E-6f);
        }
    }

    @Test
    void dualQuatPackedAndSoAProduceSameResult() {
        Fixture f = Fixture.create(32, 29);
        float[] packedX = new float[f.vertices];
        float[] packedY = new float[f.vertices];
        float[] packedZ = new float[f.vertices];
        float[] soaX = new float[f.vertices];
        float[] soaY = new float[f.vertices];
        float[] soaZ = new float[f.vertices];

        SkinningKernels.skinDualQuat4(f.dqs, f.jointIndices, f.jointWeights, f.inX, f.inY, f.inZ, packedX, packedY, packedZ, f.vertices);
        SkinningKernels.skinDualQuat4SoA(f.dqs, f.j0, f.j1, f.j2, f.j3, f.w0, f.w1, f.w2, f.w3, f.inX, f.inY, f.inZ, soaX, soaY, soaZ, f.vertices);

        for (int i = 0; i < f.vertices; i++) {
            assertEquals(packedX[i], soaX[i], 1E-6f);
            assertEquals(packedY[i], soaY[i], 1E-6f);
            assertEquals(packedZ[i], soaZ[i], 1E-6f);
        }
    }

    @Test
    void lbsMatchesManualReferenceBlend() {
        Fixture f = Fixture.create(16, 71);
        float[] outX = new float[f.vertices];
        float[] outY = new float[f.vertices];
        float[] outZ = new float[f.vertices];
        SkinningKernels.skinLbs4(f.joints, f.jointIndices, f.jointWeights, f.inX, f.inY, f.inZ, outX, outY, outZ, f.vertices);

        Vector3f tmp = new Vector3f();
        for (int i = 0; i < f.vertices; i++) {
            int base = i << 2;
            float ex = 0.0f, ey = 0.0f, ez = 0.0f;
            for (int lane = 0; lane < 4; lane++) {
                int j = f.jointIndices[base + lane];
                float w = f.jointWeights[base + lane];
                f.rigid[j].transformPosition(f.inX[i], f.inY[i], f.inZ[i], tmp);
                ex += tmp.x * w;
                ey += tmp.y * w;
                ez += tmp.z * w;
            }
            assertEquals(ex, outX[i], 1E-5f);
            assertEquals(ey, outY[i], 1E-5f);
            assertEquals(ez, outZ[i], 1E-5f);
        }
    }

    @Test
    void dualQuatZeroWeightsFallsBackToInputPosition() {
        DualQuatSoA dqs = new DualQuatSoA(1);
        int[] ji = {0, 0, 0, 0};
        float[] jw = {0.0f, 0.0f, 0.0f, 0.0f};
        float[] inX = {0.25f};
        float[] inY = {-0.75f};
        float[] inZ = {1.25f};
        float[] outX = {0.0f};
        float[] outY = {0.0f};
        float[] outZ = {0.0f};
        SkinningKernels.skinDualQuat4(dqs, ji, jw, inX, inY, inZ, outX, outY, outZ, 1);
        assertEquals(inX[0], outX[0], 1E-6f);
        assertEquals(inY[0], outY[0], 1E-6f);
        assertEquals(inZ[0], outZ[0], 1E-6f);
    }

    @Test
    void dualQuatBlendIsAntipodalityInvariant() {
        RigidTransformf a = new RigidTransformf(
                new Vector3f(0.1f, -0.3f, 0.7f),
                new Quaternionf().rotateXYZ(0.25f, -0.4f, 0.15f));
        RigidTransformf b = new RigidTransformf(
                new Vector3f(-0.8f, 0.5f, -0.2f),
                new Quaternionf().rotateXYZ(-0.55f, 0.2f, 0.45f));

        DualQuatTransformf dqa = new DualQuatTransformf().setFromRigid(a);
        DualQuatTransformf dqb = new DualQuatTransformf().setFromRigid(b);
        DualQuatTransformf dqbNeg = new DualQuatTransformf().set(dqb);
        dqbNeg.real.mul(-1.0f);
        dqbNeg.dual.mul(-1.0f);

        DualQuatSoA base = new DualQuatSoA(2);
        base.set(0, dqa);
        base.set(1, dqb);
        DualQuatSoA flipped = new DualQuatSoA(2);
        flipped.set(0, dqa);
        flipped.set(1, dqbNeg);

        int[] ji = {0, 1, 0, 0};
        float[] jw = {0.5f, 0.5f, 0.0f, 0.0f};
        float[] inX = {0.35f};
        float[] inY = {-0.2f};
        float[] inZ = {0.9f};

        float[] outBaseX = {0.0f}, outBaseY = {0.0f}, outBaseZ = {0.0f};
        float[] outFlipX = {0.0f}, outFlipY = {0.0f}, outFlipZ = {0.0f};

        SkinningKernels.skinDualQuat4(base, ji, jw, inX, inY, inZ, outBaseX, outBaseY, outBaseZ, 1);
        SkinningKernels.skinDualQuat4(flipped, ji, jw, inX, inY, inZ, outFlipX, outFlipY, outFlipZ, 1);

        assertEquals(outBaseX[0], outFlipX[0], 1E-6f);
        assertEquals(outBaseY[0], outFlipY[0], 1E-6f);
        assertEquals(outBaseZ[0], outFlipZ[0], 1E-6f);
    }

    @Test
    void lbsSoAScalarAndSimdForcedMatch() {
        Fixture f = Fixture.create(129, 991);
        float[] sx = new float[f.vertices];
        float[] sy = new float[f.vertices];
        float[] sz = new float[f.vertices];
        float[] vx = new float[f.vertices];
        float[] vy = new float[f.vertices];
        float[] vz = new float[f.vertices];
        SkinningKernels.skinLbs4SoAScalar(f.joints, f.j0, f.j1, f.j2, f.j3, f.w0, f.w1, f.w2, f.w3, f.inX, f.inY, f.inZ, sx, sy, sz, f.vertices);
        SkinningKernels.skinLbs4SoASimd(f.joints, f.j0, f.j1, f.j2, f.j3, f.w0, f.w1, f.w2, f.w3, f.inX, f.inY, f.inZ, vx, vy, vz, f.vertices);
        for (int i = 0; i < f.vertices; i++) {
            assertEquals(sx[i], vx[i], 1E-6f);
            assertEquals(sy[i], vy[i], 1E-6f);
            assertEquals(sz[i], vz[i], 1E-6f);
        }
    }

    private static final class Fixture {
        static final int JOINTS = 8;
        final int vertices;
        final TransformSoA joints = new TransformSoA(JOINTS);
        final DualQuatSoA dqs = new DualQuatSoA(JOINTS);
        final RigidTransformf[] rigid = new RigidTransformf[JOINTS];
        final int[] jointIndices;
        final float[] jointWeights;
        final int[] j0;
        final int[] j1;
        final int[] j2;
        final int[] j3;
        final float[] w0;
        final float[] w1;
        final float[] w2;
        final float[] w3;
        final float[] inX;
        final float[] inY;
        final float[] inZ;

        private Fixture(int vertices) {
            this.vertices = vertices;
            jointIndices = new int[vertices * 4];
            jointWeights = new float[vertices * 4];
            j0 = new int[vertices];
            j1 = new int[vertices];
            j2 = new int[vertices];
            j3 = new int[vertices];
            w0 = new float[vertices];
            w1 = new float[vertices];
            w2 = new float[vertices];
            w3 = new float[vertices];
            inX = new float[vertices];
            inY = new float[vertices];
            inZ = new float[vertices];
        }

        static Fixture create(int vertices, long seed) {
            Fixture f = new Fixture(vertices);
            SplittableRandom rnd = new SplittableRandom(seed);
            for (int j = 0; j < JOINTS; j++) {
                RigidTransformf r = new RigidTransformf(
                        new Vector3f((float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0)),
                        new Quaternionf().rotateXYZ((float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0), (float) rnd.nextDouble(-1.0, 1.0)));
                f.rigid[j] = r;
                f.joints.tx[j] = r.translation.x;
                f.joints.ty[j] = r.translation.y;
                f.joints.tz[j] = r.translation.z;
                f.joints.qx[j] = r.rotation.x;
                f.joints.qy[j] = r.rotation.y;
                f.joints.qz[j] = r.rotation.z;
                f.joints.qw[j] = r.rotation.w;
                f.dqs.set(j, new DualQuatTransformf().setFromRigid(r));
            }
            for (int i = 0; i < vertices; i++) {
                f.inX[i] = (float) rnd.nextDouble(-1.0, 1.0);
                f.inY[i] = (float) rnd.nextDouble(-1.0, 1.0);
                f.inZ[i] = (float) rnd.nextDouble(-1.0, 1.0);
                int a = rnd.nextInt(JOINTS);
                int b = rnd.nextInt(JOINTS);
                int c = rnd.nextInt(JOINTS);
                int d = rnd.nextInt(JOINTS);
                int base = i << 2;
                f.jointIndices[base] = a;
                f.jointIndices[base + 1] = b;
                f.jointIndices[base + 2] = c;
                f.jointIndices[base + 3] = d;
                f.j0[i] = a;
                f.j1[i] = b;
                f.j2[i] = c;
                f.j3[i] = d;
                float x = rnd.nextFloat();
                float y = rnd.nextFloat();
                float z = rnd.nextFloat();
                float w = rnd.nextFloat();
                float inv = 1.0f / (x + y + z + w);
                f.w0[i] = x * inv;
                f.w1[i] = y * inv;
                f.w2[i] = z * inv;
                f.w3[i] = w * inv;
                f.jointWeights[base] = f.w0[i];
                f.jointWeights[base + 1] = f.w1[i];
                f.jointWeights[base + 2] = f.w2[i];
                f.jointWeights[base + 3] = f.w3[i];
            }
            return f;
        }
    }
}
