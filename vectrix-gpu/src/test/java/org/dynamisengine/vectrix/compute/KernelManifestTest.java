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
        assertParams("activation", GpuActivations.PARAMS);
        assertParams("activation_inplace", GpuActivations.PARAMS_IN_PLACE);
        assertParams("activation_self", GpuActivations.PARAMS_SELF);
        assertParams("dropout", GpuActivations.PARAMS_DROPOUT);
        assertParams("dropout_inplace", GpuActivations.PARAMS_DROPOUT_IN_PLACE);
        assertParams("adamw", GpuOptim.PARAMS_ADAMW);
        assertParams("sgd", GpuOptim.PARAMS_SGD);
        assertEquals("float32", Manifest.paramTypes("gemm").get("alpha"));
        assertEquals("float32", Manifest.paramTypes("gemm").get("beta"));
        for (String k : new String[] {"dropout", "dropout_inplace"}) {
            assertEquals("float32", Manifest.paramTypes(k).get("prob"), k + ".prob");
            assertEquals("float32", Manifest.paramTypes(k).get("scale"), k + ".scale");
        }
        for (String f : new String[] {"negLr", "beta1", "beta2", "eps", "bc1", "bc2", "a1", "a2", "decay"}) {
            assertEquals("float32", Manifest.paramTypes("adamw").get(f), "adamw." + f);
        }
        for (String f : new String[] {"negLr", "momentum", "weightDecay"}) {
            assertEquals("float32", Manifest.paramTypes("sgd").get(f), "sgd." + f);
        }
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
        assertEquals("a:0,b:1,dest:2", Manifest.get("activation.buffers"));
        assertEquals("dest:0,other:1", Manifest.get("activation_inplace.buffers"));
        assertEquals("x:0", Manifest.get("activation_self.buffers"));
        for (String k : new String[] {"activation", "activation_inplace", "activation_self", "adamw", "sgd"}) {
            assertEquals("256,1,1", Manifest.get(k + ".local"), k);
        }
        assertEquals("x:0,y:1", Manifest.get("dropout.buffers"));
        assertEquals("64,1,1", Manifest.get("dropout.local"));
        assertEquals("x:0", Manifest.get("dropout_inplace.buffers"));
        assertEquals("64,1,1", Manifest.get("dropout_inplace.local"));
        assertEquals("param:0,grad:1,m:2,v:3", Manifest.get("adamw.buffers"));
        assertEquals("param:0,grad:1,buf:2", Manifest.get("sgd.buffers"));
    }

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
        for (String kernel : List.of("gemm", "philox", "softmax", "softmax_inplace", "activation",
                "activation_inplace", "activation_self", "dropout", "dropout_inplace", "adamw", "sgd")) {
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
