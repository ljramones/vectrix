/**
 * Vectrix: math for real-time graphics, simulation, signal processing and machine learning. Every package is
 * exported; see {@code docs/package-map.md} for what each one holds and how they depend on each other.
 */
module org.dynamisengine.vectrix.ml {
    requires transitive org.dynamisengine.vectrix;

    exports org.dynamisengine.vectrix.nn;
}
