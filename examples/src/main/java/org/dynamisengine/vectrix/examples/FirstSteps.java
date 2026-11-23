// SPDX-License-Identifier: MIT
package org.dynamisengine.vectrix.examples;

import org.dynamisengine.vectrix.core.CoreConfig;
import org.dynamisengine.vectrix.core.MathMode;
import org.dynamisengine.vectrix.core.Matrix4f;
import org.dynamisengine.vectrix.core.Matrix4fc;
import org.dynamisengine.vectrix.core.Quaternionf;
import org.dynamisengine.vectrix.core.Vector3f;
import org.dynamisengine.vectrix.core.Vector3fc;

/**
 * The conventions every Vectrix type follows: mutable classes with fluent methods, read-only {@code ...c} views,
 * results written into a destination you pass, and FAST/STRICT math modes.
 */
public final class FirstSteps {

    /** What {@link #run()} computed. */
    public record Result(Vector3f moved, Vector3f direction, float angleDegrees, boolean strictRejectedBadInput) {
    }

    private FirstSteps() {
    }

    public static Result run() {
        // tag::fluent[]
        // Mutable types have fluent methods that change `this` and return it.
        Matrix4f model = new Matrix4f()
            .translate(10, 0, 0)
            .rotateY((float) Math.toRadians(90))
            .scale(2);

        // Methods with a `dest` parameter write the result there and leave the inputs alone, so a frame loop can
        // reuse its objects instead of allocating.
        Vector3f moved = model.transformPosition(new Vector3f(1, 0, 0), new Vector3f());
        // end::fluent[]

        // tag::readonly[]
        // A read-only view (Matrix4fc, Vector3fc, ...) is the same object behind an interface without setters:
        // pass it where the callee must not change your data.
        Vector3f direction = forward(model, new Vector3f());
        // end::readonly[]

        // tag::quaternion[]
        Quaternionf a = new Quaternionf().rotateY((float) Math.toRadians(30));
        Quaternionf b = new Quaternionf().rotateY((float) Math.toRadians(90));
        float angle = (float) Math.toDegrees(a.difference(b, new Quaternionf()).angle());
        // end::quaternion[]

        // tag::strict[]
        // STRICT mode checks preconditions instead of trusting them; FAST (the default) skips the checks.
        // The mode applies to the current task only, so other threads keep theirs.
        boolean rejected;
        try {
            CoreConfig.runWithMathMode(MathMode.STRICT, () -> {
                Quaternionf notUnit = new Quaternionf(0, 0, 0, 2);
                notUnit.log(new Quaternionf()); // throws: the logarithm needs a unit quaternion
            });
            rejected = false;
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        // end::strict[]
        return new Result(moved, direction, angle, rejected);
    }

    /** The model's local -Z axis in world space. Takes a read-only view: it cannot change the caller's matrix. */
    static Vector3f forward(Matrix4fc model, Vector3f dest) {
        Vector3fc minusZ = new Vector3f(0, 0, -1);
        return model.transformDirection(minusZ, dest).normalize();
    }

    public static void main(String[] args) {
        Result r = run();
        System.out.println("model * (1, 0, 0)       = " + Format.vector(r.moved(), 3));
        System.out.println("forward direction       = " + Format.vector(r.direction(), 3));
        System.out.println("angle between rotations = " + r.angleDegrees() + " degrees");
        System.out.println("STRICT rejected a non-unit quaternion: " + r.strictRejectedBadInput());
    }
}
