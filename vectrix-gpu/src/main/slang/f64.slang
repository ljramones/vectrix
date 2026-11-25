// IEEE 754 binary64 arithmetic on 64-bit integers, for ports of Vectrix code that computes in double (GPUs such as
// Apple's have no FP64). Every operation rounds exactly as Java's double arithmetic does: to nearest, ties to even,
// with subnormals, signed zeros and infinities. A double is its bit pattern (Double.doubleToRawLongBits) in a
// uint64_t. NaN results are the canonical quiet NaN; callers compare NaNs as NaNs, not by payload.
//
// One core, dFma (Math.fma), computes x·y + z exactly and rounds once; dMul and dAdd are fused multiply-adds whose
// extra operation is exact (x·y + (-0), x·1 + y), so they round exactly as Java's * and +. dDiv is a restoring
// division. Kernels that include this need the device's INT64 capability.

static const uint64_t D_SIGN = 0x8000000000000000ULL;
static const uint64_t D_INF = 0x7FF0000000000000ULL;
static const uint64_t D_NAN = 0x7FF8000000000000ULL;
static const uint64_t D_MANT = 0x000FFFFFFFFFFFFFULL;
static const uint64_t D_HIDDEN = 0x0010000000000000ULL;
static const uint64_t D_ONE = 0x3FF0000000000000ULL;
static const uint64_t D_NEG_ZERO = 0x8000000000000000ULL;

bool dIsNaN(uint64_t a)
{
    return (a & ~D_SIGN) > D_INF;
}

bool dIsInf(uint64_t a)
{
    return (a & ~D_SIGN) == D_INF;
}

bool dIsZero(uint64_t a)
{
    return (a & ~D_SIGN) == 0;
}

uint64_t dNeg(uint64_t a)
{
    return a ^ D_SIGN;
}

// An unsigned 128-bit integer.
struct U128
{
    uint64_t hi;
    uint64_t lo;
};

U128 u128(uint64_t hi, uint64_t lo)
{
    U128 r;
    r.hi = hi;
    r.lo = lo;
    return r;
}

// The number of significant bits (0 for 0), by halving: no bit-scan intrinsic, whose top-bit behaviour differs
// between targets.
int bitLength64(uint64_t x)
{
    if (x == 0)
    {
        return 0;
    }
    int n = 1;
    if ((x >> 32) != 0)
    {
        n += 32;
        x >>= 32;
    }
    if ((x >> 16) != 0)
    {
        n += 16;
        x >>= 16;
    }
    if ((x >> 8) != 0)
    {
        n += 8;
        x >>= 8;
    }
    if ((x >> 4) != 0)
    {
        n += 4;
        x >>= 4;
    }
    if ((x >> 2) != 0)
    {
        n += 2;
        x >>= 2;
    }
    if ((x >> 1) != 0)
    {
        n += 1;
    }
    return n;
}

int bitLength128(U128 x)
{
    return x.hi != 0 ? 64 + bitLength64(x.hi) : bitLength64(x.lo);
}

U128 shl128(U128 x, int s)
{
    if (s <= 0)
    {
        return x;
    }
    if (s >= 128)
    {
        return u128(0, 0);
    }
    if (s >= 64)
    {
        return u128(x.lo << uint64_t(s - 64), 0);
    }
    return u128((x.hi << uint64_t(s)) | (x.lo >> uint64_t(64 - s)), x.lo << uint64_t(s));
}

// x >> s, with every bit shifted out ORed into bit 0 (sticky).
U128 shrSticky128(U128 x, int s)
{
    if (s <= 0)
    {
        return x;
    }
    if (s >= 128)
    {
        return u128(0, (x.hi | x.lo) != 0 ? 1 : 0);
    }
    U128 r;
    uint64_t lost;
    if (s >= 64)
    {
        int t = s - 64;
        r = u128(0, t == 0 ? x.hi : (x.hi >> uint64_t(t)));
        lost = x.lo | (t == 0 ? 0 : (x.hi << uint64_t(64 - t)));
    }
    else
    {
        r = u128(x.hi >> uint64_t(s), (x.lo >> uint64_t(s)) | (x.hi << uint64_t(64 - s)));
        lost = x.lo << uint64_t(64 - s);
    }
    if (lost != 0)
    {
        r.lo |= 1;
    }
    return r;
}

