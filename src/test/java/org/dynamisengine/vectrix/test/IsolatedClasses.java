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

import java.net.URL;
import java.net.URLClassLoader;

import org.dynamisengine.vectrix.core.Matrix4f;

/**
 * A class loader that loads the library's classes afresh, so tests can observe static initialisation (system
 * properties read into {@code static final} fields) in a known order.
 * <p>
 * The parent is the bootstrap loader, not the platform loader. Under Surefire the library sits on the module
 * path, and the built-in platform loader delegates any package of a boot-layer module to the loader that
 * defined it, so a platform-parented loader silently returns the already-loaded classes. Every JDK module the
 * library needs ({@code java.base}, {@code jdk.unsupported}) is defined to the bootstrap loader.
 */
final class IsolatedClasses {

    private IsolatedClasses() {
    }

    static URLClassLoader loader() {
        URL classes = Matrix4f.class.getProtectionDomain().getCodeSource().getLocation();
        URLClassLoader cl = new URLClassLoader(new URL[] {classes}, null);
        try {
            Class<?> probe = cl.loadClass(Matrix4f.class.getName());
            if (probe == Matrix4f.class || probe.getClassLoader() != cl) {
                throw new IllegalStateException("library classes were not isolated: " + probe.getClassLoader());
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        return cl;
    }
}
