// Philox.word and Philox.uniform streams: dest[off + e] = word(seed, start + e), or uniform(seed, start + e), for e
// in [0, n). Integer arithmetic only, so every device matches the CPU bit for bit. Java twin:
// org.dynamisengine.vectrix.hash.Philox.
//
// One thread per Philox counter writes up to the four words of its block. The stream index is an unsigned 64-bit
// value that wraps, as in Java; the counter is index / 4 (62 bits).
import common;
import philox_block;

struct PhiloxParams
{
    uint seedLo;
    uint seedHi;
    uint startLo;
    uint startHi;
    uint off;
    uint n;
    uint uniform;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint> dest;
[[vk::push_constant]] ConstantBuffer<PhiloxParams> p;

[shader("compute")]
[numthreads(64, 1, 1)]
void philox(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint t = (group.y * p.gridX + group.x) * 64 + local.x;
    uint skip = p.startLo & 3u;
    // counters covering [start, start + n): ceil((skip + n) / 4), with n < 2^31
    if (t >= (skip + p.n + 3u) / 4u)
    {
        return;
    }
    uint4 words = streamBlock(p.seedLo, p.seedHi, p.startLo, p.startHi, t);
    [ForceUnroll]
    for (uint w = 0; w < 4; w++)
    {
        int e = int(t * 4u + w) - int(skip);
        if (e >= 0 && uint(e) < p.n)
        {
            uint word = words[w];
            dest[p.off + uint(e)] = p.uniform != 0 ? asuint(uniformOf(word)) : word;
        }
    }
}
