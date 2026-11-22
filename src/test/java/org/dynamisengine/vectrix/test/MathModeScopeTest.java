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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.dynamisengine.vectrix.core.CoreConfig;
import org.dynamisengine.vectrix.core.MathMode;
import org.dynamisengine.vectrix.experimental.KernelConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Thread-confined math-mode overrides ({@link CoreConfig#runWithMathMode}, {@link CoreConfig#callWithMathMode}). */
class MathModeScopeTest {

    private MathMode saved;

    @BeforeEach
    void saveDefault() {
        saved = CoreConfig.defaultMathMode();
        CoreConfig.setMathMode(MathMode.FAST);
    }

    @AfterEach
    void restoreDefault() {
        CoreConfig.setMathMode(saved);
    }

    @Test
    void overrideAppliesInsideTheTaskOnly() {
        CoreConfig.runWithMathMode(MathMode.STRICT, () -> {
            assertEquals(MathMode.STRICT, CoreConfig.mathMode());
            assertEquals(MathMode.STRICT, KernelConfig.mathMode());
            assertEquals(MathMode.FAST, CoreConfig.defaultMathMode());
            assertTrue(CoreConfig.determinismFingerprint().startsWith("vectrix.math.mode=STRICT;"));
        });
        assertEquals(MathMode.FAST, CoreConfig.mathMode());
    }

    @Test
    void overridesNestAndReturnValues() {
        String r = CoreConfig.callWithMathMode(MathMode.STRICT, () -> {
            String inner = CoreConfig.callWithMathMode(MathMode.FAST, () -> CoreConfig.mathMode().name());
            return inner + "/" + CoreConfig.mathMode();
        });
        assertEquals("FAST/STRICT", r);
    }

    @Test
    void overrideEndsWhenTheTaskThrows() {
        assertThrows(IOException.class, () -> CoreConfig.callWithMathMode(MathMode.STRICT, () -> {
            throw new IOException("checked exceptions propagate unchanged");
        }));
        assertThrows(IllegalStateException.class, () -> CoreConfig.runWithMathMode(MathMode.STRICT, () -> {
            throw new IllegalStateException();
        }));
        assertEquals(MathMode.FAST, CoreConfig.mathMode());
    }

    @Test
    void settingTheDefaultDoesNotDisturbAnActiveOverride() {
        CoreConfig.runWithMathMode(MathMode.STRICT, () -> {
            CoreConfig.setMathMode(MathMode.FAST);
            assertEquals(MathMode.STRICT, CoreConfig.mathMode());
        });
        CoreConfig.runWithMathMode(MathMode.FAST, () -> CoreConfig.setMathMode(MathMode.STRICT));
        assertEquals(MathMode.STRICT, CoreConfig.mathMode());
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CoreConfig.runWithMathMode(null, () -> { }));
        assertThrows(IllegalArgumentException.class, () -> CoreConfig.runWithMathMode(MathMode.STRICT, null));
        assertThrows(IllegalArgumentException.class, () -> CoreConfig.callWithMathMode(MathMode.STRICT, null));
    }

    @Test
    void otherThreadsKeepTheDefault() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch checked = new CountDownLatch(1);
        AtomicReference<MathMode> seenInside = new AtomicReference<>();
        Thread t = Thread.ofPlatform().start(() -> CoreConfig.runWithMathMode(MathMode.STRICT, () -> {
            inside.countDown();
            try {
                checked.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            seenInside.set(CoreConfig.mathMode());
        }));
        assertTrue(inside.await(10, TimeUnit.SECONDS));
        assertEquals(MathMode.FAST, CoreConfig.mathMode(), "another thread's override leaked into this one");
        checked.countDown();
        t.join(10_000);
        assertEquals(MathMode.STRICT, seenInside.get());
    }

    @Test
    void concurrentOverridesStayConfined() throws Exception {
        AtomicInteger wrong = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            MathMode mine = (i & 1) == 0 ? MathMode.STRICT : MathMode.FAST;
            boolean overrides = i % 4 != 3;
            threads.add(Thread.ofVirtual().start(() -> {
                for (int k = 0; k < 2_000; k++) {
                    if (overrides) {
                        CoreConfig.runWithMathMode(mine, () -> {
                            if (CoreConfig.mathMode() != mine) {
                                wrong.incrementAndGet();
                            }
                        });
                    } else if (CoreConfig.mathMode() != MathMode.FAST) {
                        wrong.incrementAndGet();
                    }
                    Thread.yield();
                }
            }));
        }
        for (Thread t : threads) {
            t.join(30_000);
        }
        assertEquals(0, wrong.get());
        assertEquals(MathMode.FAST, CoreConfig.mathMode());
    }
}
