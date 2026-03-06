/*
 * The MIT License
 *
 * Copyright (c) 2026 JOML
 */
package org.vectrix.bench;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.vectrix.core.Vector3f;
import org.vectrix.physics.InertiaTensorf;
import org.vectrix.physics.PbdConstraintsf;
import org.vectrix.physics.PdControllersf;
import org.vectrix.physics.SpringDamperf;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class PhysicsMathBenchmark extends ThroughputBenchmark {
    @Param({"64", "1024", "16384"})
    public int count;

    private float[] masses;
    private float[] radii;
    private float[] heights;
    private Vector3f[] angularError;
    private Vector3f[] angularVelocity;
    private Vector3f[] torqueOut;
    private Vector3f[] posA;
    private Vector3f[] posB;
    private Vector3f[] correction;
    private float[] springCurrent;
    private float[] springTarget;
    private float[] springVelocity;
    private float[] springVelTmp;
    private Vector3f[] inertiaOut;

    @Setup
    public void setup() {
        masses = new float[count];
        radii = new float[count];
        heights = new float[count];
        angularError = new Vector3f[count];
        angularVelocity = new Vector3f[count];
        torqueOut = new Vector3f[count];
        posA = new Vector3f[count];
        posB = new Vector3f[count];
        correction = new Vector3f[count];
        springCurrent = new float[count];
        springTarget = new float[count];
        springVelocity = new float[count];
        springVelTmp = new float[1];
        inertiaOut = new Vector3f[count];

        SplittableRandom rnd = new SplittableRandom(9191L);
        for (int i = 0; i < count; i++) {
            masses[i] = (float) rnd.nextDouble(1.0, 50.0);
            radii[i] = (float) rnd.nextDouble(0.1, 3.0);
            heights[i] = (float) rnd.nextDouble(0.2, 6.0);
            angularError[i] = new Vector3f((float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0), (float) rnd.nextDouble(-2.0, 2.0));
            angularVelocity[i] = new Vector3f((float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0));
            torqueOut[i] = new Vector3f();
            posA[i] = new Vector3f((float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0));
            posB[i] = new Vector3f((float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0), (float) rnd.nextDouble(-10.0, 10.0));
            correction[i] = new Vector3f();
            springCurrent[i] = (float) rnd.nextDouble(-1.0, 1.0);
            springTarget[i] = (float) rnd.nextDouble(-1.0, 1.0);
            springVelocity[i] = (float) rnd.nextDouble(-5.0, 5.0);
            inertiaOut[i] = new Vector3f();
        }
    }

    @Benchmark
    public float inertiaTensorBatch() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            InertiaTensorf.capsule(masses[i], radii[i], heights[i], inertiaOut[i]);
            sum += inertiaOut[i].x + inertiaOut[i].y + inertiaOut[i].z;
        }
        return sum;
    }

    @Benchmark
    public float pdControllerBatch() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            PdControllersf.torque(angularError[i], 12.0f, angularVelocity[i], 0.45f, torqueOut[i]);
            PdControllersf.clampTorque(torqueOut[i], 25.0f, torqueOut[i]);
            sum += torqueOut[i].x + torqueOut[i].y + torqueOut[i].z;
        }
        return sum;
    }

    @Benchmark
    public float springDamperBatch() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            float v = SpringDamperf.criticallyDamped(springCurrent[i], springTarget[i], springVelocity[i], 18.0f, 1.0f / 60.0f, springVelTmp);
            springVelocity[i] = springVelTmp[0];
            springCurrent[i] = v;
            sum += v;
        }
        return sum;
    }

    @Benchmark
    public float pbdDistanceConstraintBatch() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            PbdConstraintsf.distanceConstraint(posA[i], 1.0f, posB[i], 1.0f, 1.5f, 0.75f, correction[i]);
            sum += correction[i].x + correction[i].y + correction[i].z;
        }
        return sum;
    }
}
