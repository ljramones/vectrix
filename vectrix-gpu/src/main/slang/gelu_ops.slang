// The exact GELU of nn.Activations (gelu, geluBackward) and the ReproducibleMath functions it uses: erf(float),
// normalCdf, normalPdf, and their double-precision parts (exp(double), the erfc continued fraction), ported with the
// binary64 arithmetic of f64.slang. Every function mirrors the named Java method, operation for operation; constants
// are the Java constants' bits. Java twin: org.dynamisengine.vectrix.nn.Activations, ...core.ReproducibleMath.
import common;
import f64;

static const uint GELU = 0;
static const uint GELU_BACKWARD = 1;

// ReproducibleMath constants
static const uint64_t ERF_D[22] = {
    0x3FF20DD750429B6DULL,
    0xBFD812746B0379E7ULL,
    0x3FBCE2F21A042BE2ULL,
    0xBF9B82CE31288B51ULL,
    0x3F7565BCD0E6A53FULL,
    0xBF4C02DB40040B86ULL,
    0x3F1F9A326F9B89B7ULL,
    0xBEEF4D25C3E0C2EBULL,
    0x3EBB9E6C9DC651A3ULL,
    0xBE85F742EC43E71AULL,
    0x3E4FCC5720624C1CULL,
    0xBE151D7181C5D36DULL,
    0x3DD9E6AD5E55A730ULL,
    0xBD9D8453CB0C46EAULL,
    0x3D5F683AE4A97007ULL,
    0xBD1F56F071A885CFULL,
    0x3CDD70B3537F4765ULL,
    0xBC9A2007AF3447F6ULL,
    0x3C55F7919BC67B8CULL,
    0xBC118CC8A061C479ULL,
    0x3BCAB5CC31489F29ULL,
    0xBB83676FC48C133CULL};
static const uint64_t EXP_D[12] = {
    0x3FE0000000000000ULL,
    0x3FC5555555555555ULL,
    0x3FA5555555555555ULL,
    0x3F81111111111111ULL,
    0x3F56C16C16C16C17ULL,
    0x3F2A01A01A01A01AULL,
    0x3EFA01A01A01A01AULL,
    0x3EC71DE3A556C734ULL,
    0x3E927E4FB7789F5CULL,
    0x3E5AE64567F544E4ULL,
    0x3E21EED8EFF8D898ULL,
    0x3DE6124613A86D09ULL};
static const uint ERF_C[13] = {
    0x3F906EBBu,
    0xBEC093A3u,
    0x3DE71791u,
    0xBCDC1672u,
    0x3BAB2DE7u,
    0xBA6016DAu,
    0x38FCD194u,
    0xB77A692Eu,
    0x35DCF365u,
    0xB42FBA17u,
    0x327E62B9u,
    0xB0A8EB8Cu,
    0x2ECF356Bu};
static const uint64_t INV_SQRT2_D = 0x3FE6A09E667F3BCDULL;
static const uint64_t SQRT_PI_D = 0x3FFC5BF891B4EF6AULL;
static const uint64_t LOG2E_D = 0x3FF71547652B82FEULL;
static const uint64_t NEG_LN2_HI_D = 0xBFE62E42FF000000ULL;
static const uint64_t NEG_LN2_LO_D = 0x3DC718432A1B0E26ULL;
static const uint64_t ROUND_D = 0x4338000000000000ULL;
static const uint64_t HALF_D = 0x3FE0000000000000ULL;
static const uint64_t NEG_HALF_D = 0xBFE0000000000000ULL;
static const uint64_t TEN_POINT_ONE_D = 0x4024333333333333ULL;
static const uint64_t NEG_746_D = 0xC087500000000000ULL;
static const uint64_t D710_D = 0x4086300000000000ULL;
static const uint64_t INV_SQRT_2PI_D = 0x3FD9884533D43651ULL;  // 0.3989422804014327
static const uint SQRT_PI_BITS = 0x3FE2DFC5u;       // 1.7724539f
static const uint INV_SQRT2_BITS = 0x3F3504F3u;     // 0.70710677f
static const uint SQRT2_BITS = 0x3FB504F3u;         // 1.4142135f

