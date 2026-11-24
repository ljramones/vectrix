// TransformKernels.composeBatch: out[i] = parents[i] * locals[i] for i in [0, count). Java twin:
// org.dynamisengine.vectrix.soa.TransformKernels (the scalar kernel; the SIMD one gives the same bits).
//
// Each operand is a TransformSoA buffer: planes tx, ty, tz, qx, qy, qz, qw, sx, sy, sz (engine.slang). One thread
// per transform.
import common;
import engine;

struct ComposeParams
{
    uint count;
    uint parentsOff;
    uint parentsStride;
    uint localsOff;
    uint localsStride;
    uint outOff;
    uint outStride;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> parents;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> locals;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> outT;
[[vk::push_constant]] ConstantBuffer<ComposeParams> p;

float P(uint field, uint i)
{
    return parents[p.parentsOff + field * p.parentsStride + i];
}

float L(uint field, uint i)
{
    return locals[p.localsOff + field * p.localsStride + i];
}

void store(uint field, uint i, float v)
{
    outT[p.outOff + field * p.outStride + i] = v;
}

[shader("compute")]
[numthreads(64, 1, 1)]
void compose(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 64 + local.x;
    if (i >= p.count)
    {
        return;
    }
    float psx = P(7, i);
    float psy = P(8, i);
    float psz = P(9, i);

    float ltx = L(0, i) * psx;
    float lty = L(1, i) * psy;
    float ltz = L(2, i) * psz;

    float pqx = P(3, i);
    float pqy = P(4, i);
    float pqz = P(5, i);
    float pqw = P(6, i);

    float tx2 = 2.0f * (pqy * ltz - pqz * lty);
    float ty2 = 2.0f * (pqz * ltx - pqx * ltz);
    float tz2 = 2.0f * (pqx * lty - pqy * ltx);
    float rx = ltx + pqw * tx2 + (pqy * tz2 - pqz * ty2);
    float ry = lty + pqw * ty2 + (pqz * tx2 - pqx * tz2);
    float rz = ltz + pqw * tz2 + (pqx * ty2 - pqy * tx2);

    float lqx = L(3, i);
    float lqy = L(4, i);
    float lqz = L(5, i);
    float lqw = L(6, i);
    float lsx = L(7, i);
    float lsy = L(8, i);
    float lsz = L(9, i);
    float ptx = P(0, i);
    float pty = P(1, i);
    float ptz = P(2, i);

    store(0, i, ptx + rx);
    store(1, i, pty + ry);
    store(2, i, ptz + rz);

    store(3, i, pqw * lqx + pqx * lqw + pqy * lqz - pqz * lqy);
    store(4, i, pqw * lqy - pqx * lqz + pqy * lqw + pqz * lqx);
    store(5, i, pqw * lqz + pqx * lqy - pqy * lqx + pqz * lqw);
    store(6, i, pqw * lqw - pqx * lqx - pqy * lqy - pqz * lqz);

    store(7, i, psx * lsx);
    store(8, i, psy * lsy);
    store(9, i, psz * lsz);
}
