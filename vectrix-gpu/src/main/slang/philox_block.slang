// Philox-4x32-10 (org.dynamisengine.vectrix.hash.Philox.block) for the kernels that draw Philox streams: philox.slang
// and dropout_core.slang. Integer arithmetic only.

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

// The block of stream `seed` that holds positions 4·((start >> 2) + t) to that + 3: Philox counter
// (start >> 2) + t, modulo 2^62, as Philox.word computes it. Seed and start are unsigned 64-bit, split in words.
uint4 streamBlock(uint seedLo, uint seedHi, uint startLo, uint startHi, uint t)
{
    uint base = (startLo >> 2) | (startHi << 30);
    uint lo = base + t;
    uint hi = ((startHi >> 2) + (lo < base ? 1u : 0u)) & 0x3FFFFFFFu;
    return philoxBlock(uint4(lo, hi, 0u, 0u), uint2(seedLo, seedHi));
}

// Philox.uniform of a word: its top 24 bits times 2^-24, exact.
float uniformOf(uint word)
{
    return float(word >> 8) * asfloat(0x33800000u);
}
