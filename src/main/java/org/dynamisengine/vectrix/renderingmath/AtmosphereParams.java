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
package org.dynamisengine.vectrix.renderingmath;

/**
 * Spherically-symmetric atmosphere parameters for transmittance LUT generation.
 * @since 1.0.0
 * @deprecated computed in single precision with a cancelling ground intersection, uniform sampling and no ozone or
 *     Mie absorption; use {@link org.dynamisengine.vectrix.atmosphere.Atmosphere} and
 *     {@link org.dynamisengine.vectrix.atmosphere.AtmosphereModel}.
 */
@Deprecated(since = "2.3.0")
public final class AtmosphereParams {
    /** Planet radius, in the length unit of all the parameters. */
    public final float groundRadius;
    /** Radius of the top of the atmosphere, greater than {@link #groundRadius}. */
    public final float atmosphereRadius;

    /** Height over which the Rayleigh density falls by a factor of e. */
    public final float rayleighScaleHeight;
    /** Height over which the Mie density falls by a factor of e. */
    public final float mieScaleHeight;

    /** Rayleigh extinction coefficient at the ground for the red channel, per unit length. */
    public final float betaRayleighR;
    /** Rayleigh extinction coefficient at the ground for the green channel, per unit length. */
    public final float betaRayleighG;
    /** Rayleigh extinction coefficient at the ground for the blue channel, per unit length. */
    public final float betaRayleighB;

    /** Mie extinction coefficient at the ground for the red channel, per unit length. */
    public final float betaMieR;
    /** Mie extinction coefficient at the ground for the green channel, per unit length. */
    public final float betaMieG;
    /** Mie extinction coefficient at the ground for the blue channel, per unit length. */
    public final float betaMieB;

    /**
     * Creates a parameter set. All lengths share one unit (for example metres) and the coefficients are per that
     * unit.
     *
     * @param groundRadius the planet radius, greater than 0
     * @param atmosphereRadius the radius of the top of the atmosphere, greater than {@code groundRadius}
     * @param rayleighScaleHeight the Rayleigh scale height, greater than 0
     * @param mieScaleHeight the Mie scale height, greater than 0
     * @param betaRayleighR the red Rayleigh extinction coefficient at the ground
     * @param betaRayleighG the green Rayleigh extinction coefficient at the ground
     * @param betaRayleighB the blue Rayleigh extinction coefficient at the ground
     * @param betaMieR the red Mie extinction coefficient at the ground
     * @param betaMieG the green Mie extinction coefficient at the ground
     * @param betaMieB the blue Mie extinction coefficient at the ground
     * @throws IllegalArgumentException if a radius or scale height is out of range
     */
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
