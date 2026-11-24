// Philox.word and Philox.uniform streams: dest[off + e] = word(seed, start + e), or uniform(seed, start + e), for e
// in [0, n). Integer arithmetic only, so every device matches the CPU bit for bit. Java twin:
// org.dynamisengine.vectrix.hash.Philox.
//
// One thread per Philox counter writes up to the four words of its block. The stream index is an unsigned 64-bit
// value that wraps, as in Java; the counter is index / 4 (62 bits).
import common;

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

// The full 64-bit product of two 32-bit words, as (low, high), without 64-bit integer support.
uint2 mulWide(uint x, uint y)
{
    uint x0 = x & 0xFFFFu;
    uint x1 = x >> 16;
    uint y0 = y & 0xFFFFu;
    uint y1 = y >> 16;
    uint p00 = x0 * y0;
    uint p01 = x0 * y1;
    uint p10 = x1 * y0;
    uint p11 = x1 * y1;
    uint mid = (p00 >> 16) + (p01 & 0xFFFFu) + (p10 & 0xFFFFu);
    uint hi = p11 + (p01 >> 16) + (p10 >> 16) + (mid >> 16);
    return uint2(x * y, hi);
}

// Philox.block: Philox-4x32-10.
uint4 philoxBlock(uint4 ctr, uint2 key)
{
    uint c0 = ctr.x;
    uint c1 = ctr.y;
    uint c2 = ctr.z;
    uint c3 = ctr.w;
    uint k0 = key.x;
    uint k1 = key.y;
    for (int r = 0; r < 10; r++)
    {
        uint2 p0 = mulWide(0xD2511F53u, c0);
        uint2 p1 = mulWide(0xCD9E8D57u, c2);
        uint n0 = p1.y ^ c1 ^ k0;
        uint n2 = p0.y ^ c3 ^ k1;
        c0 = n0;
        c1 = p1.x;
        c2 = n2;
        c3 = p0.x;
        k0 += 0x9E3779B9u;
        k1 += 0xBB67AE85u;
    }
    return uint4(c0, c1, c2, c3);
}

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
    // counter = (start >> 2) + t, modulo 2^62
    uint base = (p.startLo >> 2) | (p.startHi << 30);
    uint lo = base + t;
    uint hi = ((p.startHi >> 2) + (lo < base ? 1u : 0u)) & 0x3FFFFFFFu;
    uint4 words = philoxBlock(uint4(lo, hi, 0u, 0u), uint2(p.seedLo, p.seedHi));
    [ForceUnroll]
    for (uint w = 0; w < 4; w++)
    {
        int e = int(t * 4u + w) - int(skip);
        if (e >= 0 && uint(e) < p.n)
        {
            uint word = words[w];
            // Philox.uniform: the top 24 bits times 2^-24, exact
            dest[p.off + uint(e)] = p.uniform != 0 ? asuint(float(word >> 8) * asfloat(0x33800000u)) : word;
        }
    }
}
