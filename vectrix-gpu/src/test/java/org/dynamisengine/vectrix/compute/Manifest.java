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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/** The compiled kernels' ABI, as recorded by scripts/build-kernels.py from Slang's reflection. */
final class Manifest {

    static final Properties P = load();

    private Manifest() {
    }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = Manifest.class.getResourceAsStream("kernels/kernels.properties")) {
            p.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return p;
    }

    static String get(String key) {
        String v = P.getProperty(key);
        if (v == null) {
            throw new IllegalStateException("kernels.properties has no " + key);
        }
        return v;
    }

    /** Field name to byte offset of kernel {@code name}'s parameter block, in declaration order. */
    static Map<String, Integer> params(String name) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String f : get(name + ".params").split(",")) {
            String[] parts = f.split(":");
            m.put(parts[0], Integer.parseInt(parts[1]));
        }
        return m;
    }

    /** Field name to scalar type ("uint32", "float32") of kernel {@code name}'s parameter block. */
    static Map<String, String> paramTypes(String name) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String f : get(name + ".params").split(",")) {
            String[] parts = f.split(":");
            m.put(parts[0], parts[2]);
        }
        return m;
    }

    /** Buffer name to binding slot of kernel {@code name}, in slot order. */
    static Map<String, Integer> buffers(String name) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String f : get(name + ".buffers").split(",")) {
            String[] parts = f.split(":");
            m.put(parts[0], Integer.parseInt(parts[1]));
        }
        return m;
    }
}