// ReproducibleMath.exp(double).
uint64_t dExp(uint64_t x)
{
    if (!dLess(NEG_746_D, x))
    {
        return dIsNaN(x) ? x : 0;  // NaN, -inf, or below the smallest subnormal
    }
    if (dLess(D710_D, x))
    {
        return D_INF;
    }
    uint64_t kf = dSub(dAdd(dMul(x, LOG2E_D), ROUND_D), ROUND_D);
    uint64_t r = dFma(kf, NEG_LN2_HI_D, x);
    r = dFma(kf, NEG_LN2_LO_D, r);
    uint64_t p = EXP_D[11];
    for (int n = 10; n >= 0; n--)
    {
        p = dFma(p, r, EXP_D[n]);
    }
    int k = int(dToLong(kf));
    int k1 = k >> 1;
    return dScalb(dScalb(dAdd(dFma(p, dMul(r, r), r), D_ONE), k1), k - k1);
}

// ReproducibleMath.erfcTailD: erfc(a) for a >= 1, the continued fraction times e^-a^2.
uint64_t erfcTailD(uint64_t a)
{
    uint64_t t = a;
    for (int n = 120; n >= 1; n--)
    {
        t = dAdd(a, dDiv(dMul(HALF_D, dFromLong(int64_t(n))), t));
    }
    return dDiv(dExp(dMul(dNeg(a), a)), dMul(SQRT_PI_D, t));
}

// ReproducibleMath.erf(float).
float reproducibleErf(float x)
{
    float a = jmin(jabs(x), 3.92f);
    float a2 = a * a;
    float a2lo = fma(a, a, -a2);
    float v;
    if (a < 1.0f)
    {
        float p = asfloat(ERF_C[12]);
        for (int n = 11; n >= 0; n--)
        {
            p = fma(p, a2, asfloat(ERF_C[n]));
        }
        v = a * p;
    }
    else
    {
        float g = reproducibleExp(-a2) * (1.0f - a2lo);
        float t = a;
        for (int n = 64; n >= 1; n--)
        {
            t = a + (0.5f * float(n)) / t;
        }
        v = 1.0f - g / (asfloat(SQRT_PI_BITS) * t);
    }
    return jcopySign(jmin(v, 1.0f), x);
}

// ReproducibleMath.normalCdf(float).
float normalCdf(float x)
{
    const float SQRT2 = asfloat(SQRT2_BITS);
    if (!(x <= -SQRT2))
    {
        if (x >= SQRT2 || x != x)
        {
            return 0.5f * (1.0f + reproducibleErf(x * asfloat(INV_SQRT2_BITS)));
        }
        uint64_t a = dMul(dFromFloat(x), INV_SQRT2_D);
        uint64_t a2 = dMul(a, a);
        uint64_t p = ERF_D[21];
        for (int n = 20; n >= 0; n--)
        {
            p = dFma(p, a2, ERF_D[n]);
        }
        return dToFloat(dMul(HALF_D, dAdd(D_ONE, dMul(a, p))));
    }
    return dToFloat(dMul(HALF_D, erfcTailD(dMin(dMul(dFromFloat(-x), INV_SQRT2_D), TEN_POINT_ONE_D))));
}

// ReproducibleMath.normalPdf(float).
float normalPdf(float x)
{
    uint64_t v = dFromFloat(x);
    return dToFloat(dMul(INV_SQRT_2PI_D, dExp(dMul(dMul(NEG_HALF_D, v), v))));
}

// Activations.gelu (a = x) and geluBackward (a = x, b = dy), one element.
float geluOp(uint op, float a, float b)
{
    if (op == GELU)
    {
        return a * normalCdf(a);
    }
    float cdf = normalCdf(a);
    float pdf = normalPdf(a);
    return b * fma(a, pdf, cdf);
}
