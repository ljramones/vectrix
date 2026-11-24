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
        assertParams("to_bf16", GpuFormats.PARAMS_TO_BF16);
        assertParams("from_bf16", GpuFormats.PARAMS_FROM_BF16);
        assertParams("to_fp8", GpuFormats.PARAMS_TO_FP8);
        assertParams("from_fp8", GpuFormats.PARAMS_FROM_FP8);
        assertParams("quantize_int8", GpuFormats.PARAMS_QUANTIZE);
        assertParams("dequantize_int8", GpuFormats.PARAMS_DEQUANTIZE);
        assertParams("gemv_int8", GpuFormats.PARAMS_GEMV);
        assertParams("argmax", GpuSampler.PARAMS);
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
        assertParams("cull_aabb", GpuCullingKernels.PARAMS);
        assertParams("transform_compose", GpuTransformKernels.PARAMS);
        assertParams("skin_lbs", GpuSkinningKernels.PARAMS);
        assertParams("skin_dq", GpuSkinningKernels.PARAMS);
        assertParams("skin_palette", GpuSkinningKernels.PARAMS_PALETTE_SKIN);
        assertParams("palette_build", GpuSkinningKernels.PARAMS_PALETTE_BUILD);
        assertParams("affine_quat_translation", GpuPackedAffineKernels.PARAMS_QUAT_TRANSLATION);
        assertParams("affine_aabb", GpuPackedAffineKernels.PARAMS_AABB);
        assertParams("affine_upload", GpuPackedAffineKernels.PARAMS_UPLOAD);
        for (int i = 0; i < GpuCullingKernels.PLANE_FLOATS; i++) {
            assertEquals("float32", Manifest.paramTypes("cull_aabb").get(GpuCullingKernels.PARAMS[i]));
        }
        assertTrue(4 * GpuCullingKernels.PARAMS.length <= 128, "the culling parameters fit Vulkan's guaranteed 128"
                + " bytes of push constants");
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
        assertEquals("x:0,h:1", Manifest.get("to_bf16.buffers"));
        assertEquals("h:0,x:1", Manifest.get("from_bf16.buffers"));
        assertEquals("x:0,codes:1", Manifest.get("to_fp8.buffers"));
        assertEquals("codes:0,x:1", Manifest.get("from_fp8.buffers"));
        assertEquals("x:0,q:1,scales:2", Manifest.get("quantize_int8.buffers"));
        assertEquals("q:0,scales:1,x:2", Manifest.get("dequantize_int8.buffers"));
        assertEquals("q:0,scales:1,x:2,y:3", Manifest.get("gemv_int8.buffers"));
        assertEquals("logits:0,dest:1", Manifest.get("argmax.buffers"));
        for (String k : List.of("to_bf16", "from_bf16", "to_fp8", "from_fp8", "dequantize_int8", "gemv_int8")) {
            assertEquals("64,1,1", Manifest.get(k + ".local"), k);
        }
        assertEquals("256,1,1", Manifest.get("quantize_int8.local"));
        assertEquals("256,1,1", Manifest.get("argmax.local"));
        assertEquals("bounds:0,result:1", Manifest.get("cull_aabb.buffers"));
        assertEquals("parents:0,locals:1,outT:2", Manifest.get("transform_compose.buffers"));
        assertEquals("joints:0,indices:1,weights:2,inPos:3,outPos:4", Manifest.get("skin_lbs.buffers"));
        assertEquals("joints:0,indices:1,weights:2,inPos:3,outPos:4", Manifest.get("skin_dq.buffers"));
        assertEquals("palette:0,indices:1,weights:2,inPos:3,outPos:4", Manifest.get("skin_palette.buffers"));
        assertEquals("joints:0,palette:1", Manifest.get("palette_build.buffers"));
        assertEquals("rotation:0,translation:1,outAffine:2", Manifest.get("affine_quat_translation.buffers"));
        assertEquals("transforms:0,order:1,inBox:2,outBox:3", Manifest.get("affine_aabb.buffers"));
        assertEquals("transforms:0,order:1,outUpload:2", Manifest.get("affine_upload.buffers"));
        for (String k : ENGINE) {
            assertEquals(EngineBuffers.GROUP + ",1,1", Manifest.get(k + ".local"), k);
        }
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

    /** The engine kernels (culling, transforms, skinning, packed affine). */
    private static final List<String> ENGINE = List.of("cull_aabb", "transform_compose", "skin_lbs", "skin_dq",
            "skin_palette", "palette_build", "affine_quat_translation", "affine_aabb", "affine_upload");

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