U128 add128(U128 a, U128 b)
{
    uint64_t lo = a.lo + b.lo;
    return u128(a.hi + b.hi + (lo < a.lo ? 1 : 0), lo);
}

U128 sub128(U128 a, U128 b)
{
    return u128(a.hi - b.hi - (a.lo < b.lo ? 1 : 0), a.lo - b.lo);
}

bool less128(U128 a, U128 b)
{
    return a.hi < b.hi || (a.hi == b.hi && a.lo < b.lo);
}

// The full 128-bit product of two 64-bit integers.
U128 mul64(uint64_t a, uint64_t b)
{
    uint64_t a0 = a & 0xFFFFFFFFULL;
    uint64_t a1 = a >> 32;
    uint64_t b0 = b & 0xFFFFFFFFULL;
    uint64_t b1 = b >> 32;
    uint64_t p00 = a0 * b0;
    uint64_t p01 = a0 * b1;
    uint64_t p10 = a1 * b0;
    uint64_t p11 = a1 * b1;
    uint64_t mid = (p00 >> 32) + (p01 & 0xFFFFFFFFULL) + (p10 & 0xFFFFFFFFULL);
    return u128(p11 + (p01 >> 32) + (p10 >> 32) + (mid >> 32), (mid << 32) | (p00 & 0xFFFFFFFFULL));
}

// The value r·2^e (r > 0, any sticky bit already in bit 0) rounded to a double with the given sign bit: at most
// 53 significant bits kept, none below 2^-1074, the rest rounded to nearest, ties to even. The callers keep at least
// two bits below a sticky bit, so it never decides a tie by itself.
uint64_t dRoundPack(uint64_t sign, U128 r, int e)
{
    int top = bitLength128(r) - 1;
    int shift = max(top - 52, -1074 - e);
    uint64_t q;
    if (shift <= 0)
    {
        q = shl128(r, -shift).lo;  // exact: at most 53 bits
    }
    else if (shift > 128)
    {
        q = 0;  // below halfway of the smallest subnormal
    }
    else
    {
        // q = floor(r / 2^shift), rem = r mod 2^shift, halfway = 2^(shift - 1)
        U128 qq;
        U128 rem;
        if (shift == 128)
        {
            qq = u128(0, 0);
            rem = r;
        }
        else if (shift >= 64)
        {
            int t = shift - 64;
            qq = u128(0, t == 0 ? r.hi : (r.hi >> uint64_t(t)));
            rem = u128(t == 0 ? 0 : (r.hi & ((1ULL << uint64_t(t)) - 1)), r.lo);
        }
        else
        {
            qq = u128(r.hi >> uint64_t(shift), (r.lo >> uint64_t(shift)) | (r.hi << uint64_t(64 - shift)));
            rem = u128(0, r.lo & ((1ULL << uint64_t(shift)) - 1));
        }
        U128 halfway = shl128(u128(0, 1), shift - 1);
        q = qq.lo;
        if (less128(halfway, rem) || (rem.hi == halfway.hi && rem.lo == halfway.lo && (q & 1) != 0))
        {
            q += 1;
        }
    }
    // q·2^(e + shift): e + shift is -1074 for a subnormal. Adding q to (biased exponent - 1) << 52 carries a
    // rounded-up significand into the exponent and leaves q < 2^52 a subnormal.
    int biasedMinusOne = e + shift + 1074;
    if (biasedMinusOne >= 2046)
    {
        return sign | D_INF;
    }
    uint64_t bits = (uint64_t(biasedMinusOne) << 52) + q;
    return sign | (bits >= D_INF ? D_INF : bits);
}

// The significand and exponent of a finite nonzero double: a = sig·2^e, sig < 2^53.
void dUnpack(uint64_t a, out uint64_t sig, out int e)
{
    int be = int((a >> 52) & 0x7FFULL);
    sig = a & D_MANT;
    if (be == 0)
    {
        e = -1074;
    }
    else
    {
        sig |= D_HIDDEN;
        e = be - 1075;
    }
}

