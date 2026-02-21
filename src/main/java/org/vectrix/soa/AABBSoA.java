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
package org.vectrix.soa;

/**
 * Structure-of-arrays storage for axis-aligned bounds.
 */
public class AABBSoA {
    public final float[] minX;
    public final float[] minY;
    public final float[] minZ;
    public final float[] maxX;
    public final float[] maxY;
    public final float[] maxZ;

    public AABBSoA(int size) {
        minX = new float[size];
        minY = new float[size];
        minZ = new float[size];
        maxX = new float[size];
        maxY = new float[size];
        maxZ = new float[size];
    }

    public int size() {
        return minX.length;
    }

    public void set(int i, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.minX[i] = minX;
        this.minY[i] = minY;
        this.minZ[i] = minZ;
        this.maxX[i] = maxX;
        this.maxY[i] = maxY;
        this.maxZ[i] = maxZ;
    }
}
