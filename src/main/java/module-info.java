module org.vectrix {
    exports org.vectrix.core;
    exports org.vectrix.affine;
    exports org.vectrix.color;
    exports org.vectrix.curve;
    exports org.vectrix.curve.scalar;
    exports org.vectrix.curve.vec2;
    exports org.vectrix.curve.vec3;
    exports org.vectrix.curve.vec4;
    exports org.vectrix.easing;

    exports org.vectrix.geometry;
    exports org.vectrix.sampling;
    exports org.vectrix.hash;
    exports org.vectrix.sdf;

    exports org.vectrix.gpu;
    exports org.vectrix.renderingmath;
    exports org.vectrix.optics;
    exports org.vectrix.ltc;
    exports org.vectrix.sh;

    exports org.vectrix.fft;
    exports org.vectrix.simd;
    exports org.vectrix.soa;

    exports org.vectrix.physics;

    exports org.vectrix.experimental to
        org.dynamisphysics.ode4j,
        org.dynamisphysics.jolt,
        org.dynamislightengine;
}
