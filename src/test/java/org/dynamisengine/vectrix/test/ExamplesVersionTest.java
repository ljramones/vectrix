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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * The separate projects in this repository (the examples and vectrix-gpu) build against this version of the library
 * and carry it themselves, so a release cannot leave them behind.
 */
class ExamplesVersionTest {

    private static String first(String xml, String regex) {
        Matcher m = Pattern.compile(regex).matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    @Test
    void examplesUseTheLibraryVersion() throws IOException {
        String lib = Files.readString(Path.of("pom.xml"));
        String ex = Files.readString(Path.of("examples/pom.xml"));
        String version = first(lib, "<artifactId>vectrix</artifactId>\\s*<version>([^<]+)</version>");
        assertEquals(version, first(ex, "<vectrix\\.version>([^<]+)</vectrix\\.version>"), "examples/pom.xml vectrix.version");
        assertEquals(version, first(ex, "<artifactId>vectrix-examples</artifactId>\\s*<version>([^<]+)</version>"),
            "examples/pom.xml version");
    }

    @Test
    void vectrixGpuUsesTheLibraryVersion() throws IOException {
        String lib = Files.readString(Path.of("pom.xml"));
        String gpu = Files.readString(Path.of("vectrix-gpu/pom.xml"));
        String version = first(lib, "<artifactId>vectrix</artifactId>\\s*<version>([^<]+)</version>");
        assertEquals(version, first(gpu, "<vectrix\\.version>([^<]+)</vectrix\\.version>"),
            "vectrix-gpu/pom.xml vectrix.version");
        assertEquals(version, first(gpu, "<artifactId>vectrix-gpu</artifactId>\\s*<version>([^<]+)</version>"),
            "vectrix-gpu/pom.xml version");
    }
}
