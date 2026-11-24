// Shared code of the format kernels: ports of org.dynamisengine.vectrix.nn.Formats and the packing of 8- and 16-bit
// data in 32-bit buffer words. Element i of a packed buffer is at byte i (8-bit) or bytes 2i, 2i + 1 (16-bit) of the
// little-endian buffer: element 0 in the low bits of word 0.
//
// Everything that reads a float's value works on its raw bits where the Java arithmetic is exact, so the results do
// not depend on whether the device flushes subnormal floats to zero: inputs are loaded as uint, and the outputs are
// stored as uint bit patterns.
import common;

// The 8-bit element i of `buf`, zero-extended.
uint packedByte(RWStructuredBuffer<uint> buf, uint i)
{
    return (buf[i >> 2] >> ((i & 3u) * 8u)) & 0xFFu;
}

// The 8-bit element i of `buf` as a Java byte: sign-extended.
int packedInt8(RWStructuredBuffer<uint> buf, uint i)
{
    return int(packedByte(buf, i) << 24) >> 24;
}

// The 16-bit element i of `buf`, zero-extended.
uint packedHalf(RWStructuredBuffer<uint> buf, uint i)
{
    return (buf[i >> 1] >> ((i & 1u) * 16u)) & 0xFFFFu;
}

// Whether float bits b are a NaN (Java's x != x).
bool isNanBits(uint b)
{
    return (b & 0x7FFFFFFFu) > 0x7F800000u;
}

// Formats.toBf16 on the raw bits of a float: the bfloat16 bits, in the low 16 bits.
uint toBf16Bits(uint b)
{
    if (isNanBits(b))
    {
        return ((b >> 16) | 0x40u) & 0xFFFFu;
    }
    return ((b + 0x7FFFu + ((b >> 16) & 1u)) >> 16) & 0xFFFFu;
}

// The value of the non-negative finite FP8 code k (Formats' table entry k, below its first infinity or NaN code).
// E4M3: subnormals m·2^-9, normals 1.m·2^(e-7). E5M2: subnormals m·2^-16, normals 1.m·2^(e-15). All of them are
// normal floats, and the products below are exact.
float fp8Positive(uint k, uint e5m2)
{
    if (e5m2 != 0u)
    {
        uint e = k >> 2;
        uint m = k & 3u;
        if (e == 0u)
        {
            return float(m) * asfloat(0x37800000u); // 2^-16
        }
        return asfloat(((e + 112u) << 23) | (m << 21));
    }
    uint e = k >> 3;
    uint m = k & 7u;
    if (e == 0u)
    {
        return float(m) * asfloat(0x3B000000u); // 2^-9
    }
    return asfloat(((e + 120u) << 23) | (m << 20));
}

// Formats.fromE4M3 / fromE5M2: the float bits of FP8 code c (0..255). The Java table holds s·v with s = ±1, so
// code 0x80 is -0 and every NaN code gives Float.NaN (0x7FC00000), whatever its sign.
uint fp8ToFloatBits(uint c, uint e5m2)
{
    uint mag = c & 0x7Fu;
    uint sign = (c & 0x80u) << 24;
    if (e5m2 != 0u)
    {
        if (mag == 0x7Cu)
        {
            return sign | 0x7F800000u;
        }
        if (mag > 0x7Cu)
        {
            return 0x7FC00000u;
        }
    }
    else if (mag == 0x7Fu)
    {
        return 0x7FC00000u;
    }
    return sign | asuint(fp8Positive(mag, e5m2));
}

// Formats.encode: the FP8 code of float bits xb, rounding to nearest, ties to even, by the Java's search of the
// ascending positive values (code k has the k-th value, so the table is fp8Positive). E4M3 has 127 finite positive
// codes and overflows to NaN; E5M2 has 124 and overflows to infinity.
uint fp8FromFloatBits(uint xb, uint e5m2, uint saturate)
{
    uint sign = (xb & 0x80000000u) != 0u ? 0x80u : 0u;
    if (isNanBits(xb))
    {
        return 0x7Fu; // NaN in both formats
    }
    float a = asfloat(xb & 0x7FFFFFFFu); // Math.abs
    int count = e5m2 != 0u ? 124 : 127;
    float maxValue = fp8Positive(uint(count - 1), e5m2);
    int maxCode = count - 1;
    // the virtual value just past max, one ulp of max beyond it
    float over = 2.0f * maxValue - fp8Positive(uint(count - 2), e5m2);
    int code;
    if (a >= over || a == asfloat(0x7F800000u))
    {
        code = -1;
    }
    else if (a > maxValue)
    {
        // between max and the virtual next value: the nearer wins, a tie goes to the even code
        float dm = a - maxValue;
        float dv = over - a;
        code = dm < dv || dm == dv && (maxCode & 1) == 0 ? maxCode : -1;
    }
    else
    {
        // the largest value <= a, by binary search over the ascending positives
        int lo = 0;
        int hi = count - 1;
        while (lo < hi)
        {
            int mid = (lo + hi + 1) >> 1;
            if (fp8Positive(uint(mid), e5m2) <= a)
            {
                lo = mid;
            }
            else
            {
                hi = mid - 1;
            }
        }
        float v0 = fp8Positive(uint(lo), e5m2);
        if (v0 == a || lo == count - 1)
        {
            code = lo;
        }
        else
        {
            float v1 = fp8Positive(uint(lo + 1), e5m2);
            float d0 = a - v0;
            float d1 = v1 - a;
            int c0 = lo;
            int c1 = lo + 1;
            code = d0 < d1 ? c0 : d1 < d0 ? c1 : (c0 & 1) == 0 ? c0 : c1;
        }
    }
    if (code < 0) // overflow
    {
        if (saturate != 0u)
        {
            code = maxCode;
        }
        else
        {
            return e5m2 != 0u ? 0x7Cu | sign : 0x7Fu | sign; // E5M2: infinity; E4M3: NaN
        }
    }
    return uint(code) | sign;
}