// Math.fma(a, b, c): a·b + c, rounded once.
uint64_t dFma(uint64_t a, uint64_t b, uint64_t c)
{
    if (dIsNaN(a) || dIsNaN(b) || dIsNaN(c))
    {
        return D_NAN;
    }
    uint64_t sp = (a ^ b) & D_SIGN;
    uint64_t sc = c & D_SIGN;
    if (dIsInf(a) || dIsInf(b))
    {
        if (dIsZero(a) || dIsZero(b))
        {
            return D_NAN;
        }
        if (dIsInf(c) && sc != sp)
        {
            return D_NAN;
        }
        return sp | D_INF;
    }
    if (dIsInf(c))
    {
        return c;
    }
    if (dIsZero(a) || dIsZero(b))
    {
        if (dIsZero(c))
        {
            return sp == sc ? c : 0;
        }
        return c;
    }
    uint64_t ma;
    uint64_t mb;
    int ea;
    int eb;
    dUnpack(a, ma, ea);
    dUnpack(b, mb, eb);
    U128 p = mul64(ma, mb);
    int ep = ea + eb;
    if (dIsZero(c))
    {
        return dRoundPack(sp, p, ep);
    }
    uint64_t mc;
    int ec;
    dUnpack(c, mc, ec);
    U128 cc = u128(0, mc);
    int tp = ep + bitLength128(p) - 1;
    int tc = ec + bitLength64(mc) - 1;
    int e = max(tp, tc) - 125;  // the larger operand's leading bit lands at bit 125
    U128 x = ep - e >= 0 ? shl128(p, ep - e) : shrSticky128(p, e - ep);
    U128 y = ec - e >= 0 ? shl128(cc, ec - e) : shrSticky128(cc, e - ec);
    if (sp == sc)
    {
        return dRoundPack(sp, add128(x, y), e);
    }
    if (x.hi == y.hi && x.lo == y.lo)
    {
        return 0;  // an exact zero sum is +0 when rounding to nearest
    }
    if (less128(x, y))
    {
        return dRoundPack(sc, sub128(y, x), e);
    }
    return dRoundPack(sp, sub128(x, y), e);
}

// a·b, as Java's double *: the product plus -0 is the product, zeros included.
uint64_t dMul(uint64_t a, uint64_t b)
{
    return dFma(a, b, D_NEG_ZERO);
}

// a + b, as Java's double +.
uint64_t dAdd(uint64_t a, uint64_t b)
{
    return dFma(a, D_ONE, b);
}

uint64_t dSub(uint64_t a, uint64_t b)
{
    return dAdd(a, dNeg(b));
}

// a / b, as Java's double /: a restoring division to 58 quotient bits plus a sticky bit, rounded once.
uint64_t dDiv(uint64_t a, uint64_t b)
{
    if (dIsNaN(a) || dIsNaN(b))
    {
        return D_NAN;
    }
    uint64_t s = (a ^ b) & D_SIGN;
    if (dIsInf(a))
    {
        return dIsInf(b) ? D_NAN : s | D_INF;
    }
    if (dIsInf(b))
    {
        return s;
    }
    if (dIsZero(b))
    {
        return dIsZero(a) ? D_NAN : s | D_INF;
    }
    if (dIsZero(a))
    {
        return s;
    }
    uint64_t ma;
    uint64_t mb;
    int ea;
    int eb;
    dUnpack(a, ma, ea);
    dUnpack(b, mb, eb);
    // normalize both significands to [2^52, 2^53)
    int na = 53 - bitLength64(ma);
    int nb = 53 - bitLength64(mb);
    ma <<= uint64_t(na);
    mb <<= uint64_t(nb);
    ea -= na;
    eb -= nb;
    uint64_t rem = ma;
    uint64_t q = 0;
    for (int i = 0; i < 58; i++)
    {
        q <<= 1;
        if (rem >= mb)
        {
            rem -= mb;
            q |= 1;
        }
        rem <<= 1;
    }
    if (rem != 0)
    {
        q |= 1;
    }
    // ma/mb·2^57 = q (truncated, sticky in bit 0)
    return dRoundPack(s, u128(0, q), ea - eb - 57);
}

// Java's (double) of a float: exact.
uint64_t dFromFloat(float f)
{
    uint u = asuint(f);
    uint64_t sign = uint64_t(u & 0x80000000u) << 32;
    uint be = (u >> 23) & 0xFFu;
    uint m = u & 0x007FFFFFu;
    if (be == 0xFFu)
    {
        return m != 0 ? D_NAN : sign | D_INF;
    }
    if (be == 0)
    {
        if (m == 0)
        {
            return sign;
        }
        return dRoundPack(sign, u128(0, m), -149);
    }
    return sign | (uint64_t(be - 127 + 1023) << 52) | (uint64_t(m) << 29);
}

