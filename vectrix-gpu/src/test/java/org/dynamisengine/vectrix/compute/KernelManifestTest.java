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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import org.dynamisengine.gpu.api.compute.Backend;
import org.junit.jupiter.api.Test;

/**
 * The compiled kernels match their sources and the Java wrappers: the parameter blocks the wrappers write have the
 * fields, order and offsets Slang compiled, and no .slang file changed without rerunning scripts/build-kernels.py.
 */
class KernelManifestTest {

    private static final Path SLANG = Path.of("src/main/slang");

    @Test
    void parameterBlocksMatchTheWrappers() {
        assertParams("gemm", GpuGemmf.PARAMS);
        assertParams("philox", GpuPhilox.PARAMS);
        assertParams("softmax", GpuSoftmax.PARAMS);
        assertParams("softmax_inplace", GpuSoftmax.PARAMS_IN_PLACE);
        assertEquals("float32", Manifest.paramTypes("gemm").get("alpha"));
        assertEquals("float32", Manifest.paramTypes("gemm").get("beta"));
        assertRowKernelParams();
    }

    /** The row kernels of GpuSoftmax (beyond softmax), GpuLoss and GpuNorms. */
    private static void assertRowKernelParams() {
        assertParams("log_softmax", GpuSoftmax.PARAMS_LOG);
        assertParams("log_softmax_inplace", GpuSoftmax.PARAMS_LOG_IN_PLACE);
        assertParams("log_sum_exp", GpuSoftmax.PARAMS_LSE);
        assertParams("softmax_backward", GpuSoftmax.PARAMS_BACKWARD);
        assertParams("softmax_backward_inplace", GpuSoftmax.PARAMS_BACKWARD_IN_PLACE);
        assertParams("log_softmax_backward", GpuSoftmax.PARAMS_BACKWARD);
        assertParams("log_softmax_backward_inplace", GpuSoftmax.PARAMS_BACKWARD_IN_PLACE);
        assertParams("cross_entropy", GpuLoss.PARAMS);
        assertParams("cross_entropy_backward", GpuLoss.PARAMS_BACKWARD);
        assertParams("cross_entropy_backward_inplace", GpuLoss.PARAMS_BACKWARD_IN_PLACE);
        assertEquals("float32", Manifest.paramTypes("cross_entropy_backward").get("dLoss"));
        assertEquals("float32", Manifest.paramTypes("cross_entropy_backward_inplace").get("dLoss"));
        assertParams("layer_norm", GpuNorms.PARAMS_LAYER);
        assertParams("layer_norm_inplace", GpuNorms.PARAMS_LAYER_IN_PLACE);
        assertParams("rms_norm", GpuNorms.PARAMS_RMS);
        assertParams("rms_norm_inplace", GpuNorms.PARAMS_RMS_IN_PLACE);
        for (String k : List.of("layer_norm", "layer_norm_inplace", "rms_norm", "rms_norm_inplace")) {
            assertEquals("float32", Manifest.paramTypes(k).get("eps"), k + ".eps");
        }
        assertParams("norm_backward", GpuNorms.PARAMS_BACKWARD);
        assertParams("norm_backward_inplace", GpuNorms.PARAMS_BACKWARD_IN_PLACE);
        assertParams("norm_param_grads", GpuNorms.PARAMS_PARAM_GRADS);
    }

    private static void assertParams(String kernel, String[] fields) {
        assertArrayEquals(fields, Manifest.params(kernel).keySet().toArray(new String[0]), kernel + " fields");
        for (int i = 0; i < fields.length; i++) {
            assertEquals(4 * i, Manifest.params(kernel).get(fields[i]), kernel + "." + fields[i] + " offset");
        }
        Manifest.paramTypes(kernel).forEach((f, t) -> assertTrue(t.equals("uint32") || t.equals("float32"),
                kernel + "." + f + " is " + t + "; the wrappers write only 32-bit fields"));
    }

