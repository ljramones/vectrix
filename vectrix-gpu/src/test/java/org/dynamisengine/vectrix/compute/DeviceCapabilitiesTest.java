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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.dynamisengine.gpu.api.compute.GpuDevice;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The float kernel tests run only where fma is fused ({@link Devices#namesWithFusedFma}, from DynamisGPU's measured
 * {@code FUSED_FMA}). So that a regression cannot turn them into silent skips, Apple GPUs (native Metal, and Vulkan
 * through MoltenVK, vendor 0x106B) must report a fused fma, as they do; and on every device the capability must
 * agree with this project's own math_probe measurement.
 */
class DeviceCapabilitiesTest {

    static Stream<String> backends() {
        return Devices.names().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backends")
    void appleGpusFuseFma(String backend) {
        boolean apple;
        try (GpuDevice device = Devices.open(backend)) {
            apple = backend.equals("metal") || device.adapter().id().contains(":" + 0x106B + ":");
            System.out.println(backend + " (" + device.adapter().name() + "): fused fma = " + Devices.fusesFma(backend));
        }
        if (apple || backend.equals("reference")) {
            assertTrue(Devices.fusesFma(backend), backend + " must fuse fma");
        }
        org.junit.jupiter.api.Assertions.assertEquals(Devices.probeFusedFma(backend), Devices.fusesFma(backend),
                backend + ": DynamisGPU's FUSED_FMA must agree with the math_probe measurement");
    }
}
