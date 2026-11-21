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

import java.net.URL;
import java.net.URLClassLoader;

import org.dynamisengine.vectrix.core.CoreConfig;
import org.junit.jupiter.api.Test;

/**
 * Review finding H-8: loading {@code KernelConfig} used to re-apply the {@code vectrix.math.mode} property and
 * silently reset a mode already chosen through {@link CoreConfig#setMathMode}. Each scenario runs in a fresh
 * class loader so the class-initialisation order is exactly the one being tested, regardless of which classes
 * other tests have already loaded.
 */
class CoreConfigModeTest {

    private static final String CORE = "org.dynamisengine.vectrix.core.CoreConfig";
    private static final String KERNEL = "org.dynamisengine.vectrix.experimental.KernelConfig";
    private static final String MODE = "org.dynamisengine.vectrix.core.MathMode";
    /** The documented system property; a literal on purpose, since it is the public contract. */
    private static final String PROP = "vectrix.math.mode";

    private static URLClassLoader isolatedLoader() {
        URL classes = CoreConfig.class.getProtectionDomain().getCodeSource().getLocation();
        return new URLClassLoader(new URL[] {classes}, ClassLoader.getPlatformClassLoader());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object strict(ClassLoader cl) throws ReflectiveOperationException {
        return Enum.valueOf((Class<Enum>) cl.loadClass(MODE), "STRICT");
    }

    private static String mode(ClassLoader cl, String owner) throws ReflectiveOperationException {
        return String.valueOf(cl.loadClass(owner).getMethod("mathMode").invoke(null));
    }

    @Test
    void loadingKernelConfigDoesNotResetAnExplicitMode() throws Exception {
        String saved = System.clearProperty(PROP);
        try (URLClassLoader cl = isolatedLoader()) {
            Class<?> core = cl.loadClass(CORE);
            core.getMethod("setMathMode", cl.loadClass(MODE)).invoke(null, strict(cl));
            assertEquals("STRICT", mode(cl, KERNEL), "KernelConfig initialisation reverted the mode");
            assertEquals("STRICT", mode(cl, CORE));
        } finally {
            restore(saved);
        }
    }

    @Test
    void propertyIsHonouredWithoutLoadingKernelConfig() throws Exception {
        String saved = System.setProperty(PROP, "STRICT");
        try (URLClassLoader cl = isolatedLoader()) {
            assertEquals("STRICT", mode(cl, CORE), "CoreConfig ignored -Dvectrix.math.mode");
        } finally {
            restore(saved);
        }
    }

    private static void restore(String saved) {
        if (saved == null) {
            System.clearProperty(PROP);
        } else {
            System.setProperty(PROP, saved);
        }
    }
}
