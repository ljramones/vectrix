// Rotary position embedding, nn.Rope.apply and applyBackward: each thread rotates one pair of one vector. The angle,
// its sine and cosine (one shared argument reduction, as Rope.rotate inlines ReproducibleMath.sin/cos) and the
// rotation are computed in double with f64.slang and rounded to float once, operation for operation as the Java;
// constants are NnConstants' bits. Vector v starts at xOff + v·ldx; its position is positions[posOff + v / group]
// (int64), so `group` vectors (the heads of a token) share one. Java twin: org.dynamisengine.vectrix.nn.Rope.
import f64;

struct RopeParams
{
    uint half;
    uint interleaved;
    uint backward;
    uint xOff;
    uint ldx;
    uint count;
    uint posOff;
    uint group;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint64_t> invFreq;
[[vk::binding(2, 0)]] RWStructuredBuffer<uint64_t> positions;
[[vk::push_constant]] ConstantBuffer<RopeParams> p;

static const uint64_t SIN_D[8] = {
    0xBFC5555555555555ULL,
    0x3F81111111111111ULL,
    0xBF2A01A01A01A01AULL,
    0x3EC71DE3A556C734ULL,
    0xBE5AE64567F544E4ULL,
    0x3DE6124613A86D09ULL,
    0xBD6AE7F3E733B81FULL,
    0x3CE952C77030AD4AULL};
static const uint64_t COS_D[9] = {
    0xBFE0000000000000ULL,
    0x3FA5555555555555ULL,
    0xBF56C16C16C16C17ULL,
    0x3EFA01A01A01A01AULL,
    0xBE927E4FB7789F5CULL,
    0x3E21EED8EFF8D898ULL,
    0xBDA93974A8C07C9DULL,
    0x3D2AE7F3E733B81FULL,
    0xBCA6827863B97D97ULL};
static const uint64_t TWO_OVER_PI_D = 0x3FE45F306DC9C883ULL;
static const uint64_t ROUND_D = 0x4338000000000000ULL;
static const uint64_t NEG_PIO2_1 = 0xBFF921FB54400000ULL;
static const uint64_t NEG_PIO2_2 = 0xBDD0B4611A600000ULL;
static const uint64_t NEG_PIO2_3 = 0xBBA3198A2E000000ULL;
static const uint64_t NEG_PIO2_3T = 0xB97B839A252049C1ULL;
static const uint64_t NEG_ONE_D = 0xBFF0000000000000ULL;

[shader("compute")]
[numthreads(64, 1, 1)]
void rope(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint t = (group.y * p.gridX + group.x) * 64 + local.x;
    if (t >= p.count * p.half)
    {
        return;
    }
    uint v = t / p.half;
    uint i = t - v * p.half;
    uint64_t a = dMul(dFromLong(int64_t(positions[p.posOff + v / p.group])), invFreq[i]);
    uint64_t kf = dSub(dAdd(dMul(a, TWO_OVER_PI_D), ROUND_D), ROUND_D);
    uint64_t r = dFma(kf, NEG_PIO2_1, a);
    r = dFma(kf, NEG_PIO2_2, r);
    r = dFma(kf, NEG_PIO2_3, r);
    r = dFma(kf, NEG_PIO2_3T, r);
    uint64_t z = dMul(r, r);
    uint64_t ps = SIN_D[7];
    for (int n = 6; n >= 0; n--)
    {
        ps = dFma(ps, z, SIN_D[n]);
    }
    uint64_t pc = COS_D[8];
    for (int n = 7; n >= 0; n--)
    {
        pc = dFma(pc, z, COS_D[n]);
    }
    uint64_t sn = dFma(dMul(r, z), ps, r);
    uint64_t cs = dFma(z, pc, D_ONE);
    uint q = uint(dToLong(kf) & 3);
    uint64_t s = q == 0 ? sn : q == 1 ? cs : q == 2 ? dNeg(sn) : dNeg(cs);
    uint64_t c = q == 0 ? cs : q == 1 ? dNeg(sn) : q == 2 ? dNeg(cs) : sn;
    s = dMul(s, p.backward != 0 ? NEG_ONE_D : D_ONE);
    uint base = p.xOff + v * p.ldx;
    uint i0 = p.interleaved != 0 ? base + 2 * i : base + i;
    uint i1 = p.interleaved != 0 ? i0 + 1 : i0 + p.half;
    uint64_t x0 = dFromFloat(x[i0]);
    uint64_t x1 = dFromFloat(x[i1]);
    x[i0] = dToFloat(dSub(dMul(x0, c), dMul(x1, s)));
    x[i1] = dToFloat(dAdd(dMul(x0, s), dMul(x1, c)));
}
