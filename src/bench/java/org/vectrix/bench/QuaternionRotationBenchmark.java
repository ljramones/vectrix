/*
 * The MIT License
 *
 * Copyright (c) 2024 Vectrix
 */
package org.vectrix.bench;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.vectrix.core.Quaternionf;
import org.vectrix.core.Vector3f;

@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class QuaternionRotationBenchmark {
    @Param({"256", "4096", "16384"})
    public int count;

    private Quaternionf[] q0;
    private Quaternionf[] q1;
    private Vector3f[] omega;
    private final Vector3f axisY = new Vector3f(0.0f, 1.0f, 0.0f);
    private final Quaternionf swing = new Quaternionf();
    private final Quaternionf twist = new Quaternionf();
    private final Vector3f vel = new Vector3f();
    private final Quaternionf dq = new Quaternionf();

    @Setup
    public void setup() {
        q0 = new Quaternionf[count];
        q1 = new Quaternionf[count];
        omega = new Vector3f[count];
        SplittableRandom rnd = new SplittableRandom(1234);
        for (int i = 0; i < count; i++) {
            float ax = (float) (rnd.nextDouble() * 2.0 - 1.0);
            float ay = (float) (rnd.nextDouble() * 2.0 - 1.0);
            float az = (float) (rnd.nextDouble() * 2.0 - 1.0);
            float angle0 = (float) (rnd.nextDouble() * Math.PI);
            float angle1 = (float) (rnd.nextDouble() * Math.PI);
            Vector3f axis = new Vector3f(ax, ay, az).normalize();
            q0[i] = new Quaternionf().fromAxisAngleRad(axis.x, axis.y, axis.z, angle0);
            q1[i] = new Quaternionf().fromAxisAngleRad(axis.x, axis.y, axis.z, angle1);
            omega[i] = new Vector3f((float) rnd.nextDouble(), (float) rnd.nextDouble(), (float) rnd.nextDouble());
        }
    }

    @Benchmark
    public float swingTwistLoop() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            q0[i].swingTwist(axisY, swing, twist);
            sum += swing.w + twist.w;
        }
        return sum;
    }

    @Benchmark
    public float angularVelocityLoop() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            q0[i].angularVelocity(q1[i], 1.0f / 60.0f, vel);
            sum += vel.x + vel.y + vel.z;
        }
        return sum;
    }

    @Benchmark
    public float integrateAngularVelocityLoop() {
        float sum = 0.0f;
        for (int i = 0; i < count; i++) {
            Quaternionf.integrateAngularVelocity(omega[i], 1.0f / 60.0f, dq);
            sum += dq.w;
        }
        return sum;
    }
}
