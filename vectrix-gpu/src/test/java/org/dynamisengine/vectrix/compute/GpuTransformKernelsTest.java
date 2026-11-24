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

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.dynamisengine.vectrix.soa.TransformKernels;
import org.dynamisengine.vectrix.soa.TransformSoA;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** {@link GpuTransformKernels} against {@code TransformKernels.composeBatch}, bit for bit, on every backend. */
class GpuTransformKernelsTest {

    private static final float UNWRITTEN = -3.5f;

    static Stream<Arguments> cases() {
        List<Arguments> out = new ArrayList<>();
        for (String backend : Devices.names()) {
            for (int n : EngineData.COUNTS) {
                for (EngineData.Scale scale : EngineData.Scale.values()) {
                    out.add(Arguments.of(backend, n, scale));
                }
            }
        }
        return out.stream();
    }

    static Stream<String> backends() {
        return Devices.names().stream();
    }

    /** Composes on {@code backend} with non-dense offsets and strides and compares the whole output buffer. */
    static void check(String backend, TransformSoA parents, TransformSoA locals, int n, boolean sameBuffer) {
        TransformSoA expected = new TransformSoA(n);
        TransformKernels.composeBatch(parents, locals, expected, n);
        int pOff = 3;
        int pStride = n + 2;
        int lOff = sameBuffer ? pOff + 10 * pStride + 1 : 1;
        int lStride = sameBuffer ? pStride : n + 5;
        int oOff = 4;
        int oStride = n + 1;
        float[] pb = EngineData.pack(EngineModels.fields(parents), n, pOff, pStride, 2, Float.NaN);
        float[] lb = EngineData.pack(EngineModels.fields(locals), n, lOff, lStride, 2, Float.NaN);
        if (sameBuffer) {
            // parents and locals in one buffer, locals after parents
            float[] both = EngineData.filled(lOff + 9 * lStride + n + 2, Float.NaN);
            System.arraycopy(pb, 0, both, 0, pOff + 9 * pStride + n);
            EngineModels.pack(EngineModels.fields(locals), n, both, lOff, lStride);
            pb = both;
        }
        float[] out = EngineData.filled(oOff + 9 * oStride + n + 3, UNWRITTEN);
        float[] want = out.clone();
        EngineModels.pack(EngineModels.fields(expected), n, want, oOff, oStride);
        try (GpuDevice device = Devices.open(backend); GpuTransformKernels k = new GpuTransformKernels(device);
                var enc = device.encoder()) {
            var gp = Devices.upload(device, enc, pb);
            var gl = sameBuffer ? gp : Devices.upload(device, enc, lb);
            var go = Devices.upload(device, enc, out);
            k.composeBatch(enc, gp, pOff, pStride, gl, lOff, lStride, go, oOff, oStride, n);
            var read = enc.download(go);
            Devices.run(device, enc);
            Bits.assertSame(want, Devices.floats(read, out.length), Devices.hardware(backend),
                    backend + " compose n=" + n + (sameBuffer ? " (one input buffer)" : ""));
        }
    }

    @ParameterizedTest(name = "{0} n={1} {2}")
    @MethodSource("cases")
    void matchesComposeBatch(String backend, int n, EngineData.Scale scale) {
        SplittableRandom r = new SplittableRandom(n * 101L + scale.ordinal());
        TransformSoA parents = EngineData.transforms(r, n, scale);
        TransformSoA locals = EngineData.transforms(r, n, EngineData.Scale.values()[(n + scale.ordinal()) % 3]);
        check(backend, parents, locals, n, n % 2 == 1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void identitiesSignedZerosAndNonUnitRotations(String backend) {
        SplittableRandom r = new SplittableRandom(8);
        int n = 200;
        TransformSoA parents = EngineData.transforms(r, n, EngineData.Scale.NON_UNIFORM);
        TransformSoA locals = EngineData.transforms(r, n, EngineData.Scale.UNIFORM);
        for (int i = 0; i < n; i += 4) {
            // identity parent
            parents.tx[i] = 0f;
            parents.ty[i] = -0f;
            parents.tz[i] = 0f;
            parents.qx[i] = 0f;
            parents.qy[i] = 0f;
            parents.qz[i] = -0f;
            parents.qw[i] = 1f;
            parents.sx[i] = 1f;
            parents.sy[i] = 1f;
            parents.sz[i] = 1f;
        }
        for (int i = 1; i < n; i += 4) {
            // identity local with signed zeros
            locals.tx[i] = -0f;
            locals.ty[i] = 0f;
            locals.tz[i] = -0f;
            locals.qx[i] = -0f;
            locals.qy[i] = 0f;
            locals.qz[i] = 0f;
            locals.qw[i] = 1f;
            locals.sx[i] = 1f;
            locals.sy[i] = 1f;
            locals.sz[i] = 1f;
        }
        for (int i = 2; i < n; i += 4) {
            parents.qw[i] *= 3f; // not unit: used as given, as in FAST mode
            locals.sx[i] = 0f;
            locals.sy[i] = -2f;
        }
        check(backend, parents, locals, n, false);
    }
}
