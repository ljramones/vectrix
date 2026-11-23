/*
 * The MIT License
 *
 * Copyright (c) 2026 Vectrix
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.dynamisengine.vectrix.dsp;

/**
 * Smoothing filters: Savitzky–Golay coefficients, a median filter, and two trackers popular for noisy input
 * (positions, sensors, pointer and controller input): the alpha-beta filter and the One Euro filter.
 * <p>
 * Trackers keep their state in a small caller-held array, so one filter object is not needed per signal. Everything
 * is deterministic. Only {@link #savitzkyGolay}, a setup method, allocates.
 *
 * @since 2.5.0
 */
public final class Smoothers {

    private Smoothers() {
    }

    /**
     * Savitzky–Golay convolution coefficients: the least-squares polynomial of degree {@code order} through the
     * {@code 2·half + 1} samples around each point, evaluated (or differentiated {@code derivative} times) at the
     * centre. Convolve with the result (for example with {@link org.dynamisengine.vectrix.fft.BlockConvolverd}, the
     * taps reversed) to smooth or differentiate while keeping peak shapes. A derivative is per sample; divide by
     * {@code dt^derivative}. This is setup code: it allocates its small linear system.
     *
     * @param half the half-width of the window in samples, {@code >= 0}
     * @param order the polynomial degree, in {@code [0, 2·half]}
     * @param derivative the derivative order, in {@code [0, order]}; 0 smooths
     * @param coeffs receives {@code 2·half + 1} coefficients from index 0, for samples {@code -half..half}
     * @throws IllegalArgumentException if an order is out of range or {@code coeffs} is too short
     */
    public static void savitzkyGolay(int half, int order, int derivative, double[] coeffs) {
        int len = 2 * half + 1;
        if (half < 0 || order < 0 || order >= len || derivative < 0 || derivative > order || coeffs.length < len) {
            throw new IllegalArgumentException("need 0 <= derivative <= order < 2·half + 1");
        }
        int m = order + 1;
        // normal equations (AᵀA) c = Aᵀ e_t with A[i][j] = (i - half)^j; solve for the row that gives the derivative
        double[] ata = new double[m * m];
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                double s = 0;
                for (int t = -half; t <= half; t++) {
                    s += pow(t, i + j);
                }
                ata[i * m + j] = s;
            }
        }
        // rhs: the derivative row of the monomial basis at 0 picks coefficient 'derivative', times derivative!
        double[] e = new double[m];
        double fact = 1;
        for (int k = 2; k <= derivative; k++) {
            fact *= k;
        }
        e[derivative] = fact;
        double[] z = gaussSolve(ata, e, m); // z = (AᵀA)⁻¹·e_d·d!
        for (int t = -half; t <= half; t++) {
            double s = 0;
            for (int j = 0; j < m; j++) {
                s += z[j] * pow(t, j);
            }
            coeffs[t + half] = s;
        }
    }

    private static double pow(int t, int k) {
        double v = 1;
        for (int i = 0; i < k; i++) {
            v *= t;
        }
        return v;
    }

    /** Gaussian elimination with partial pivoting on a copy (setup code; small systems). */
    private static double[] gaussSolve(double[] a0, double[] b0, int m) {
        double[] a = a0.clone();
        double[] b = b0.clone();
        for (int c = 0; c < m; c++) {
            int piv = c;
            for (int r = c + 1; r < m; r++) {
                if (Math.abs(a[r * m + c]) > Math.abs(a[piv * m + c])) {
                    piv = r;
                }
            }
            for (int j = 0; j < m; j++) {
                double t = a[c * m + j];
                a[c * m + j] = a[piv * m + j];
                a[piv * m + j] = t;
            }
            double t = b[c];
            b[c] = b[piv];
            b[piv] = t;
            for (int r = c + 1; r < m; r++) {
                double f = a[r * m + c] / a[c * m + c];
                for (int j = c; j < m; j++) {
                    a[r * m + j] -= f * a[c * m + j];
                }
                b[r] -= f * b[c];
            }
        }
        double[] x = new double[m];
        for (int r = m - 1; r >= 0; r--) {
            double s = b[r];
            for (int j = r + 1; j < m; j++) {
                s -= a[r * m + j] * x[j];
            }
            x[r] = s / a[r * m + r];
        }
        return x;
    }

    /**
     * A running median of {@code 2·half + 1} samples, with the edges replicated. Removes impulsive noise (spikes)
     * that linear filters smear.
     *
     * @param x the samples
     * @param xOff the offset of the first sample
     * @param n the number of samples, {@code >= 0}
     * @param half the half-width of the window, {@code >= 0}
     * @param y receives {@code n} values; must not overlap {@code x}
     * @param yOff the offset of the first output
     * @param scratch {@code 2·half + 1} floats, used from index 0
     * @throws IllegalArgumentException if {@code n} or {@code half} is negative, an array is null, {@code scratch} is
     *         too short, or {@code x} or {@code y} does not hold {@code n} values from its offset (checked before
     *         anything is written)
     */
    public static void median(float[] x, int xOff, int n, int half, float[] y, int yOff, float[] scratch) {
        if (n < 0 || half < 0 || half > (Integer.MAX_VALUE - 1) / 2 || x == null || y == null || scratch == null
                || scratch.length < 2 * half + 1 || xOff < 0 || xOff > x.length - n || yOff < 0
                || yOff > y.length - n) {
            throw new IllegalArgumentException("need n >= 0 values from each offset and 2·half + 1 floats of scratch: n "
                    + n + ", half " + half);
        }
        int len = 2 * half + 1;
        for (int i = 0; i < n; i++) {
            // insertion sort of the window (small windows: O(w²) is fine and allocation-free)
            for (int k = 0; k < len; k++) {
                int idx = Math.min(n - 1, Math.max(0, i + k - half));
                float v = x[xOff + idx];
                int j = k - 1;
                while (j >= 0 && Float.compare(scratch[j], v) > 0) {
                    scratch[j + 1] = scratch[j];
                    j--;
                }
                scratch[j + 1] = v;
            }
            y[yOff + i] = scratch[half];
        }
    }

    /**
     * One alpha-beta filter step: predict {@code x += v·dt}, then correct with the residual {@code r = z - x}:
     * {@code x += α·r}, {@code v += β·r/dt}.
     *
     * @param state {@code [x, v]}, updated
     * @param z the measured position
     * @param dt the time step, {@code > 0}
     * @param alpha the position gain, in (0, 1]
     * @param beta the velocity gain, {@code > 0}
     * @return the filtered position
     */
    public static double alphaBeta(double[] state, double z, double dt, double alpha, double beta) {
        double x = Math.fma(state[1], dt, state[0]);
        double r = z - x;
        state[0] = Math.fma(alpha, r, x);
        state[1] = Math.fma(beta / dt, r, state[1]);
        return state[0];
    }

    /**
     * One step of the One Euro filter (Casiez, Roussel and Vogel, CHI 2012): a low-pass filter whose cutoff rises
     * with speed, {@code fc = minCutoff + beta·|dx̂|}, so it removes jitter at rest and lag in fast motion.
     *
     * @param state {@code [x̂, dx̂, initialized]}, initially all zero, updated
     * @param x the new sample
     * @param dt the time since the previous sample, in seconds, {@code > 0}
     * @param minCutoff the cutoff at rest, in hertz (for example 1)
     * @param beta the speed coefficient (for example 0.007)
     * @param dCutoff the cutoff for the derivative, in hertz (usually 1)
     * @return the filtered value
     */
    public static double oneEuro(double[] state, double x, double dt, double minCutoff, double beta,
            double dCutoff) {
        if (state[2] == 0) {
            state[0] = x;
            state[1] = 0;
            state[2] = 1;
            return x;
        }
        double dx = (x - state[0]) / dt;
        double dHat = lowpass(state[1], dx, smoothing(dt, dCutoff));
        double cutoff = minCutoff + beta * Math.abs(dHat);
        double xHat = lowpass(state[0], x, smoothing(dt, cutoff));
        state[0] = xHat;
        state[1] = dHat;
        return xHat;
    }

    /** {@code α = 1 / (1 + τ/dt)}, {@code τ = 1/(2π·fc)}. */
    private static double smoothing(double dt, double cutoff) {
        double tau = 1 / (2 * Math.PI * cutoff);
        return 1 / (1 + tau / dt);
    }

    private static double lowpass(double prev, double x, double a) {
        return Math.fma(a, x - prev, prev);
    }
}
