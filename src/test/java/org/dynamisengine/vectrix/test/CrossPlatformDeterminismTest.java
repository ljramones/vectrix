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
package org.dynamisengine.vectrix.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.dynamisengine.vectrix.core.Matrix4f;
import org.junit.jupiter.api.Test;

/**
 * Cross-platform determinism (docs/determinism.md): under {@code -Dvectrix.strictMath=true}, every public method
 * and the batch kernels must produce bit-identical results on every CPU and JVM. {@link DeterminismDigest} hashes
 * them in a child JVM, and the hashes are compared with {@code determinism/golden.txt}, which was recorded on one
 * platform; CI runs this test on x86_64 and arm64.
 * <p>
 * Methods missing from the golden file (newly added) are reported but do not fail. To re-record the file after an
 * intended behaviour change:
 * {@code mvn test -Dtest=CrossPlatformDeterminismTest -Dvectrix.determinism.write=src/test/resources/determinism/golden.txt}
 */
class CrossPlatformDeterminismTest {

    private static final String GOLDEN = "/determinism/golden.txt";

    private static Map<String, String> parse(List<String> lines) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String l : lines) {
            int sp = l.lastIndexOf(' ');
            if (sp > 0 && !l.startsWith("#") && !l.startsWith("WARNING")) {
                m.put(l.substring(0, sp), l.substring(sp + 1));
            }
        }
        return m;
    }

    private static List<String> runDigest() throws Exception {
        String javaExe = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String library = new File(Matrix4f.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
        String tests = new File(DeterminismDigest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                .getPath();
        Process p = new ProcessBuilder(javaExe, "-Dvectrix.strictMath=true", "--add-modules", "jdk.incubator.vector",
                "--enable-native-access=ALL-UNNAMED", "-cp", library + File.pathSeparator + tests,
                DeterminismDigest.class.getName())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(300, TimeUnit.SECONDS), "digest JVM timed out");
        assertEquals(0, p.exitValue(), "digest JVM failed");
        return out.lines().toList();
    }

    @Test
    void resultsMatchTheRecordedGolden() throws Exception {
        List<String> lines = runDigest();
        String write = System.getProperty("vectrix.determinism.write");
        if (write != null && !write.isEmpty()) {
            List<String> file = new ArrayList<>();
            file.add("# Recorded by CrossPlatformDeterminismTest on " + System.getProperty("os.arch") + ", "
                    + System.getProperty("java.vm.version") + ". One line per method: <method> <hash>.");
            file.addAll(lines);
            Files.write(Path.of(write), file, StandardCharsets.UTF_8);
            System.out.println("CrossPlatformDeterminismTest: wrote " + lines.size() + " entries to " + write);
            return;
        }
        Map<String, String> golden;
        try (InputStream in = CrossPlatformDeterminismTest.class.getResourceAsStream(GOLDEN)) {
            assertTrue(in != null, "missing " + GOLDEN);
            golden = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList());
        }
        Map<String, String> actual = parse(lines);
        List<String> changed = new ArrayList<>();
        int added = 0;
        for (Map.Entry<String, String> e : actual.entrySet()) {
            String expected = golden.get(e.getKey());
            if (expected == null) {
                added++;
            } else if (!expected.equals(e.getValue())) {
                changed.add(e.getKey());
            }
        }
        System.out.println("CrossPlatformDeterminismTest: " + actual.size() + " entries on "
                + System.getProperty("os.arch") + ", " + changed.size() + " differ from the golden, " + added
                + " not in it yet");
        assertTrue(actual.size() > 5000, "digest covered too few methods: " + actual.size());
        if (!changed.isEmpty()) {
            fail(changed.size() + " results differ from the golden recorded on another platform or version: "
                    + String.join(", ", changed.subList(0, Math.min(25, changed.size()))));
        }
    }
}
