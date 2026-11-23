// SPDX-License-Identifier: MIT
package org.dynamisengine.vectrix.examples;

import org.dynamisengine.vectrix.core.Matrix4x3f;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3d;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.world.CameraRelative;
import org.dynamisengine.vectrix.world.WorldPos;

/**
 * Rendering far from the origin. A float has 64 m of resolution at 10⁹ m and a double 0.1 µm; {@link WorldPos}
 * keeps an integer cell plus a small double offset, and its differences are exact to within one ulp of the result
 * at any distance. Rendering then uses camera-relative floats, which are small and precise.
 */
public final class LargeWorld {

    /** The camera-relative position of the object computed three ways. */
    public record Result(Vector3f exact, Vector3f viaDouble, Vector3f viaFloat, Matrix4x3f model) {
    }

    static final double FAR_AWAY = 1e9; // metres: about three times the distance to the Moon

    private LargeWorld() {
    }

    public static Result run() {
        // tag::worldpos[]
        // 1024 m cells (2^10). The offset within a cell is a double, so resolution is about 1e-13 m everywhere.
        WorldPos camera = new WorldPos(10).set(FAR_AWAY, 2.0, FAR_AWAY);
        WorldPos object = new WorldPos(10).set(FAR_AWAY, 2.0, FAR_AWAY).add(0.123456789, 1.5, -3.25);

        // The camera-relative position: exact difference, rounded once to float for the GPU.
        Vector3f exact = object.sub(camera, new Vector3f());
        // end::worldpos[]

        // The same with plain doubles and plain floats, for comparison.
        Vector3d cd = new Vector3d(FAR_AWAY, 2.0, FAR_AWAY);
        Vector3d od = new Vector3d(FAR_AWAY + 0.123456789, 3.5, FAR_AWAY - 3.25);
        Vector3f viaDouble = new Vector3f(od.sub(cd, new Vector3d()));
        Vector3f cf = new Vector3f((float) FAR_AWAY, 2f, (float) FAR_AWAY);
        Vector3f of = new Vector3f((float) (FAR_AWAY + 0.123456789), 3.5f, (float) (FAR_AWAY - 3.25));
        Vector3f viaFloat = of.sub(cf, new Vector3f());

        // tag::model[]
        // The model matrix the renderer uploads: rotation and scale at the object's position, relative to the camera.
        Matrix4x3f model = CameraRelative.modelMatrix(object, new Quaternionf().rotateY(0.5f), new Vector3f(1, 1, 1),
            camera, new Matrix4x3f());
        // end::model[]
        return new Result(exact, viaDouble, viaFloat, model);
    }

    public static void main(String[] args) {
        Result r = run();
        System.out.println("object - camera, offset was (0.123456789, 1.5, -3.25):");
        System.out.println("  WorldPos: " + Format.vector(r.exact(), 9));
        System.out.println("  doubles:  " + Format.vector(r.viaDouble(), 9));
        System.out.println("  floats:   " + Format.vector(r.viaFloat(), 9));
    }
}
