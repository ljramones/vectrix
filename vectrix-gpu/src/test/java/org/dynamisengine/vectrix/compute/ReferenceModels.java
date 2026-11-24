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
package org.dynamisengine.vectrix.compute;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.dynamisengine.gpu.api.compute.ComputeException;
import org.dynamisengine.gpu.api.compute.Dimensions;

/**
 * Models of kernels for the {@link ReferenceProvider}: each one runs the Vectrix CPU method a kernel ports, on the
 * dispatch's buffers, with the parameters decoded at the offsets the compiled kernel reads.
 * <p>
 * To add a kernel, write its models in a class of its own (say {@code NormsModels}) with a
 * {@code static void register(Map<String, ReferenceModel> m)}, and call it from the static block below: one line.
 */
final class ReferenceModels {

    /** One kernel's CPU model. */
    interface ReferenceModel {
        void run(Call call);
    }

    /** A decoded dispatch: parameters by field name, buffers by slot, and the group counts. */
    record Call(Map<String, Long> u32, Map<String, Float> f32, Map<Integer, ByteBuffer> slots, Dimensions groups) {

        /** A 32-bit unsigned field as an int (throws if it does not fit). */
        int i(String field) {
            Long v = u32.get(field);
            if (v == null) {
                throw new IllegalStateException("no uint32 parameter " + field);
            }
            return Math.toIntExact(v);
        }

        /** A 32-bit unsigned field. */
        long u(String field) {
            Long v = u32.get(field);
            if (v == null) {
                throw new IllegalStateException("no uint32 parameter " + field);
            }
            return v;
        }

        /** A float field. */
        float f(String field) {
            Float v = f32.get(field);
            if (v == null) {
                throw new IllegalStateException("no float32 parameter " + field);
            }
            return v;
        }

        /** The whole buffer at {@code slot} as floats (a copy). */
        float[] floats(int slot) {
            ByteBuffer b = slots.get(slot);
            float[] a = new float[b.capacity() / 4];
            b.asFloatBuffer().get(a);
            return a;
        }

        /** The whole buffer at {@code slot} as ints (a copy). */
        int[] ints(int slot) {
            ByteBuffer b = slots.get(slot);
            int[] a = new int[b.capacity() / 4];
            b.asIntBuffer().get(a);
            return a;
        }

        /** Writes {@code a} over the buffer at {@code slot}. */
        void put(int slot, float[] a) {
            slots.get(slot).asFloatBuffer().put(a);
        }

        /** Writes {@code a} over the buffer at {@code slot}. */
        void put(int slot, int[] a) {
            slots.get(slot).asIntBuffer().put(a);
        }

        /** Fails the dispatch unless {@code ok}: for checks that the groups cover the work. */
        void require(boolean ok, String what) {
            if (!ok) {
                throw new ComputeException(ComputeException.Code.INVALID_ARGUMENT, "reference: " + what);
            }
        }

        /** Requires a linear grid of {@code groups} groups whose {@code gridX} parameter matches its width. */
        void requireLinearGrid(long groups) {
            require(u("gridX") == this.groups.x() && (long) this.groups.x() * this.groups.y() >= groups,
                    "groups " + this.groups + " (gridX " + u("gridX") + ") do not cover " + groups);
        }
    }

    private static final Map<String, ReferenceModel> MODELS = new HashMap<>();

    static {
        // one line per kernel group, e.g. NormsModels.register(MODELS);
    }

    private ReferenceModels() {
    }

    static ReferenceModel get(String kernel) {
        return MODELS.get(kernel);
    }
}
