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
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every package is exported the same way to JPMS (module-info) and OSGi (the bundle's Export-Package), and every
 * package with public classes is exported. 2.5.0 shipped with eight packages missing from Export-Package.
 */
class PackageExportsTest {

    /** For each jar's module: its module-info exports, its OSGi Export-Package and its packages agree (4.0). */
    @Test
    void moduleAndOsgiExportsAgreeAndCoverEveryPackage() throws IOException {
        for (String mod : Sources.MODULES) {
            Path main = Sources.ROOT.resolve(mod).resolve("src/main/java");
            // the descriptor the build generates module-info.class from, kept out of the sources (javac would compile it)
            String mi = Files.readString(Sources.ROOT.resolve(mod).resolve("src/main/jpms/module-info.java"));
            Set<String> module = new TreeSet<>();
            Matcher m = Pattern.compile("^\\s*exports ([\\w.]+);", Pattern.MULTILINE).matcher(mi);
            while (m.find()) {
                module.add(m.group(1));
            }
            String pom = Files.readString(Sources.ROOT.resolve(mod).resolve("pom.xml"));
            Matcher e = Pattern.compile("<vectrix\\.osgi\\.exports>([^<]*)</vectrix\\.osgi\\.exports>").matcher(pom);
            assertTrue(e.find(), mod + ": no vectrix.osgi.exports");
            Set<String> osgi = new TreeSet<>(Arrays.asList(e.group(1).split(",")));
            assertEquals(module, osgi, mod + ": module-info exports and OSGi Export-Package differ");
            Set<String> packages;
            try (Stream<Path> files = Files.walk(main.resolve("org/dynamisengine/vectrix"))) {
                packages = files.filter(p -> p.toString().endsWith(".java") && !p.endsWith("package-info.java"))
                    .map(p -> main.relativize(p.getParent()).toString().replace('/', '.').replace('\\', '.'))
                    .collect(Collectors.toCollection(TreeSet::new));
            }
            assertEquals(packages, module, mod + ": packages with sources versus exported packages");
        }
    }
}
