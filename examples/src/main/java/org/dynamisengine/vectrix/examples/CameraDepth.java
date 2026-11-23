// SPDX-License-Identifier: MIT
package org.dynamisengine.vectrix.examples;

import org.dynamisengine.vectrix.camera.Projectionsf;
import org.dynamisengine.vectrix.camera.ShadowCascadesf;
import org.dynamisengine.vectrix.core.DepthRange;
import org.dynamisengine.vectrix.core.Matrix4f;

/**
 * Projection matrices for the three depth conventions, the depth precision each one gives, and shadow-cascade
 * splits.
 * <p>
 * {@link DepthRange} names the clip-space depth range a projection maps the near and far planes to: OpenGL's
 * [-1, 1], Vulkan's and Direct3D's [0, 1], and reverse-Z (near at 1, far at 0). Every API that builds a projection,
 * reads its planes or extracts a frustum takes the same {@code DepthRange}, so they cannot disagree.
 */
public final class CameraDepth {

    /** Depth at {@code distance} metres for each convention, and whether a 1 cm step changes the float depth. */
    public record Precision(DepthRange range, float depth, float roundTripDistance, boolean resolvesOneCentimetre) {
    }

    /** What {@link #run()} computed. */
    public record Result(Precision[] precision, float[] cascadeSplits) {
    }

    static final float NEAR = 0.1f;
    static final float FAR = 10_000f;
    static final float DISTANCE = 5_000f;

    private CameraDepth() {
    }

    public static Result run() {
        DepthRange[] ranges = DepthRange.values();
        Precision[] precision = new Precision[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            DepthRange range = ranges[i];
            // tag::projection[]
            // The same camera in each convention. Reverse-Z also allows an infinite far plane
            // (Projectionsf.reverseZInfinite); a finite one keeps the comparison fair here.
            Matrix4f proj = Projectionsf.perspective((float) Math.toRadians(60), 16f / 9f, NEAR, FAR, range,
                new Matrix4f());

            // Depth written to the depth buffer for a point DISTANCE metres in front of the camera, and back.
            float depth = Projectionsf.ndcDepth(proj, DISTANCE);
            float back = Projectionsf.linearDepth(depth, NEAR, FAR, range);

            // Floats are dense near 0 and sparse near 1. Reverse-Z puts distant depths near 0, where a float can
            // still tell 5000.00 m from 5000.01 m; the other conventions put them near 1, where it cannot.
            boolean resolves = Projectionsf.ndcDepth(proj, DISTANCE + 0.01f) != depth;
            // end::projection[]
            precision[i] = new Precision(range, depth, back, resolves);
        }

        // tag::cascades[]
        // Four shadow cascades over the first 200 m, half logarithmic and half uniform (lambda = 0.5).
        float[] splits = ShadowCascadesf.practicalSplits(NEAR, 200f, 4, 0.5f, new float[5]);
        // end::cascades[]
        return new Result(precision, splits);
    }

    public static void main(String[] args) {
        Result r = run();
        for (Precision p : r.precision()) {
            System.out.printf(java.util.Locale.ROOT, "%-22s depth(%.0f m) = %.9g, back to %.3f m, resolves 1 cm: %b%n", p.range(), DISTANCE,
                p.depth(), p.roundTripDistance(), p.resolvesOneCentimetre());
        }
        System.out.println("cascade splits: " + java.util.Arrays.toString(r.cascadeSplits()));
    }
}