// Java's (double) of an int or long that fits in 63 bits: exact below 2^53, rounded to nearest above.
uint64_t dFromLong(int64_t v)
{
    if (v == 0)
    {
        return 0;
    }
    uint64_t sign = v < 0 ? D_SIGN : 0;
    uint64_t m = v < 0 ? uint64_t(-v) : uint64_t(v);
    return dRoundPack(sign, u128(0, m), 0);
}

// Java's (float) of a double: rounded to nearest, ties to even, with float subnormals and overflow to infinity.
float dToFloat(uint64_t a)
{
    uint sign = uint(a >> 32) & 0x80000000u;
    if (dIsNaN(a))
    {
        return asfloat(0x7FC00000u);
    }
    if (dIsInf(a))
    {
        return asfloat(sign | 0x7F800000u);
    }
    if (dIsZero(a))
    {
        return asfloat(sign);
    }
    uint64_t sig;
    int e;
    dUnpack(a, sig, e);
    int top = bitLength64(sig) - 1;
    int shift = max(top - 23, -149 - e);
    uint64_t q;
    if (shift >= 64)
    {
        q = 0;
        // the halfway-way point is 2^(shift-1) > sig: rounds to zero
    }
    else
    {
        q = sig >> uint64_t(shift);
        uint64_t rem = sig & ((1ULL << uint64_t(shift)) - 1);
        uint64_t halfway = 1ULL << uint64_t(shift - 1);
        if (rem > halfway || (rem == halfway && (q & 1) != 0))
        {
            q += 1;
        }
    }
    int biasedMinusOne = e + shift + 149;
    if (biasedMinusOne >= 254)
    {
        return asfloat(sign | 0x7F800000u);
    }
    uint bits = uint(biasedMinusOne) * 0x00800000u + uint(q);
    if (bits >= 0x7F800000u)
    {
        return asfloat(sign | 0x7F800000u);
    }
    return asfloat(sign | bits);
}

// Java's (long) of a double: truncation toward zero, NaN to 0, and saturation to Long.MIN_VALUE/MAX_VALUE beyond.
int64_t dToLong(uint64_t a)
{
    if (dIsNaN(a) || dIsZero(a))
    {
        return 0;
    }
    bool negative = (a & D_SIGN) != 0;
    if (dIsInf(a))
    {
        return negative ? int64_t(D_SIGN) : int64_t(~D_SIGN);
    }
    uint64_t sig;
    int e;
    dUnpack(a, sig, e);
    if (e + bitLength64(sig) > 63)
    {
        return negative ? int64_t(D_SIGN) : int64_t(~D_SIGN);  // |a| >= 2^63
    }
    uint64_t m = e >= 0 ? sig << uint64_t(e) : (e <= -64 ? 0 : sig >> uint64_t(-e));
    int64_t v = int64_t(m);
    return negative ? -v : v;
}

// a < b, as Java's double < (false if either is NaN; -0 == +0).
bool dLess(uint64_t a, uint64_t b)
{
    if (dIsNaN(a) || dIsNaN(b))
    {
        return false;
    }
    if (dIsZero(a) && dIsZero(b))
    {
        return false;
    }
    bool na = (a & D_SIGN) != 0;
    bool nb = (b & D_SIGN) != 0;
    if (na != nb)
    {
        return na;
    }
    return na ? a > b : a < b;
}

// Math.min(double, double): NaN if either is NaN; -0 is less than +0.
uint64_t dMin(uint64_t a, uint64_t b)
{
    if (dIsNaN(a) || dIsNaN(b))
    {
        return D_NAN;
    }
    if (dIsZero(a) && dIsZero(b))
    {
        return (a | b);  // -0 if either is -0
    }
    return dLess(b, a) ? b : a;
}

// Math.scalb(a, n) for -1022 <= n <= 1023: a·2^n rounded once (only into the subnormal range).
uint64_t dScalb(uint64_t a, int n)
{
    return dMul(a, uint64_t(n + 1023) << 52);
}