// x·2^64 for float bits b with |x| < 2^63, exactly, without a subnormal operand (a subnormal x is rebuilt from its
// integer significand). The quotient of two such values is the quotient of the originals.
float times2p64(uint b)
{
    uint mag = b & 0x7FFFFFFFu;
    float r = mag < 0x00800000u ? float(mag) * asfloat(0x15000000u) : asfloat(mag + 0x20000000u); // 2^-85; +64 exp
    return asfloat(asuint(r) | (b & 0x80000000u));
}

// The bits a non-finite block's scale gets on the GPU (the CPU method rejects such input).
static const uint NONFINITE_SCALE = 0x7FC00000u;

// Formats.quantizeInt8's scale of a block whose largest magnitude has bits amax: amax / 127f, or for a scale below
// 2^-126 the smallest float >= amax / 127. Below amax = 2^-118 the division runs on the operands times 2^64 (normal
// floats, the same quotient), so a device that flushes subnormals gives the CPU's bits:
//   - when fl(amax·2^64 / 127) >= 2^-62, the CPU's amax / 127f is that value times 2^-64. (Between 2^-126 - 2^-151
//     and 2^-126 the 24-bit rounding gives 2^-62, and the CPU rounds to the subnormal grid, also up to 2^-126.)
//   - otherwise the CPU rounds amax / 127.0 up to the float grid, which there is the multiples of 2^-149: with
//     amax = u·2^-149 (u an integer below 2^31) the scale is ceil(u / 127)·2^-149, whose float bits are the integer
//     itself (it is below 2^24). The double rounding in the Java cannot land on a grid point: u - 127·k is a nonzero
//     integer, so amax / 127 is at least 2^-149 / 127 from every multiple of 2^-149, far beyond a double's ulp.
uint int8Scale(uint amax)
{
    if (amax == 0u)
    {
        return 0u;
    }
    if (amax >= 0x04800000u) // 2^-118: the scale is at least 2^-125
    {
        return asuint(asfloat(amax) / 127.0f);
    }
    float s64 = times2p64(amax) / 127.0f;
    if (asuint(s64) >= 0x20800000u) // 2^-62
    {
        return asuint(s64) - 0x20000000u;
    }
    uint u = amax < 0x00800000u ? amax : ((amax & 0x007FFFFFu) | 0x00800000u) << ((amax >> 23) - 1u);
    return (u + 126u) / 127u;
}

// Formats.quantizeInt8's code of float bits xb in a block of scale bits sb: (byte) clamp(rint(x / scale), ±127), as
// the low 8 bits. A scale below 2^-100 divides the operands times 2^64 (the same quotient, without subnormals); above
// it, a subnormal x has |x / scale| < 2^-26, whose code is 0 whether or not the device flushes x.
uint int8Code(uint xb, uint sb)
{
    if (sb == 0u || sb == NONFINITE_SCALE)
    {
        return 0u;
    }
    float v = sb < 0x0D800000u ? times2p64(xb) / times2p64(sb) : asfloat(xb) / asfloat(sb); // 2^-100
    float r = (v + 12582912.0f) - 12582912.0f; // Math.rint: |v| <= 128 here, and 1.5·2^23 is even
    r = jmax(-127.0f, jmin(127.0f, r));
    return uint(int(r)) & 0xFFu;
}

// Formats.dequantizeInt8's q·scale for scale bits sb, as float bits. A subnormal scale u·2^-149 multiplies in
// integers: q·u is below 2^30, so the product is q·u·2^-149 rounded once (by the conversion to float), the bits the
// CPU computes even on a device that flushes subnormals.
uint int8Dequantize(int q, uint sb)
{
    uint mag = sb & 0x7FFFFFFFu;
    if (mag == 0u || mag >= 0x00800000u)
    {
        return asuint(float(q) * asfloat(sb));
    }
    uint sign = (sb ^ (q < 0 ? 0x80000000u : 0u)) & 0x80000000u;
    uint prod = uint(q < 0 ? -q : q) * mag;
    if (prod < 0x01000000u)
    {
        return sign | prod; // exact: the float with these bits is prod·2^-149
    }
    return sign | (asuint(float(prod)) - (149u << 23));
}
