// Activations.dropout: y[yOff + e] = Philox.uniform(seed, start + e) >= prob ? x[xOff + e] * scale : 0, for e in
// [0, n), scale = 1 / (1 - prob) computed by the wrapper as the Java does. Java twin:
// org.dynamisengine.vectrix.nn.Activations.dropout.
//
// Thread t draws Philox counter (start >> 2) + t and handles the up to four elements whose stream positions are in
// that block, as philox.slang does. Each thread reads an element before writing it, so y may be x at the same offset.
import philox_block;

void dropoutCounter(RWStructuredBuffer<float> x, uint xOff, RWStructuredBuffer<float> y, uint yOff, uint seedLo,
        uint seedHi, uint startLo, uint startHi, float prob, float scale, uint n, uint t)
{
    uint skip = startLo & 3u;
    // counters covering [start, start + n): ceil((skip + n) / 4), with n < 2^31
    if (t >= (skip + n + 3u) / 4u)
    {
        return;
    }
    uint4 words = streamBlock(seedLo, seedHi, startLo, startHi, t);
    [ForceUnroll]
    for (uint w = 0; w < 4; w++)
    {
        uint k = t * 4u + w; // the stream position, relative to start - skip
        if (k >= skip && k - skip < n)
        {
            uint e = k - skip;
            y[yOff + e] = uniformOf(words[w]) >= prob ? x[xOff + e] * scale : 0.0f;
        }
    }
}
