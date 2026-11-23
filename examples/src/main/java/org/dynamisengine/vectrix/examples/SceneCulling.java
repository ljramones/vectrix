// SPDX-License-Identifier: MIT
package org.dynamisengine.vectrix.examples;

import org.dynamisengine.vectrix.affine.Transformf;
import org.dynamisengine.vectrix.camera.Projectionsf;
import org.dynamisengine.vectrix.core.DepthRange;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Matrix4x3f;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.geometry.CullingKernels;
import org.dynamisengine.vectrix.geometry.FrustumIntersection;
import org.dynamisengine.vectrix.geometry.FrustumPlanes;
import org.dynamisengine.vectrix.soa.AABBSoA;
import org.dynamisengine.vectrix.soa.TransformKernels;
import org.dynamisengine.vectrix.soa.TransformSoA;

/**
 * A frame of a scene: compose a transform hierarchy in batch, bound every object in world space, and cull the
 * bounds against a reverse-Z camera frustum.
 * <p>
 * The scene is a grid of unit cubes, children of one root that is moved and turned. Batch kernels work on
 * structure-of-arrays storage ({@code TransformSoA}, {@code AABBSoA}): one array per component, which the SIMD
 * kernels of the {@code -simd} jar process several elements at a time.
 */
public final class SceneCulling {

    /** Per-object results: world-space bounds and the culling classification. */
    public record Result(int count, float[] worldMin, float[] worldMax, int[] classification, int visible) {
    }

    static final int SIDE = 32;
    static final float SPACING = 4f;

    private SceneCulling() {
    }

    public static Result run() {
        int n = SIDE * SIDE;

        // tag::hierarchy[]
        // Local transforms of the children, relative to the root: a grid centered on the root.
        TransformSoA locals = new TransformSoA(n);
        Transformf t = new Transformf();
        for (int i = 0; i < n; i++) {
            t.identity();
            t.translation.set((i % SIDE - SIDE / 2) * SPACING, 0, (i / SIDE - SIDE / 2) * SPACING);
            locals.set(i, t);
        }

        // The root's world transform, repeated as the parent of every child. In a deeper hierarchy you compose one
        // level at a time, gathering each level's parents from the level above.
        Transformf root = new Transformf();
        root.translation.set(0, 0, -70);
        root.rotation.rotateY((float) Math.toRadians(20));
        TransformSoA parents = new TransformSoA(n);
        for (int i = 0; i < n; i++) {
            parents.set(i, root);
        }

        // world[i] = parents[i] * locals[i], in one batch call.
        TransformSoA world = new TransformSoA(n);
        TransformKernels.composeBatch(parents, locals, world, n);
        // end::hierarchy[]

        // tag::bounds[]
        // World-space bounds of each unit cube: transform its local box by the world matrix.
        AABBSoA bounds = new AABBSoA(n);
        float[] wMin = new float[3 * n];
        float[] wMax = new float[3 * n];
        Matrix4x3f m = new Matrix4x3f();
        Vector3f min = new Vector3f();
        Vector3f max = new Vector3f();
        for (int i = 0; i < n; i++) {
            world.get(i, t).toAffineMat4Fast(m);
            m.transformAab(-0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, min, max);
            bounds.set(i, min.x, min.y, min.z, max.x, max.y, max.z);
            wMin[3 * i] = min.x;
            wMin[3 * i + 1] = min.y;
            wMin[3 * i + 2] = min.z;
            wMax[3 * i] = max.x;
            wMax[3 * i + 1] = max.y;
            wMax[3 * i + 2] = max.z;
        }
        // end::bounds[]

        // tag::cull[]
        // A reverse-Z camera with an infinite far plane: depth 1 at the near plane, 0 at infinity.
        Matrix4f proj = Projectionsf.reverseZInfinite((float) Math.toRadians(60), 16f / 9f, 0.1f, new Matrix4f());
        Matrix4f view = new Matrix4f().lookAt(0, 2, 0, 0, 2, -1, 0, 1, 0);
        Matrix4f viewProj = proj.mul(view, new Matrix4f());

        // Extract the planes for the depth range the projection was built with.
        FrustumPlanes frustum = new FrustumPlanes().set(viewProj, false, DepthRange.REVERSED_ZERO_TO_ONE);
        int[] classification = new int[n];
        CullingKernels.frustumCullAabbBatch(frustum, bounds, classification, n);

        int visible = 0;
        for (int c : classification) {
            if (c != FrustumIntersection.OUTSIDE) {
                visible++;
            }
        }
        // end::cull[]
        return new Result(n, wMin, wMax, classification, visible);
    }

    public static void main(String[] args) {
        Result r = run();
        System.out.println(r.visible() + " of " + r.count() + " objects are in the view frustum");
    }
}
