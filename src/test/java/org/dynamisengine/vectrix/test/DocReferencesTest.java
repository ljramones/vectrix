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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every Vectrix class and member the documentation names in {@code `code`} exists, is public, and is not deprecated
 * (unless the same line is about the deprecation). Covers the README, {@code docs/} and the examples README.
 * Forms checked: {@code Class}, {@code Class.member}, {@code package.Class}, {@code package.Class.member}, with an
 * optional argument list; JDK classes are checked for existence only.
 */
class DocReferencesTest {

    private static final String ROOT = "org.dynamisengine.vectrix.";
    private static final Pattern SPAN = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern REF = Pattern.compile(
        "(?:((?:org\\.dynamisengine\\.vectrix\\.)?[a-z][a-z0-9]*(?:\\.[a-z][a-z0-9]*)*)\\.)?([A-Z][A-Za-z0-9]*)"
            + "(?:\\.([A-Za-z_][A-Za-z0-9_]*))?(?:\\(.*\\))?");
    /** File names written in code spans ({@code README.md}), not classes. */
    private static final Pattern FILE = Pattern.compile("(?i)md|java|xml|txt|json|ya?ml|sh|py|html|csv|jar");
    /** Types of other libraries the docs mention: JMH's {@code Mode}. JMH benchmark classes are skipped too. */
    private static final java.util.Set<String> EXTERNAL = java.util.Set.of("Mode");
    private static final String[] JDK = {"java.lang.", "java.util.", "java.nio.", "java.lang.foreign.",
        "java.util.concurrent.", "jdk.incubator.vector."};

    private static Map<String, List<String>> vectrixClasses() throws IOException {
        Map<String, List<String>> bySimpleName = new HashMap<>();
        Path src = Path.of("src/main/java/org/dynamisengine/vectrix");
        try (Stream<Path> files = Files.walk(src)) {
            files.filter(p -> p.toString().endsWith(".java") && !p.endsWith("package-info.java")).forEach(p -> {
                String rel = src.relativize(p).toString().replace('/', '.').replace('\\', '.');
                String fqcn = ROOT + rel.substring(0, rel.length() - ".java".length());
                bySimpleName.computeIfAbsent(fqcn.substring(fqcn.lastIndexOf('.') + 1), k -> new ArrayList<>())
                    .add(fqcn);
            });
        }
        return bySimpleName;
    }

    static List<Path> docs() throws IOException {
        List<Path> out = new ArrayList<>(List.of(Path.of("README.md"), Path.of("examples/README.md")));
        try (Stream<Path> s = Files.walk(Path.of("docs"))) {
            s.filter(p -> p.toString().endsWith(".md")).sorted().forEach(out::add);
        }
        return out;
    }

    @Test
    void namedApisExistAndAreCurrent() throws IOException {
        Map<String, List<String>> classes = vectrixClasses();
        List<String> problems = new ArrayList<>();
        for (Path doc : docs()) {
            List<String> lines = Files.readAllLines(doc);
            boolean fenced = false;
            for (int n = 0; n < lines.size(); n++) {
                String line = lines.get(n);
                if (line.trim().startsWith("```")) {
                    fenced = !fenced;
                    continue;
                }
                if (fenced) {
                    continue;
                }
                boolean aboutDeprecation = line.toLowerCase(Locale.ROOT).contains("deprecat");
                Matcher span = SPAN.matcher(line);
                while (span.find()) {
                    Matcher m = REF.matcher(span.group(1).trim());
                    if (m.matches() && !(m.group(3) != null && FILE.matcher(m.group(3)).matches())
                            && !EXTERNAL.contains(m.group(2)) && !m.group(2).endsWith("Benchmark")) {
                        String problem = check(m.group(1), m.group(2), m.group(3), classes, aboutDeprecation);
                        if (problem != null) {
                            problems.add(doc + ":" + (n + 1) + ": `" + span.group(1) + "` " + problem);
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), problems.size() + " stale references:\n" + String.join("\n", problems));
    }

    private static final Pattern LINK = Pattern.compile("\\]\\(([^)#\\s]+)(?:#[^)]*)?\\)");

    @Test
    void relativeLinksResolve() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Path doc : docs()) {
            List<String> lines = Files.readAllLines(doc);
            for (int n = 0; n < lines.size(); n++) {
                Matcher m = LINK.matcher(lines.get(n));
                while (m.find()) {
                    String target = m.group(1);
                    if (!target.contains("://") && !target.startsWith("mailto:")
                            && !Files.exists(doc.toAbsolutePath().getParent().resolve(target).normalize())) {
                        problems.add(doc + ":" + (n + 1) + ": broken link " + target);
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    private static String check(String pkg, String cls, String member, Map<String, List<String>> classes,
            boolean aboutDeprecation) {
        List<Class<?>> candidates = new ArrayList<>();
        if (pkg != null) {
            String p = pkg.startsWith(ROOT) ? pkg : ROOT + pkg;
            if (!pkg.startsWith(ROOT) && !Files.isDirectory(Path.of("src/main/java", p.replace('.', '/')))) {
                return null; // not a Vectrix package (java.util.List, a file name, ...)
            }
            Class<?> c = load(p + "." + cls);
            if (c == null) {
                return "names a class that does not exist";
            }
            candidates.add(c);
        } else if (classes.containsKey(cls)) {
            for (String fqcn : classes.get(cls)) {
                Class<?> c = load(fqcn);
                if (c != null && Modifier.isPublic(c.getModifiers())) {
                    candidates.add(c);
                }
            }
            if (candidates.isEmpty()) {
                return "names a class that is not public";
            }
        } else {
            if (member == null || jdk(cls) != null) {
                return null; // a JDK type or a plain capitalised word
            }
            // a family name such as Quaternion or Matrix: the member must exist in one of its types
            for (Map.Entry<String, List<String>> e : classes.entrySet()) {
                if (e.getKey().startsWith(cls) && e.getKey().length() <= cls.length() + 2) {
                    for (String fqcn : e.getValue()) {
                        Class<?> c = load(fqcn);
                        if (c != null && Modifier.isPublic(c.getModifiers()) && member(c, member) != null) {
                            return null;
                        }
                    }
                }
            }
            return "names an unknown class";
        }
        // A simple name may match several classes (dsp.Spectra and color.Spectra, core.Complexf and the deprecated
        // fft.Complexf): the reference is fine when any of them is a current match.
        String problem = "names a member that does not exist";
        for (Class<?> c : candidates) {
            AnnotatedElement found = member == null ? c : member(c, member);
            if (found != null) {
                String d = deprecated(c, aboutDeprecation, "class");
                d = d != null ? d : deprecated(found, aboutDeprecation, "member");
                if (d == null) {
                    return null;
                }
                problem = d;
            }
        }
        return problem;
    }

    private static AnnotatedElement member(Class<?> c, String name) {
        AnnotatedElement fallback = null;
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name)) {
                if (!m.isAnnotationPresent(Deprecated.class)) {
                    return m;
                }
                fallback = m;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        for (Field f : c.getFields()) {
            if (f.getName().equals(name)) {
                return f;
            }
        }
        for (Class<?> n : c.getClasses()) {
            if (n.getSimpleName().equals(name)) {
                return n;
            }
        }
        return null;
    }

    private static String deprecated(AnnotatedElement e, boolean allowed, String what) {
        return !allowed && e.isAnnotationPresent(Deprecated.class) ? "names a deprecated " + what : null;
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, DocReferencesTest.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Class<?> jdk(String simple) {
        for (String p : JDK) {
            Class<?> c = load(p + simple);
            if (c != null) {
                return c;
            }
        }
        return null;
    }
}
