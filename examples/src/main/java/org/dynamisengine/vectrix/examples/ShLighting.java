// SPDX-License-Identifier: MIT
package org.dynamisengine.vectrix.examples;

import org.dynamisengine.vectrix.sh.ShConvolution;
import org.dynamisengine.vectrix.sh.ShCoeffs9f;
import org.dynamisengine.vectrix.sh.ShProjection;

/**
 * Bake a sky into nine spherical-harmonic coefficients per colour channel, then light diffuse surfaces from them.
 * <p>
 * Order-2 SH (9 coefficients) capture the low-frequency part of an environment, which is all a diffuse
 * (Lambertian) surface responds to: projecting, convolving with the cosine lobe and evaluating along a normal
 * gives the irradiance within a few percent of the exact integral.
 */
public final class ShLighting {

    /** Irradiance (RGB) for the normals up, sideways and down. */
    public record Result(ShCoeffs9f irradianceSh, float[] up, float[] side, float[] down) {
    }

    static final int ROWS = 64;
    static final int COLS = 128;

    private ShLighting() {
    }

    /** The environment: a blue sky brightening toward the zenith, over dark grey ground. */
    static void sky(float x, float y, float z, float[] rgb) {
        if (y > 0) {
            rgb[0] = 0.3f + 0.4f * y;
            rgb[1] = 0.5f + 0.4f * y;
            rgb[2] = 1.0f + 0.5f * y;
        } else {
            rgb[0] = 0.10f;
            rgb[1] = 0.10f;
            rgb[2] = 0.08f;
        }
    }

    public static Result run() {
        // tag::project[]
        // Integrate the environment over a latitude-longitude grid; each cell's weight is its solid angle.
        ShCoeffs9f radiance = new ShCoeffs9f();
        float[] scratch = new float[9];
        float[] rgb = new float[3];
        for (int i = 0; i < ROWS; i++) {
            double theta0 = Math.PI * i / ROWS;
            double theta1 = Math.PI * (i + 1) / ROWS;
            double theta = 0.5 * (theta0 + theta1);
            float solidAngle = (float) ((Math.cos(theta0) - Math.cos(theta1)) * 2 * Math.PI / COLS);
            for (int j = 0; j < COLS; j++) {
                double phi = 2 * Math.PI * (j + 0.5) / COLS;
                float x = (float) (Math.sin(theta) * Math.cos(phi));
                float y = (float) Math.cos(theta); // y is up
                float z = (float) (Math.sin(theta) * Math.sin(phi));
                sky(x, y, z, rgb);
                ShProjection.projectSample(x, y, z, rgb[0], rgb[1], rgb[2], solidAngle, scratch, radiance);
            }
        }
        // end::project[]

        // tag::irradiance[]
        // Convolve with the clamped cosine: the coefficients now give irradiance E(n) for any normal n.
        // A Lambertian surface with albedo a reflects radiance a · E(n) / π.
        ShCoeffs9f irradiance = ShConvolution.convolveLambertL2(radiance, new ShCoeffs9f());
        float[] up = new float[3];
        float[] side = new float[3];
        float[] down = new float[3];
        ShConvolution.evaluateL2(irradiance, 0, 1, 0, scratch, up);
        ShConvolution.evaluateL2(irradiance, 1, 0, 0, scratch, side);
        ShConvolution.evaluateL2(irradiance, 0, -1, 0, scratch, down);
        // end::irradiance[]
        return new Result(irradiance, up, side, down);
    }

    public static void main(String[] args) {
        Result r = run();
        System.out.println("irradiance facing up:      " + java.util.Arrays.toString(r.up()));
        System.out.println("irradiance facing sideways: " + java.util.Arrays.toString(r.side()));
        System.out.println("irradiance facing down:    " + java.util.Arrays.toString(r.down()));
    }
}
