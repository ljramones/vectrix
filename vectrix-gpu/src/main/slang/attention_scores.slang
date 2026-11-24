// Attention scores: for each attention row and key j below keysFor(query),
//   out[row][j] = scale·Blasf.dot(x[row], m[j] of key/value head g, width) + mask[query][j]
// with the scale and the mask optional. Java twin: org.dynamisengine.vectrix.nn.Attention.
//
//   forward:  x = q, m = the keys, scaled and masked: `scale * Blasf.dot(q, K_j, d)`, then `s + mask[j]`.
//   backward: x = dOut, m = the values, neither: `Blasf.dot(dOut, V_j, dv)`.
// One thread per score computes the whole dot product in Blasf's order (attention_common.slang). Groups of 64 cover
// `chunks` 64-key chunks of each row.
import common;
import attention_common;

struct ScoresParams
{
    uint rows;
    uint heads;
    uint group;
    uint queries;
    uint t;
    uint causal;
    uint width;
    uint scaled;
    float scale;
    uint xOff;
    uint ldx;
    uint mOff;
    uint ldm;
    uint masked;
    uint maskOff;
    uint ldMask;
    uint outOff;
    uint ldOut;
    uint chunks;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> m;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> mask;
[[vk::binding(3, 0)]] RWStructuredBuffer<float> out;
[[vk::push_constant]] ConstantBuffer<ScoresParams> p;

static const uint CHUNK = 64;

struct RowTerms : IDotTerms
{
    uint xBase;
    uint mBase;

    float left(uint e)
    {
        return x[xBase + e];
    }

    float right(uint e)
    {
        return m[mBase + e];
    }
};

[shader("compute")]
[numthreads(64, 1, 1)]
void attentionScores(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint gi = group.y * p.gridX + group.x;
    uint row = gi / p.chunks;
    if (row >= p.rows)
    {
        return;
    }
    uint j = (gi % p.chunks) * CHUNK + local.x;
    uint r = row / p.heads;
    uint g = (row % p.heads) / p.group;
    if (j >= keysFor(r, p.queries, p.t, p.causal))
    {
        return;
    }
    RowTerms terms;
    terms.xBase = p.xOff + row * p.ldx;
    terms.mBase = p.mOff + j * p.ldm + g * p.width;
    float d = serialDot(terms, p.width);
    float s = p.scaled != 0 ? p.scale * d : d;
    out[p.outOff + row * p.ldOut + j] = p.masked != 0 ? s + mask[p.maskOff + r * p.ldMask + j] : s;
}
