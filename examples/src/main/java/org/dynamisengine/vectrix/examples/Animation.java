// SPDX-License-Identifier: MIT
package org.dynamisengine.vectrix.examples;

import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.ik.TwoBoneIkf;
import org.dynamisengine.vectrix.soa.SkinningKernels;
import org.dynamisengine.vectrix.soa.TransformSoA;

/**
 * Reach for a target with two-bone IK, then deform a mesh with the resulting pose by linear blend skinning.
 * <p>
 * The arm has a shoulder at the origin, an elbow at (1, 0, 0) and a wrist at (2, 0, 0) in its bind pose. IK gives
 * new joint positions and the rotations that bend the bones there; skinning moves each vertex by a weighted blend
 * of its joints' transforms.
 */
public final class Animation {

    /** The solved joints, the remaining distance to the target, and the skinned vertices. */
    public record Result(Vector3f elbow, Vector3f wrist, float missedBy, float[] x, float[] y, float[] z) {
    }

    static final Vector3f TARGET = new Vector3f(1.2f, 1.1f, 0);
    static final int VERTICES = 21; // along the arm, from shoulder to wrist

    private Animation() {
    }

    public static Result run() {
        // tag::ik[]
        Vector3f shoulder = new Vector3f(0, 0, 0);
        Vector3f elbow = new Vector3f(1, 0, 0);
        Vector3f wrist = new Vector3f(2, 0, 0);
        Vector3f pole = new Vector3f(0, 0, -1); // the elbow bends toward the pole

        Vector3f newElbow = new Vector3f();
        Vector3f newWrist = new Vector3f();
        Quaternionf shoulderDelta = new Quaternionf();
        Quaternionf elbowDelta = new Quaternionf();
        // maxStretch 1: the bones keep their length. Returns how far the wrist ends from the target.
        float missedBy = TwoBoneIkf.solve(shoulder, elbow, wrist, TARGET, pole, 1f, newElbow, newWrist,
            shoulderDelta, elbowDelta);
        // end::ik[]

        // tag::pose[]
        // Skinning transforms map bind-pose positions to posed ones: posedWorld · inverse(bindWorld).
        // Bind pose: both joints unrotated, at the shoulder and the elbow.
        Transformf shoulderBind = new Transformf();
        Transformf elbowBind = new Transformf();
        elbowBind.translation.set(elbow);

        // Posed world transforms: the deltas are world-space rotations, the root's first.
        Transformf shoulderPosed = new Transformf();
        shoulderPosed.rotation.set(shoulderDelta);
        Transformf elbowPosed = new Transformf();
        elbowPosed.translation.set(newElbow);
        elbowDelta.mul(shoulderDelta, elbowPosed.rotation);

        TransformSoA skinning = new TransformSoA(2);
        Transformf inverse = new Transformf();
        Transformf skin = new Transformf();
        skinning.set(0, Transformf.compose(shoulderPosed, shoulderBind.invertRigidFast(inverse), skin));
        skinning.set(1, Transformf.compose(elbowPosed, elbowBind.invertRigidFast(inverse), skin));
        // end::pose[]

        // tag::skin[]
        // Up to four joints per vertex: indices and weights interleaved, four per vertex. Vertices blend from the
        // upper arm (joint 0) to the forearm (joint 1) around the elbow.
        float[] inX = new float[VERTICES];
        float[] inY = new float[VERTICES];
        float[] inZ = new float[VERTICES];
        int[] joints = new int[4 * VERTICES];
        float[] weights = new float[4 * VERTICES];
        for (int v = 0; v < VERTICES; v++) {
            float along = 2f * v / (VERTICES - 1);
            inX[v] = along;
            float w1 = Math.clamp((along - 0.8f) / 0.4f, 0f, 1f);
            joints[4 * v] = 0;
            joints[4 * v + 1] = 1;
            weights[4 * v] = 1 - w1;
            weights[4 * v + 1] = w1; // the other two slots keep weight 0
        }
        float[] outX = new float[VERTICES];
        float[] outY = new float[VERTICES];
        float[] outZ = new float[VERTICES];
        SkinningKernels.skinLbs4(skinning, joints, weights, inX, inY, inZ, outX, outY, outZ, VERTICES);
        // end::skin[]
        return new Result(newElbow, newWrist, missedBy, outX, outY, outZ);
    }

    public static void main(String[] args) {
        Result r = run();
        System.out.println("elbow  " + Format.vector(r.elbow(), 3));
        System.out.println("wrist  " + Format.vector(r.wrist(), 3) + ", target " + Format.vector(TARGET, 3)
            + ", missed by " + r.missedBy());
        int last = VERTICES - 1;
        System.out.println("skinned wrist vertex " + Format.vector(new Vector3f(r.x()[last], r.y()[last], r.z()[last]), 3));
    }
}
