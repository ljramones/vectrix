// SPDX-License-Identifier: MIT
package org.dynamisengine.vectrix.examples;

import org.dynamisengine.vectrix.blas.Gemmf;
import org.dynamisengine.vectrix.hash.Philox;
import org.dynamisengine.vectrix.nn.Activations;
import org.dynamisengine.vectrix.nn.Loss;
import org.dynamisengine.vectrix.nn.Optim;

/**
 * Train a two-layer network to tell whether a point lies inside the unit circle, from matrix products,
 * activations, a loss and an optimizer, with the backward pass written out.
 * <p>
 * Matrices are row-major {@code float[]} with explicit row strides. The {@code blas} and {@code nn} kernels are
 * reproducible: the same seed gives the same weights, bit for bit, on every machine and with either jar, whatever
 * the batch size.
 */
public final class TinyNetwork {

    /** Loss at the start and end of training, and accuracy on points the network never saw. */
    public record Result(float firstLoss, float lastLoss, float testAccuracy) {
    }

    static final int HIDDEN = 16;
    static final int BATCH = 256;
    static final int STEPS = 600;

    // Parameters live in one array, gradients in another with the same layout, so one optimizer call updates all.
    static final int W1 = 0;                     // 2 × HIDDEN
    static final int B1 = W1 + 2 * HIDDEN;       // HIDDEN
    static final int W2 = B1 + HIDDEN;           // HIDDEN × 2
    static final int B2 = W2 + 2 * HIDDEN;       // 2
    static final int PARAMS = B2 + 2;

    private TinyNetwork() {
    }

    public static Result run() {
        float[] params = new float[PARAMS];
        Philox.fillNormal(1, 0, 0f, (float) Math.sqrt(1.0 / 2), params, W1, 2 * HIDDEN);
        Philox.fillNormal(1, 1000, 0f, (float) Math.sqrt(1.0 / HIDDEN), params, W2, 2 * HIDDEN);
        float[] grads = new float[PARAMS];
        float[] adamM = new float[PARAMS];
        float[] adamV = new float[PARAMS];

        float[] x = new float[BATCH * 2];
        int[] label = new int[BATCH];
        float[] hidden = new float[BATCH * HIDDEN];
        float[] logits = new float[BATCH * 2];
        float[] dLogits = new float[BATCH * 2];
        float[] dHidden = new float[BATCH * HIDDEN];
        int scratchSize = Math.max(Math.max(Gemmf.scratchSize(false, false, BATCH, HIDDEN, 2),
            Gemmf.scratchSize(true, false, HIDDEN, 2, BATCH)), Math.max(Gemmf.scratchSize(false, true, BATCH,
            HIDDEN, 2), Gemmf.scratchSize(true, false, 2, HIDDEN, BATCH)));
        float[] scratch = new float[Math.max(scratchSize, 2)];

        float first = 0;
        float last = 0;
        for (int step = 1; step <= STEPS; step++) {
            sample(step, x, label);
            float loss = forward(params, x, hidden, logits, label, scratch);
            first = step == 1 ? loss : first;
            last = loss;

            // tag::backward[]
            // dLoss/dlogits for the mean loss over the batch.
            for (int i = 0; i < BATCH; i++) {
                Loss.crossEntropyBackward(logits, 2 * i, 2, label[i], 1f / BATCH, dLogits, 2 * i);
            }
            // dW2 = hiddenᵀ · dLogits, db2 = column sums of dLogits
            Gemmf.gemm(true, false, HIDDEN, 2, BATCH, 1f, hidden, 0, HIDDEN, dLogits, 0, 2, 0f, grads, W2, 2,
                scratch, 0);
            columnSums(dLogits, 2, grads, B2);
            // dHidden = dLogits · W2ᵀ, then back through tanh (its derivative from its output: 1 - y²)
            Gemmf.gemm(false, true, BATCH, HIDDEN, 2, 1f, dLogits, 0, 2, params, W2, 2, 0f, dHidden, 0, HIDDEN,
                scratch, 0);
            Activations.tanhBackward(hidden, 0, dHidden, 0, dHidden, 0, BATCH * HIDDEN);
            // dW1 = xᵀ · dHidden, db1 = column sums of dHidden
            Gemmf.gemm(true, false, 2, HIDDEN, BATCH, 1f, x, 0, 2, dHidden, 0, HIDDEN, 0f, grads, W1, HIDDEN,
                scratch, 0);
            columnSums(dHidden, HIDDEN, grads, B1);

            Optim.adamW(params, 0, grads, 0, adamM, 0, adamV, 0, PARAMS, 0.01f, 0.9f, 0.999f, 1e-8f, 0f, step);
            // end::backward[]
        }

        // Accuracy on a fresh batch the network was never trained on.
        sample(STEPS + 1, x, label);
        forward(params, x, hidden, logits, label, scratch);
        int correct = 0;
        for (int i = 0; i < BATCH; i++) {
            int predicted = logits[2 * i + 1] > logits[2 * i] ? 1 : 0;
            correct += predicted == label[i] ? 1 : 0;
        }
        return new Result(first, last, correct / (float) BATCH);
    }

