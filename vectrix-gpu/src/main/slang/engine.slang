// Shared helpers for the engine kernels (culling, transforms, skinning, packed affine): ports of Vectrix code that
// must round exactly as the Java does, and the addressing of the structure-of-arrays buffers.
//
// An SoA buffer holds each field of a Vectrix SoA type as a plane: field f of element i is at off + f * stride + i.
import common;

// Java's 0.0f + v: v, except that -0 becomes +0 (NaN stays NaN). Slang folds 0 + v to v even at -O0, so a sum that
// Java starts at 0.0f must take its first term through this.
float plusZero(float v)
{
    return v == 0.0f ? asfloat(asuint(v) & 0x7FFFFFFFu) : v;
}

// Term k of a Java sum that starts at 0.0f: plusZero(v) for the first term, sum + v after it.
float add(uint k, float sum, float v)
{
    return k == 0 ? plusZero(v) : sum + v;
}

// The full 64-bit product of two 32-bit words, as (low, high), without 64-bit integer support.
uint2 engineMulWide(uint x, uint y)
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

// Whether r * m^2 < 2^73, exactly, for r < 2^25 and m < 2^26 (the product stays below 2^77).
bool belowTwoPow73(uint r, uint m)
{
    uint2 m2 = engineMulWide(m, m);
    uint2 a = engineMulWide(r, m2.x);
    uint2 b = engineMulWide(r, m2.y);
    uint w1 = a.y + b.x;
    uint carry = w1 < a.y ? 1u : 0u;
    return b.y + carry < 512u;
}

// Vectrix core.Math.invsqrt(float): (float) (1.0 / Math.sqrt(x)), evaluated in double on the CPU. That value is the
// correctly rounded 1/sqrt(x) for every float (checked exhaustively against the Java), which this computes without
// FP64: x = r * 4^k with r in [1, 4) as the integer R * 2^-23, a float estimate y of 1/sqrt(r) in [0.5, 1] as the
// integer Y * 2^-24, then Y moved to the correctly rounded value by exact integer tests of the midpoints
// (1/sqrt(r) > (2Y + 1) * 2^-25 exactly when R * (2Y + 1)^2 < 2^73; never equal), and the result scaled by 2^-k.
float vectrixInvsqrt(float x)
{
    if (!(x > 0.0f))
    {
        // 1/sqrt(+0) = +inf, 1/sqrt(-0) = -inf, NaN for NaN and negative values
        return x == 0.0f ? (asuint(x) != 0u ? asfloat(0xFF800000u) : asfloat(0x7F800000u)) : asfloat(0x7FC00000u);
    }
    if (x == asfloat(0x7F800000u))
    {
        return 0.0f;
    }
    uint bits = asuint(x);
    int ef = int(bits >> 23);
    uint mant = bits & 0x007FFFFFu;
    int e;
    if (ef == 0)
    {
        // subnormal: normalize the significand
        int shift = 23 - int(firstbithigh(mant));
        mant = (mant << uint(shift)) & 0x007FFFFFu;
        e = -126 - shift;
    }
    else
    {
        e = ef - 127;
    }
    int k = e >> 1;
    int par = e - 2 * k;
    uint r = (mant | 0x00800000u) << uint(par);
    float rr = asfloat((uint(127 + par) << 23) | mant);
    float y0 = 1.0f / sqrt(rr);
    uint y = uint(y0 * 16777216.0f);
    [ForceUnroll]
    for (int s = 0; s < 2; s++)
    {
        if (y < 0x01000000u && belowTwoPow73(r, 2u * y + 1u))
        {
            y++;
        }
    }
    [ForceUnroll]
    for (int s = 0; s < 2; s++)
    {
        if (y > 0x00800000u && !belowTwoPow73(r, 2u * y - 1u))
        {
            y--;
        }
    }
    return float(y) * asfloat(0x33800000u) * asfloat(uint(127 - k) << 23);
}
