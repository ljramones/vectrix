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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Where the cross-jar tests find the library's sources (4.0): the repository root, and the main sources of each jar's
 * module. The tests run in {@code vectrix-tests}, so the paths start at its parent.
 */
final class Sources {

    /** The repository root. */
    static final Path ROOT = Path.of("..");

    /** The jars' modules, in dependency order. */
    static final List<String> MODULES = List.of("vectrix-core", "vectrix-render", "vectrix-signal", "vectrix-ml");

    private Sources() {
    }

    /** Each module's {@code src/main/java}. */
    static List<Path> mainRoots() {
        return MODULES.stream().map(m -> ROOT.resolve(m).resolve("src/main/java")).toList();
    }

    /** Every file under the modules' main sources. */
    static Stream<Path> walkMain() {
        return mainRoots().stream().flatMap(r -> {
            try {
                return Files.walk(r);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** The main-source root that holds {@code file}. */
    static Path rootOf(Path file) {
        for (Path r : mainRoots()) {
            if (file.startsWith(r)) {
                return r;
            }
        }
        throw new IllegalArgumentException(file + " is not under a module's main sources");
    }

    /** Whether {@code pkg} (dotted) has main sources in some module. */
    static boolean isPackage(String pkg) {
        return mainRoots().stream().anyMatch(r -> Files.isDirectory(r.resolve(pkg.replace('.', '/'))));
    }
}
