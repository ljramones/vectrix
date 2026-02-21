/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
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
package org.vectrix.simd;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.vectrix.core.Internal;
import org.vectrix.experimental.KernelConfig;

/**
 * Runtime SIMD capability probing without a static Vector API dependency.
 */
@Internal("Internal backend probing; not a stable public API.")
public final class SimdSupport {
    public enum Backend {
        SCALAR,
        VECTOR_API
    }

    private static final Capability CAPABILITY = detect();

    private SimdSupport() {
    }

    public static Backend backend() {
        if (!KernelConfig.simdEnabled()) {
            return Backend.SCALAR;
        }
        return CAPABILITY.vectorApiAvailable ? Backend.VECTOR_API : Backend.SCALAR;
    }

    public static boolean isVectorApiAvailable() {
        return CAPABILITY.vectorApiAvailable;
    }

    public static int preferredFloatLanes() {
        return CAPABILITY.preferredFloatLanes;
    }

    private static Capability detect() {
        try {
            Class<?> floatVectorClass = Class.forName("jdk.incubator.vector.FloatVector");
            Field speciesPreferredField = floatVectorClass.getField("SPECIES_PREFERRED");
            Object speciesPreferred = speciesPreferredField.get(null);
            Method lengthMethod = speciesPreferred.getClass().getMethod("length");
            int lanes = ((Integer) lengthMethod.invoke(speciesPreferred)).intValue();
            if (lanes < 1) {
                lanes = 1;
            }
            return new Capability(true, lanes);
        } catch (Throwable t) {
            return new Capability(false, 1);
        }
    }

    private static final class Capability {
        final boolean vectorApiAvailable;
        final int preferredFloatLanes;

        Capability(boolean vectorApiAvailable, int preferredFloatLanes) {
            this.vectorApiAvailable = vectorApiAvailable;
            this.preferredFloatLanes = preferredFloatLanes;
        }
    }
}
