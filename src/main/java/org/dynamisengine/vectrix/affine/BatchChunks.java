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
package org.dynamisengine.vectrix.affine;

/**
 * Splits an index range into consecutive fixed-size chunks, for kernels that process a large batch piece by piece
 * to stay in cache. Chunks run in order on the calling thread.
 */
public final class BatchChunks {
    /** Receives one chunk of a range. */
    @FunctionalInterface
    public interface ChunkConsumer {
        /**
         * Processes the indices {@code [startInclusive, endExclusive)}.
         *
         * @param startInclusive the first index of the chunk
         * @param endExclusive one past the last index of the chunk
         */
        void accept(int startInclusive, int endExclusive);
    }

    private BatchChunks() {
    }

    /**
     * Calls {@code consumer} for {@code [0, chunkSize)}, {@code [chunkSize, 2 * chunkSize)}, and so on up to
     * {@code count}; the last chunk may be shorter. Does nothing if {@code count <= 0}.
     *
     * @param count the length of the range
     * @param chunkSize the chunk length; 0 or less means the whole range as one chunk
     * @param consumer called once per chunk, in increasing order
     */
    public static void forEachChunk(int count, int chunkSize, ChunkConsumer consumer) {
        if (count <= 0) {
            return;
        }
        int size = chunkSize <= 0 ? count : chunkSize;
        for (int i = 0; i < count; i += size) {
            int end = java.lang.Math.min(i + size, count);
            consumer.accept(i, end);
        }
    }
}
