/**
 * Runnable Vectrix examples. Each example is a class with a {@code run()} method returning its results, which the
 * tests check, and a {@code main} that prints them; {@link org.dynamisengine.vectrix.examples.Examples} runs any of
 * them by name.
 */
module org.dynamisengine.vectrix.examples {
    // 4.0: one module per jar. The vectrix artifact brings all four jars; a module requires the ones it uses.
    requires org.dynamisengine.vectrix;
    requires org.dynamisengine.vectrix.render;
    requires org.dynamisengine.vectrix.signal;
    requires org.dynamisengine.vectrix.ml;
}