    /** Batch {@code index}: points uniform in [-1.5, 1.5]², labelled 1 inside the unit circle. */
    static void sample(int index, float[] x, int[] label) {
        long base = (long) index * BATCH * 2;
        for (int i = 0; i < BATCH; i++) {
            float px = 3 * Philox.uniform(99, base + 2 * i) - 1.5f;
            float py = 3 * Philox.uniform(99, base + 2 * i + 1) - 1.5f;
            x[2 * i] = px;
            x[2 * i + 1] = py;
            label[i] = px * px + py * py < 1 ? 1 : 0;
        }
    }

    // tag::forward[]
    /** {@code hidden = tanh(x·W1 + b1)}, {@code logits = hidden·W2 + b2}; returns the mean cross-entropy. */
    static float forward(float[] params, float[] x, float[] hidden, float[] logits, int[] label, float[] scratch) {
        Gemmf.gemm(false, false, BATCH, HIDDEN, 2, 1f, x, 0, 2, params, W1, HIDDEN, 0f, hidden, 0, HIDDEN,
            scratch, 0);
        addBias(hidden, HIDDEN, params, B1);
        Activations.tanh(hidden, 0, hidden, 0, BATCH * HIDDEN);
        Gemmf.gemm(false, false, BATCH, 2, HIDDEN, 1f, hidden, 0, HIDDEN, params, W2, 2, 0f, logits, 0, 2,
            scratch, 0);
        addBias(logits, 2, params, B2);
        float sum = 0;
        for (int i = 0; i < BATCH; i++) {
            sum += Loss.crossEntropy(logits, 2 * i, 2, label[i], scratch, 0);
        }
        return sum / BATCH;
    }
    // end::forward[]

    static void addBias(float[] rows, int width, float[] params, int biasOff) {
        for (int i = 0; i < BATCH; i++) {
            for (int j = 0; j < width; j++) {
                rows[i * width + j] += params[biasOff + j];
            }
        }
    }

    static void columnSums(float[] rows, int width, float[] dest, int destOff) {
        for (int j = 0; j < width; j++) {
            float s = 0;
            for (int i = 0; i < BATCH; i++) {
                s += rows[i * width + j];
            }
            dest[destOff + j] = s;
        }
    }

    public static void main(String[] args) {
        Result r = run();
        System.out.printf(java.util.Locale.ROOT, "loss: %.4f at the start, %.4f after %d steps%n", r.firstLoss(),
            r.lastLoss(), STEPS);
        System.out.printf(java.util.Locale.ROOT, "accuracy on unseen points: %.1f %%%n", 100 * r.testAccuracy());
    }
}
