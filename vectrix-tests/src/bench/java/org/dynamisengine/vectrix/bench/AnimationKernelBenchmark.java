/*
 * The MIT License
 *
 * Copyright (c) 2026 JOML
 */
package org.dynamisengine.vectrix.bench;

import java.util.SplittableRandom;

import org.dynamisengine.vectrix.animation.PoseKernelsf;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.ik.Ccdf;
import org.dynamisengine.vectrix.ik.DampedLeastSquaresf;
import org.dynamisengine.vectrix.ik.Fabrikf;
import org.dynamisengine.vectrix.ik.TwoBoneIkf;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * IK solvers and pose kernels (2.4.0). The iterative solvers work in place, so each call first restores the chain
 * from a pristine copy; {@link #copyBaseline} measures that copy alone.
 */
@State(Scope.Benchmark)
public class AnimationKernelBenchmark extends LatencyBenchmark {
    /** Chain length for the iterative solvers, and joint count for the pose kernels (times 16). */
    @Param({"4", "16"})
    public int chain;

    private float[] pristine, positions, lengths, deltas, scratch, bendLimits;
    private float tx, ty, tz;
    private final Vector3f root = new Vector3f(0, 0, 0), mid = new Vector3f(0, 1, 0.1f), end = new Vector3f(0, 2, 0);
    private final Vector3f target = new Vector3f(0.6f, 1.2f, 0.4f), pole = new Vector3f(0, 0, 1);
    private final Vector3f midOut = new Vector3f(), endOut = new Vector3f();
    private final Quaternionf rootDelta = new Quaternionf(), midDelta = new Quaternionf();

    private int joints;
    private int[] parents;
    private float[] localT, localR, localS, modelT, modelR, modelS, poseA, poseB, poseC, outR, weights;
    private float[][] poses;

    @Setup
    public void setup() {
        SplittableRandom r = new SplittableRandom(42);
        pristine = new float[3 * chain];
        lengths = new float[chain - 1];
        for (int i = 1; i < chain; i++) {
            pristine[3 * i] = pristine[3 * i - 3] + (float) r.nextDouble(-0.2, 0.2);
            pristine[3 * i + 1] = pristine[3 * i - 2] + 1;
            pristine[3 * i + 2] = pristine[3 * i - 1] + (float) r.nextDouble(-0.2, 0.2);
            float dx = pristine[3 * i] - pristine[3 * i - 3], dy = pristine[3 * i + 1] - pristine[3 * i - 2];
            float dz = pristine[3 * i + 2] - pristine[3 * i - 1];
            lengths[i - 1] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        positions = pristine.clone();
        deltas = new float[4 * chain];
        scratch = new float[4 * chain];
        bendLimits = new float[2 * chain];
        for (int i = 0; i < chain; i++) {
            bendLimits[2 * i] = 0.05f;
            bendLimits[2 * i + 1] = 1.2f;
        }
        // reachable, off the chain's line
        tx = 0.4f * chain;
        ty = 0.6f * (chain - 1);
        tz = 0.2f * chain;

        joints = 16 * chain;
        parents = new int[joints];
        localT = new float[3 * joints];
        localR = new float[4 * joints];
        localS = new float[3 * joints];
        modelT = new float[3 * joints];
        modelR = new float[4 * joints];
        modelS = new float[3 * joints];
        poseA = new float[4 * joints];
        poseB = new float[4 * joints];
        poseC = new float[4 * joints];
        outR = new float[4 * joints];
        for (int j = 0; j < joints; j++) {
            parents[j] = j == 0 ? -1 : r.nextInt(j);
            for (int k = 0; k < 3; k++) {
                localT[3 * j + k] = (float) r.nextDouble(-1, 1);
                localS[3 * j + k] = 1;
            }
            randomUnit(r, localR, j);
            randomUnit(r, poseA, j);
            randomUnit(r, poseB, j);
            randomUnit(r, poseC, j);
        }
        poses = new float[][] {poseA, poseB, poseC};
        weights = new float[] {0.5f, 0.3f, 0.2f};
    }

    private static void randomUnit(SplittableRandom r, float[] q, int j) {
        Quaternionf t = new Quaternionf().rotateXYZ((float) r.nextDouble(-3, 3), (float) r.nextDouble(-3, 3),
                (float) r.nextDouble(-3, 3));
        q[4 * j] = t.x;
        q[4 * j + 1] = t.y;
        q[4 * j + 2] = t.z;
        q[4 * j + 3] = t.w;
    }

    @Benchmark
    public float twoBone() {
        return TwoBoneIkf.solve(root, mid, end, target, pole, 1f, midOut, endOut, rootDelta, midDelta);
    }

    @Benchmark
    public float[] copyBaseline() {
        System.arraycopy(pristine, 0, positions, 0, positions.length);
        return positions;
    }

    @Benchmark
    public float fabrik() {
        System.arraycopy(pristine, 0, positions, 0, positions.length);
        return Fabrikf.solve(positions, chain, lengths, tx, ty, tz, 1e-4f, 20, null);
    }

    @Benchmark
    public float fabrikBendLimits() {
        System.arraycopy(pristine, 0, positions, 0, positions.length);
        return Fabrikf.solve(positions, chain, lengths, tx, ty, tz, 1e-4f, 20, bendLimits);
    }

    @Benchmark
    public float ccd() {
        System.arraycopy(pristine, 0, positions, 0, positions.length);
        return Ccdf.solve(positions, chain, tx, ty, tz, 1e-4f, 20, 0.5f, deltas);
    }

    @Benchmark
    public float dampedLeastSquares() {
        System.arraycopy(pristine, 0, positions, 0, positions.length);
        return DampedLeastSquaresf.solve(positions, chain, tx, ty, tz, 0.1f, 0.5f, 1e-4f, 20, scratch);
    }

    @Benchmark
    public float[] localToModel() {
        PoseKernelsf.localToModel(parents, joints, localT, localR, localS, modelT, modelR, modelS);
        return modelR;
    }

    @Benchmark
    public float[] slerpPose() {
        PoseKernelsf.slerp(poseA, poseB, 0.37f, outR, joints);
        return outR;
    }

    @Benchmark
    public float[] nlerpPose() {
        PoseKernelsf.nlerp(poseA, poseB, 0.37f, outR, joints);
        return outR;
    }

    @Benchmark
    public float[] blendThreePoses() {
        PoseKernelsf.blendRotations(poses, weights, 3, outR, joints);
        return outR;
    }
}
