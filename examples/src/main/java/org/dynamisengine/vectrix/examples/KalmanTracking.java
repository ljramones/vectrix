// SPDX-License-Identifier: MIT
package org.dynamisengine.vectrix.examples;

import org.dynamisengine.vectrix.dsp.Kalman;
import org.dynamisengine.vectrix.hash.Philox;

/**
 * Track an object moving at constant velocity from noisy position measurements with a linear Kalman filter.
 * <p>
 * The state is position and velocity, {@code x = [p, v]}; each step predicts with the motion model and then
 * corrects with the measured position. Matrices are row-major {@code double[]}; the filter state, covariance and
 * scratch are the caller's, so the loop does not allocate and can run for many objects side by side.
 */
public final class KalmanTracking {

    /** RMS position error of the raw measurements and of the filter, and the final velocity estimate. */
    public record Result(double measurementRms, double filterRms, double velocity, int rejected) {
    }

    static final int STEPS = 400;
    static final double DT = 0.05;          // seconds per step
    static final double TRUE_VELOCITY = 2.0; // metres per second
    static final double NOISE = 0.5;         // measurement noise, metres (standard deviation)
    static final double GATE = 16.0;         // reject innovations beyond 4 sigma (squared distance, 1 measurement)

    private KalmanTracking() {
    }

    public static Result run() {
        // tag::model[]
        int n = 2;
        int m = 1;
        double[] x = {0, 0};                     // initial guess: at rest at the origin
        double[] p = {10, 0, 0, 10};             // ... with large uncertainty
        double[] f = {1, DT, 0, 1};              // p' = p + v·dt, v' = v
        double q0 = 0.01;                        // process noise: small random accelerations
        double[] q = {q0 * DT * DT * DT / 3, q0 * DT * DT / 2, q0 * DT * DT / 2, q0 * DT};
        double[] h = {1, 0};                     // we measure the position only
        double[] r = {NOISE * NOISE};
        double[] scratch = new double[Kalman.scratchSize(n, m)];
        // end::model[]

        float[] noise = new float[STEPS];
        Philox.fillNormal(7, 0, 0f, (float) NOISE, noise, 0, STEPS);
        double[] z = new double[1];
        double measuredSq = 0;
        double filteredSq = 0;
        int rejected = 0;
        for (int k = 0; k < STEPS; k++) {
            double truth = TRUE_VELOCITY * DT * (k + 1);
            z[0] = truth + noise[k];
            if (k == STEPS / 2) {
                z[0] += 25; // one wild measurement, to show gating
            }
            // tag::loop[]
            Kalman.predict(n, x, p, f, q, scratch);
            // Gate before updating: innovationDistance gives the squared Mahalanobis distance of the measurement
            // without changing the state, so an implausible measurement is simply skipped.
            if (Kalman.innovationDistance(n, m, x, p, h, r, z, scratch) <= GATE) {
                Kalman.update(n, m, x, p, h, r, z, scratch);
            } else {
                rejected++;
            }
            // end::loop[]
            if (k >= 50 && k != STEPS / 2) { // after the filter has converged; not the wild measurement
                measuredSq += (z[0] - truth) * (z[0] - truth);
                filteredSq += (x[0] - truth) * (x[0] - truth);
            }
        }
        int counted = STEPS - 51;
        return new Result(Math.sqrt(measuredSq / counted), Math.sqrt(filteredSq / counted), x[1], rejected);
    }

    public static void main(String[] args) {
        Result r = run();
        System.out.printf(java.util.Locale.ROOT, "position error, raw measurements: %.3f m RMS%n", r.measurementRms());
        System.out.printf(java.util.Locale.ROOT, "position error, Kalman filter:    %.3f m RMS%n", r.filterRms());
        System.out.printf(java.util.Locale.ROOT, "velocity estimate: %.3f m/s (true %.1f)%n", r.velocity(),
            TRUE_VELOCITY);
        System.out.println("measurements rejected by the gate: " + r.rejected());
    }
}