    @Test
    void bindingsAndGroupSizesMatchTheWrappers() {
        assertEquals("a:0,b:1,c:2", Manifest.get("gemm.buffers"));
        assertEquals("16,16,1", Manifest.get("gemm.local"));
        assertEquals("dest:0", Manifest.get("philox.buffers"));
        assertEquals("64,1,1", Manifest.get("philox.local"));
        assertEquals("x:0,y:1", Manifest.get("softmax.buffers"));
        assertEquals("256,1,1", Manifest.get("softmax.local"));
        assertEquals("x:0", Manifest.get("softmax_inplace.buffers"));
        assertEquals("256,1,1", Manifest.get("softmax_inplace.local"));
        assertEquals("x:0,out:1", Manifest.get("log_sum_exp.buffers"));
        assertEquals("y:0,dy:1,dx:2", Manifest.get("softmax_backward.buffers"));
        assertEquals("a:0,b:1", Manifest.get("log_softmax_backward_inplace.buffers"));
        assertEquals("logits:0,targets:1,loss:2", Manifest.get("cross_entropy.buffers"));
        assertEquals("logits:0,targets:1", Manifest.get("cross_entropy_backward_inplace.buffers"));
        assertEquals("x:0,gamma:1,beta:2,y:3,stats:4", Manifest.get("layer_norm.buffers"));
        assertEquals("x:0,gamma:1,y:2,stats:3", Manifest.get("rms_norm.buffers"));
        assertEquals("x:0,gamma:1,stats:2,dy:3,dx:4", Manifest.get("norm_backward.buffers"));
        assertEquals("a:0,gamma:1,stats:2,b:3", Manifest.get("norm_backward_inplace.buffers"));
        assertEquals("x:0,stats:1,dy:2,dGamma:3,dBeta:4", Manifest.get("norm_param_grads.buffers"));
        assertEquals("64,1,1", Manifest.get("norm_param_grads.local"));
        for (String k : ROW_KERNELS) {
            if (!k.equals("norm_param_grads")) {
                assertEquals("256,1,1", Manifest.get(k + ".local"), k + ".local");
            }
        }
    }

    /** The kernels added for the row reductions (GpuSoftmax beyond softmax, GpuLoss, GpuNorms). */
    private static final List<String> ROW_KERNELS = List.of("log_softmax", "log_softmax_inplace", "log_sum_exp",
            "softmax_backward", "softmax_backward_inplace", "log_softmax_backward", "log_softmax_backward_inplace",
            "cross_entropy", "cross_entropy_backward", "cross_entropy_backward_inplace", "layer_norm",
            "layer_norm_inplace", "rms_norm", "rms_norm_inplace", "norm_backward", "norm_backward_inplace",
            "norm_param_grads");

    @Test
    void compiledKernelsAreUpToDate() throws IOException {
        assertEquals(Manifest.get("source.sha256"), sourceHash(SLANG),
                "src/main/slang changed since the kernels were compiled: run python3 scripts/build-kernels.py");
        assertEquals(Manifest.TEST.getProperty("source.sha256"), sourceHash(SLANG, Path.of("src/test/slang")),
                "src/main/slang or src/test/slang changed since the test kernels were compiled: run python3 "
                        + "scripts/build-kernels.py");
    }

    private static String sourceHash(Path... dirs) throws IOException {
        MessageDigest h = sha256();
        List<Path> files = new ArrayList<>();
        for (Path dir : dirs) {
            try (Stream<Path> s = Files.list(dir)) {
                files.addAll(s.filter(p -> p.toString().endsWith(".slang")).toList());
            }
        }
        files.sort(null);
        for (Path f : files) {
            h.update(f.getFileName().toString().getBytes(StandardCharsets.UTF_8));
            h.update((byte) 0);
            h.update(Files.readAllBytes(f));
            h.update((byte) 0);
        }
        return HexFormat.of().formatHex(h.digest());
    }

    @Test
    void resourcesAreTheCompiledOutputs() {
        List<String> kernels = new ArrayList<>(List.of("gemm", "philox", "softmax", "softmax_inplace"));
        kernels.addAll(ROW_KERNELS);
        for (String kernel : kernels) {
            var bundle = Kernels.bundle(kernel);
            byte[] spirv = bundle.executable(Backend.VULKAN).bytes();
            byte[] metal = bundle.executable(Backend.METAL).bytes();
            assertEquals(Manifest.get(kernel + ".spirv.sha256"), hex(spirv), kernel + ".spv");
            assertEquals(Manifest.get(kernel + ".metal.sha256"), hex(metal), kernel + ".metal");
            String source = new String(metal, StandardCharsets.UTF_8);
            assertTrue(source.startsWith("#pragma METAL fp math_mode(safe)\n#pragma METAL fp contract(off)\n"),
                    kernel + ".metal must disable fused multiply-adds");
        }
    }

    private static String hex(byte[] b) {
        return HexFormat.of().formatHex(sha256().digest(b));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
}
