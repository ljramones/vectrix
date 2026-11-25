/*
 * The MIT License
 *
 * Copyright (c) 2017-2024 JOML
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The coverage gate over the whole library (4.0). Each jar's own tests cover only part of it: the cross-jar tests in
 * vectrix-tests (twin parity, aliasing, determinism, allocation) drive every jar. So vectrix-tests aggregates the
 * JaCoCo data of every module, and this checks the aggregate's totals.
 *
 * <p>Usage: {@code java CoverageGate.java <jacoco.xml> <line minimum> <branch minimum> [skipTests] [maven.test.skip] [jacoco.skip]}
 */
public final class CoverageGate {

    private CoverageGate() {
    }

    public static void main(String[] args) throws Exception {
        Path report = Path.of(args[0]);
        // the tests or JaCoCo skipped (-DskipTests, -Dmaven.test.skip, -Djacoco.skip): no data, nothing to check. Otherwise a missing report
        // is a build fault, not a pass.
        boolean testsSkipped = false;
        for (int i = 3; i < args.length; i++) {
            testsSkipped |= args[i].equals("true");
        }
        if (testsSkipped) {
            // JaCoCo may still write an empty report
            System.out.println("[CoverageGate] tests or JaCoCo skipped, no coverage to check");
            return;
        }
        if (!Files.exists(report)) {
            System.err.println("[CoverageGate] FAILED: no aggregate report at " + report);
            System.exit(1);
        }
        String xml = Files.readString(report);
        double lineMin = Double.parseDouble(args[1]);
        double branchMin = Double.parseDouble(args[2]);
        // the report's own counters follow its last group or package
        int at = Math.max(xml.lastIndexOf("</group>"), xml.lastIndexOf("</package>"));
        String totals = xml.substring(Math.max(at, 0));
        double line = ratio(totals, "LINE");
        double branch = ratio(totals, "BRANCH");
        String result = String.format("coverage of all jars: line %.4f (minimum %s), branch %.4f (minimum %s)",
            line, args[1], branch, args[2]);
        if (line < lineMin || branch < branchMin) {
            System.err.println("[CoverageGate] FAILED: " + result);
            System.exit(1);
        }
        System.out.println("[CoverageGate] " + result);
    }

    private static double ratio(String totals, String type) {
        Matcher m = Pattern.compile("<counter type=\"" + type + "\" missed=\"(\\d+)\" covered=\"(\\d+)\"/>")
            .matcher(totals);
        if (!m.find()) {
            throw new IllegalStateException("no " + type + " counter in the aggregate report");
        }
        double missed = Double.parseDouble(m.group(1));
        double covered = Double.parseDouble(m.group(2));
        return covered / (missed + covered);
    }
}
