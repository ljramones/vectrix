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
import java.util.Properties;
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
        assertParams("attention_scores", GpuAttention.PARAMS_SCORES);
        assertParams("attention_softmax", GpuAttention.PARAMS_SOFTMAX);
        assertParams("attention_softmax_backward", GpuAttention.PARAMS_SOFTMAX_BACKWARD);
        assertParams("attention_mix", GpuAttention.PARAMS_MIX);
        assertParams("attention_accumulate", GpuAttention.PARAMS_ACCUMULATE);
        assertParams("blas_partial", GpuBlasf.PARAMS_PARTIAL);
        assertParams("blas_combine", GpuBlasf.PARAMS_COMBINE);
        assertParams("blas_amax", GpuBlasf.PARAMS_AMAX);
        assertParams("blas_axpy", GpuBlasf.PARAMS_AXPY);
        assertParams("blas_axpy_inplace", GpuBlasf.PARAMS_AXPY_IN_PLACE);
        assertParams("blas_scal", GpuBlasf.PARAMS_SCAL);
        assertParams("gemv", GpuGemmf.PARAMS_GEMV);
        for (String k : new String[] {"blas_axpy", "blas_axpy_inplace", "blas_scal"}) {
            assertEquals("float32", Manifest.paramTypes(k).get("a"));
        }
        assertEquals("float32", Manifest.paramTypes("gemv").get("alpha"));
        assertEquals("float32", Manifest.paramTypes("gemv").get("beta"));
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
        assertEquals("x:0,m:1,mask:2,out:3", Manifest.get("attention_scores.buffers"));
        assertEquals("64,1,1", Manifest.get("attention_scores.local"));
        assertEquals("probs:0", Manifest.get("attention_softmax.buffers"));
        assertEquals("256,1,1", Manifest.get("attention_softmax.local"));
        assertEquals("probs:0,ds:1", Manifest.get("attention_softmax_backward.buffers"));
        assertEquals("256,1,1", Manifest.get("attention_softmax_backward.local"));
        assertEquals("m:0,w:1,out:2", Manifest.get("attention_mix.buffers"));
        assertEquals("64,1,1", Manifest.get("attention_mix.local"));
        assertEquals("w:0,x:1,dest:2", Manifest.get("attention_accumulate.buffers"));
        assertEquals("64,1,1", Manifest.get("attention_accumulate.local"));
        assertEquals("x:0,y:1,scratch:2", Manifest.get("blas_partial.buffers"));
        assertEquals("256,1,1", Manifest.get("blas_partial.local"));
        assertEquals("scratch:0,result:1", Manifest.get("blas_combine.buffers"));
        assertEquals("32,1,1", Manifest.get("blas_combine.local"));
        assertEquals("x:0,scratch:1,result:2", Manifest.get("blas_amax.buffers"));
        assertEquals("256,1,1", Manifest.get("blas_amax.local"));
        assertEquals("x:0,y:1", Manifest.get("blas_axpy.buffers"));
        assertEquals("256,1,1", Manifest.get("blas_axpy.local"));
        assertEquals("x:0", Manifest.get("blas_axpy_inplace.buffers"));
        assertEquals("256,1,1", Manifest.get("blas_axpy_inplace.local"));
        assertEquals("x:0", Manifest.get("blas_scal.buffers"));
        assertEquals("256,1,1", Manifest.get("blas_scal.local"));
        assertEquals("a:0,x:1,y:2", Manifest.get("gemv.buffers"));
        assertEquals("64,1,1", Manifest.get("gemv.local"));
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
        for (String kernel : kernelNames()) {
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

    /** Every kernel in kernels.properties (one {@code <name>.entry} key each). */
    private static List<String> kernelNames() {
        Properties main = new Properties();
        try (var in = Manifest.class.getResourceAsStream("kernels/kernels.properties")) {
            main.load(in);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return main.stringPropertyNames().stream().filter(k -> k.endsWith(".entry"))
                .map(k -> k.substring(0, k.length() - ".entry".length())).sorted().toList();
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
