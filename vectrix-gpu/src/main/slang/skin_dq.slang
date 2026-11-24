// SkinningKernels.skinDualQuat4 and skinDualQuat4SoA: dual quaternion linear blending, four influences per vertex.
// Java twin: org.dynamisengine.vectrix.soa.SkinningKernels (skinDualQuat4Single, signForReference).
//
// Buffers: joints is a DualQuatSoA buffer (planes rx, ry, rz, rw, dx, dy, dz, dw, see engine.slang); positions are
// planes x, y, z; influences are addressed as in skin_lbs.slang. A vertex with a joint index outside
// [0, jointCount) is not written. One thread per vertex.
import common;
import engine;

struct SkinParams
{
    uint count;
    uint jointCount;
    uint jointsOff;
    uint jointStride;
    uint idxOff;
    uint idxSlot;
    uint idxStep;
    uint wOff;
    uint wSlot;
    uint wStep;
    uint inOff;
    uint inStride;
    uint outOff;
    uint outStride;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> joints;
[[vk::binding(1, 0)]] RWStructuredBuffer<int> indices;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> weights;
[[vk::binding(3, 0)]] RWStructuredBuffer<float> inPos;
[[vk::binding(4, 0)]] RWStructuredBuffer<float> outPos;
[[vk::push_constant]] ConstantBuffer<SkinParams> p;

float J(uint field, uint j)
{
    return joints[p.jointsOff + field * p.jointStride + j];
}

// SkinningKernels.signForReference: -1 when joint j's rotation is in the other hemisphere from the reference.
float signForReference(float refX, float refY, float refZ, float refW, uint j)
{
    float rx = J(0, j);
    float ry = J(1, j);
    float rz = J(2, j);
    float rw = J(3, j);
    float dot = refX * rx + refY * ry + refZ * rz + refW * rw;
    return dot < 0.0f ? -1.0f : 1.0f;
}

// One blended component: c[j0]·sw0 + c[j1]·sw1 + c[j2]·sw2 + c[j3]·sw3, left to right.
float blend(uint field, uint j0, uint j1, uint j2, uint j3, float sw0, float sw1, float sw2, float sw3)
{
    return J(field, j0) * sw0 + J(field, j1) * sw1 + J(field, j2) * sw2 + J(field, j3) * sw3;
}

[shader("compute")]
[numthreads(64, 1, 1)]
void skinDualQuat(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint i = (group.y * p.gridX + group.x) * 64 + local.x;
    if (i >= p.count)
    {
        return;
    }
    uint j0 = uint(indices[p.idxOff + i * p.idxStep]);
    uint j1 = uint(indices[p.idxOff + p.idxSlot + i * p.idxStep]);
    uint j2 = uint(indices[p.idxOff + 2 * p.idxSlot + i * p.idxStep]);
    uint j3 = uint(indices[p.idxOff + 3 * p.idxSlot + i * p.idxStep]);
    if (j0 >= p.jointCount || j1 >= p.jointCount || j2 >= p.jointCount || j3 >= p.jointCount)
    {
        return;
    }
    float w0 = weights[p.wOff + i * p.wStep];
    float w1 = weights[p.wOff + p.wSlot + i * p.wStep];
    float w2 = weights[p.wOff + 2 * p.wSlot + i * p.wStep];
    float w3 = weights[p.wOff + 3 * p.wSlot + i * p.wStep];
    float x = inPos[p.inOff + i];
    float y = inPos[p.inOff + p.inStride + i];
    float z = inPos[p.inOff + 2 * p.inStride + i];

    float refX = J(0, j0);
    float refY = J(1, j0);
    float refZ = J(2, j0);
    float refW = J(3, j0);

    float s0 = signForReference(refX, refY, refZ, refW, j0);
    float s1 = signForReference(refX, refY, refZ, refW, j1);
    float s2 = signForReference(refX, refY, refZ, refW, j2);
    float s3 = signForReference(refX, refY, refZ, refW, j3);

    float sw0 = s0 * w0;
    float sw1 = s1 * w1;
    float sw2 = s2 * w2;
    float sw3 = s3 * w3;

    float arx = blend(0, j0, j1, j2, j3, sw0, sw1, sw2, sw3);
    float ary = blend(1, j0, j1, j2, j3, sw0, sw1, sw2, sw3);
    float arz = blend(2, j0, j1, j2, j3, sw0, sw1, sw2, sw3);
    float arw = blend(3, j0, j1, j2, j3, sw0, sw1, sw2, sw3);

    float adx = blend(4, j0, j1, j2, j3, sw0, sw1, sw2, sw3);
    float ady = blend(5, j0, j1, j2, j3, sw0, sw1, sw2, sw3);
    float adz = blend(6, j0, j1, j2, j3, sw0, sw1, sw2, sw3);
    float adw = blend(7, j0, j1, j2, j3, sw0, sw1, sw2, sw3);

    float norm2 = arx * arx + ary * ary + arz * arz + arw * arw;
    if (norm2 == 0.0f)
    {
        outPos[p.outOff + i] = x;
        outPos[p.outOff + p.outStride + i] = y;
        outPos[p.outOff + 2 * p.outStride + i] = z;
        return;
    }
    float invNorm = vectrixInvsqrt(norm2);
    arx *= invNorm;
    ary *= invNorm;
    arz *= invNorm;
    arw *= invNorm;
    adx *= invNorm;
    ady *= invNorm;
    adz *= invNorm;
    adw *= invNorm;

    float tx2 = 2.0f * (ary * z - arz * y);
    float ty2 = 2.0f * (arz * x - arx * z);
    float tz2 = 2.0f * (arx * y - ary * x);
    float rx = x + arw * tx2 + (ary * tz2 - arz * ty2);
    float ry = y + arw * ty2 + (arz * tx2 - arx * tz2);
    float rz = z + arw * tz2 + (arx * ty2 - ary * tx2);

    float tx = 2.0f * (-adw * arx + adx * arw - ady * arz + adz * ary);
    float ty = 2.0f * (-adw * ary + adx * arz + ady * arw - adz * arx);
    float tz = 2.0f * (-adw * arz - adx * ary + ady * arx + adz * arw);

    outPos[p.outOff + i] = rx + tx;
    outPos[p.outOff + p.outStride + i] = ry + ty;
    outPos[p.outOff + 2 * p.outStride + i] = rz + tz;
}
