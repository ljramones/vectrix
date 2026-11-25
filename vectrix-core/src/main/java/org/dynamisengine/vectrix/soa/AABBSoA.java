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
package org.dynamisengine.vectrix.soa;

/**
 * Structure-of-arrays storage for axis-aligned bounds: element {@code i} is the box from
 * {@code (minX[i], minY[i], minZ[i])} to {@code (maxX[i], maxY[i], maxZ[i])}.
 * <p>
 * The arrays are public and have a fixed length. Not thread-safe: synchronize writers yourself.
 */
public class AABBSoA {
    /**
     * The minimum x coordinate of each box.
     */
    public final float[] minX;
    /**
     * The minimum y coordinate of each box.
     */
    public final float[] minY;
    /**
     * The minimum z coordinate of each box.
     */
    public final float[] minZ;
    /**
     * The maximum x coordinate of each box.
     */
    public final float[] maxX;
    /**
     * The maximum y coordinate of each box.
     */
    public final float[] maxY;
    /**
     * The maximum z coordinate of each box.
     */
    public final float[] maxZ;

    /**
     * Creates storage for {@code size} boxes, all with every coordinate 0.
     *
     * @param size the number of boxes
     */
    public AABBSoA(int size) {
        minX = new float[size];
        minY = new float[size];
        minZ = new float[size];
        maxX = new float[size];
        maxY = new float[size];
        maxZ = new float[size];
    }

    /**
     * Returns the number of boxes.
     *
     * @return the length of the arrays
     */
    public int size() {
        return minX.length;
    }

    /**
     * Sets box {@code i}. The bounds are stored as given, without checking that min is at most max.
     *
     * @param i the box index
     * @param minX the minimum x
     * @param minY the minimum y
     * @param minZ the minimum z
     * @param maxX the maximum x
     * @param maxY the maximum y
     * @param maxZ the maximum z
     */
    public void set(int i, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX[i] = minX;
        this.minY[i] = minY;
        this.minZ[i] = minZ;
        this.maxX[i] = maxX;
        this.maxY[i] = maxY;
        this.maxZ[i] = maxZ;
    }
}
