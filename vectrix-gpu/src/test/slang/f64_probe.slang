// Test probe for f64.slang: out[i] = op(a[i], b[i], c[i]) on double bit patterns. Java twin: F64GpuTest.Op.
import f64;

struct F64ProbeParams
{
    uint op;
    uint n;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint64_t> a;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint64_t> b;
[[vk::binding(2, 0)]] RWStructuredBuffer<uint64_t> c;
[[vk::binding(3, 0)]] RWStructuredBuffer<uint64_t> o;
[[vk::push_constant]] ConstantBuffer<F64ProbeParams> p;

[shader("compute")]
[numthreads(64, 1, 1)]
void f64Probe(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 64 + local.x;
    if (i >= p.n)
    {
        return;
    }
    uint64_t x = a[i];
    uint64_t y = b[i];
    uint64_t z = c[i];
    uint64_t r;
    switch (p.op)
    {
    case 0: r = dFma(x, y, z); break;
    case 1: r = dMul(x, y); break;
    case 2: r = dAdd(x, y); break;
    case 3: r = dSub(x, y); break;
    case 4: r = dDiv(x, y); break;
    case 5: r = dFromFloat(asfloat(uint(x))); break;
    case 6: r = uint64_t(asuint(dToFloat(x))); break;
    case 7: r = uint64_t(dToLong(x)); break;
    case 8: r = dFromLong(int64_t(x)); break;
    case 9: r = dLess(x, y) ? 1 : 0; break;
    case 10: r = dMin(x, y); break;
    default: r = dScalb(x, int(int64_t(y))); break;
    }
    o[i] = r;
}
