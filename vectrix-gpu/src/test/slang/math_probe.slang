// Test-only probe of the shared functions in common.slang: out[i] = f(x) for x = asfloat(base + i·stride), so a
// test can sweep any part of the 2^32 float bit patterns without uploading inputs. ReproducibleMathGpuTest compares
// each value with the Java function.
import common;

struct ProbeParams
{
    uint fn;
    uint base;
    uint stride;
    uint count;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<uint> outBits;
[[vk::push_constant]] ConstantBuffer<ProbeParams> p;

float evaluate(uint fn, float x)
{
    switch (fn)
    {
    case 0: return reproducibleExp(x);
    case 1: return reproducibleExpm1(x);
    case 2: return reproducibleTanh(x);
    case 3: return reproducibleSigmoid(x);
    case 4: return reproducibleLog(x);
    case 5: return reproducibleLog1p(x);
    case 6: return sqrt(x);
    case 7: return jabs(x);
    case 8: return fma(x, x, -(x * x)); // the exact error of x·x when fma is fused, 0 when it is two roundings
    default: return asfloat(0x7FC00000u);
    }
}

[shader("compute")]
[numthreads(64, 1, 1)]
void probe(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 64 + local.x;
    if (i >= p.count)
    {
        return;
    }
    outBits[i] = asuint(evaluate(p.fn, asfloat(p.base + i * p.stride)));
}
