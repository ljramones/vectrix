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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The top-level packages form a directed acyclic graph, with {@code core} and {@code hash} at the bottom (see
 * {@code docs/package-map.md}). A subpackage counts as part of its top-level package. The only edges allowed to point
 * upward come from deprecated forwarders kept for compatibility, which go in 3.0.
 */
class PackageDependencyTest {

    private static final Path ROOT = Path.of("src/main/java/org/dynamisengine/vectrix");
    private static final Pattern REF = Pattern.compile("org\\.dynamisengine\\.vectrix\\.([a-z0-9]+)(?:\\.[a-z0-9]+)*\\.[A-Z]");
    /** Edges kept only by deprecated forwarders, removed in 3.0. */
    private static final Set<String> DEPRECATED_EDGES = Set.of(
        "core->camera",   // Matrix4*/FrustumPlanes overloads taking camera.DepthRange
        "affine->soa");   // affine.TransformKernels, moved to soa

    static Map<String, Set<String>> graph() throws IOException {
        Map<String, Set<String>> g = new TreeMap<>();
        try (Stream<Path> files = Files.walk(ROOT)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Path rel = ROOT.relativize(f);
                if (rel.getNameCount() < 2) {
                    continue;
                }
                String pkg = rel.getName(0).toString();
                Set<String> deps = g.computeIfAbsent(pkg, k -> new TreeSet<>());
                String src = Files.readString(f).replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
                Matcher m = REF.matcher(src);
                while (m.find()) {
                    String dep = m.group(1);
                    if (!dep.equals(pkg) && !DEPRECATED_EDGES.contains(pkg + "->" + dep)) {
                        deps.add(dep);
                    }
                }
            }
        }
        return g;
    }

    @Test
    void packagesFormADag() throws IOException {
        Map<String, Set<String>> g = graph();
        Map<String, Integer> state = new HashMap<>();
        List<String> cycles = new ArrayList<>();
        for (String p : g.keySet()) {
            visit(p, g, state, new ArrayDeque<>(), cycles);
        }
        assertTrue(cycles.isEmpty(), "package cycles: " + cycles);
    }

    @Test
    void coreAndHashAreTheBottom() throws IOException {
        Map<String, Set<String>> g = graph();
        assertEquals(Set.of(), g.get("core"), "core must not depend on other packages");
        assertTrue(Set.of("core").containsAll(g.get("hash")), "hash may depend on core only: " + g.get("hash"));
    }

    private static void visit(String p, Map<String, Set<String>> g, Map<String, Integer> state, Deque<String> path,
            List<String> cycles) {
        Integer s = state.get(p);
        if (s != null) {
            if (s == 1) {
                List<String> cycle = new ArrayList<>(path);
                java.util.Collections.reverse(cycle);
                cycles.add(String.join(" -> ", cycle.subList(cycle.indexOf(p), cycle.size())) + " -> " + p);
            }
            return;
        }
        state.put(p, 1);
        path.push(p);
        for (String d : g.getOrDefault(p, Set.of())) {
            visit(d, g, state, path, cycles);
        }
        path.pop();
        state.put(p, 2);
    }
}
