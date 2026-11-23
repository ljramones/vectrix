/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix Contributors
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
/**
 * Physics math kernels, in float and double twins:
 * <ul>
 * <li>mass properties: primitive inertia, volumes, the parallel-axis shift, tensor rotation, combining bodies, and
 * exact polyhedral mass properties;</li>
 * <li>exact damped-spring steps, damping and stiffness helpers, and suspension forces;</li>
 * <li>PD controllers;</li>
 * <li>Pacejka tyre kernels;</li>
 * <li>position-based constraint projections.</li>
 * </ul>
 * These are pure functions. Vectrix never steps a simulation: bodies, solvers, integration and vehicle models belong
 * to the physics library. Contact kernels are in {@code geometry.Contacts*}.
 */
package org.dynamisengine.vectrix.physics;
