// SPDX-License-Identifier: MIT
package org.dynamisengine.vectrix.examples;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Java code in the documentation is quoted from these tested examples, never written by hand. Each block is
 * preceded by {@code <!-- snippet: Example#region -->} and must equal the example's {@code // tag::region[]} ...
 * {@code // end::region[]} lines, de-indented. A Java block without a marker fails, so every piece of Java in the
 * docs compiles and runs. Run with {@code -Dsnippets.write=true} to rewrite the blocks from the sources.
 */
class DocSnippetsTest {

    private static final Path REPO = Path.of("..");
    private static final Path SOURCES = Path.of("src/main/java/org/dynamisengine/vectrix/examples");
    private static final Pattern MARKER = Pattern.compile("<!-- snippet: (\\w+)#([\\w-]+) -->");

    static String region(String example, String region) throws IOException {
        Path file = SOURCES.resolve(example + ".java");
        if (!Files.exists(file)) {
            return null;
        }
        List<String> lines = Files.readAllLines(file);
        int start = -1;
        int end = -1;
        for (int i = 0; i < lines.size(); i++) {
            String t = lines.get(i).trim();
            if (t.equals("// tag::" + region + "[]")) {
                start = i + 1;
            } else if (t.equals("// end::" + region + "[]") && start >= 0) {
                end = i;
                break;
            }
        }
        if (start < 0 || end < 0) {
            return null;
        }
        List<String> body = lines.subList(start, end);
        int indent = Integer.MAX_VALUE;
        for (String l : body) {
            if (!l.isBlank()) {
                indent = Math.min(indent, l.length() - l.stripLeading().length());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String l : body) {
            sb.append(l.isBlank() ? "" : l.substring(indent)).append('\n');
        }
        return sb.toString();
    }

    static List<Path> docs() throws IOException {
        List<Path> out = new ArrayList<>();
        out.add(REPO.resolve("README.md"));
        out.add(REPO.resolve("examples/README.md"));
        try (Stream<Path> s = Files.walk(REPO.resolve("docs"))) {
            s.filter(p -> p.toString().endsWith(".md")).sorted().forEach(out::add);
        }
        return out;
    }

    @Test
    void javaInTheDocsIsQuotedFromTheExamples() throws IOException {
        boolean write = Boolean.getBoolean("snippets.write");
        List<String> problems = new ArrayList<>();
        for (Path doc : docs()) {
            List<String> lines = new ArrayList<>(Files.readAllLines(doc));
            boolean changed = false;
            for (int i = 0; i < lines.size(); i++) {
                if (!lines.get(i).trim().equals("```java")) {
                    continue;
                }
                int close = i + 1;
                while (close < lines.size() && !lines.get(close).trim().equals("```")) {
                    close++;
                }
                String where = REPO.relativize(doc) + ":" + (i + 1);
                Matcher m = i > 0 ? MARKER.matcher(lines.get(i - 1).trim()) : null;
                if (m == null || !m.matches()) {
                    problems.add(where + ": Java block without a <!-- snippet: Example#region --> marker");
                    i = close;
                    continue;
                }
                String expected = region(m.group(1), m.group(2));
                if (expected == null) {
                    problems.add(where + ": no region " + m.group(1) + "#" + m.group(2));
                    i = close;
                    continue;
                }
                String actual = String.join("\n", lines.subList(i + 1, close)) + (close > i + 1 ? "\n" : "");
                if (!actual.equals(expected)) {
                    if (write) {
                        List<String> replacement = expected.isEmpty() ? List.of()
                            : List.of(expected.substring(0, expected.length() - 1).split("\n", -1));
                        lines.subList(i + 1, close).clear();
                        lines.addAll(i + 1, replacement);
                        close = i + 1 + replacement.size();
                        changed = true;
                    } else {
                        problems.add(where + ": differs from " + m.group(1) + "#" + m.group(2)
                            + " (run with -Dsnippets.write=true to update)");
                    }
                }
                i = close;
            }
            if (changed) {
                Files.write(doc, lines);
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
