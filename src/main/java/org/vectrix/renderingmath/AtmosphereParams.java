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
package org.vectrix.renderingmath;

/**
 * Spherically-symmetric atmosphere parameters for transmittance LUT generation.
 */
public final class AtmosphereParams {
    public final float groundRadius;
    public final float atmosphereRadius;

    public final float rayleighScaleHeight;
    public final float mieScaleHeight;

    public final float betaRayleighR;
    public final float betaRayleighG;
    public final float betaRayleighB;

    public final float betaMieR;
    public final float betaMieG;
    public final float betaMieB;

    public AtmosphereParams(float groundRadius, float atmosphereRadius, float rayleighScaleHeight, float mieScaleHeight,
        float betaRayleighR, float betaRayleighG, float betaRayleighB, float betaMieR, float betaMieG, float betaMieB) {
        if (groundRadius <= 0.0f || atmosphereRadius <= groundRadius || rayleighScaleHeight <= 0.0f || mieScaleHeight <= 0.0f) {
            throw new IllegalArgumentException("radii/scale heights");
        }
        this.groundRadius = groundRadius;
        this.atmosphereRadius = atmosphereRadius;
        this.rayleighScaleHeight = rayleighScaleHeight;
        this.mieScaleHeight = mieScaleHeight;
        this.betaRayleighR = betaRayleighR;
        this.betaRayleighG = betaRayleighG;
        this.betaRayleighB = betaRayleighB;
        this.betaMieR = betaMieR;
        this.betaMieG = betaMieG;
        this.betaMieB = betaMieB;
    }
}
