// The weighted sum of an attention row, a transposed Gemmf.gemv with beta 0: for each attention row and column i
// below width,
//   out[row][i] = alpha·Blasf.dot(column i of m (key/value head g), w[row], keysFor(query))
// where the dot runs over the keys j: fma(m[j][i], w[row][j], acc) in Blasf's order (attention_common.slang).
// Java twin: org.dynamisengine.vectrix.nn.Attention.
//
//   forward:  m = the values, w = the probabilities, alpha = 1: out = Vᵀ·p.
//   backward: m = the keys, w = the score gradients ds, alpha = scale: dq = scale·Kᵀ·ds.
// One thread per output element; groups of 64 cover `chunks` 64-column chunks of each row.
import common;
import attention_common;

struct MixParams
{
    uint rows;
    uint heads;
    uint group;
    uint queries;
    uint t;
    uint causal;
    uint width;
    float alpha;
    uint mOff;
    uint ldm;
    uint wOff;
    uint ldw;
    uint outOff;
    uint ldOut;
    uint chunks;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> m;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> w;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> out;
[[vk::push_constant]] ConstantBuffer<MixParams> p;

static const uint CHUNK = 64;

struct ColumnTerms : IDotTerms
{
    uint mBase;
    uint wBase;

    float left(uint e)
    {
        return m[mBase + e * p.ldm];
    }

    float right(uint e)
    {
        return w[wBase + e];
    }
};

[shader("compute")]
[numthreads(64, 1, 1)]
void attentionMix(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint gi = group.y * p.gridX + group.x;
    uint row = gi / p.chunks;
    if (row >= p.rows)
    {
        return;
    }
    uint i = (gi % p.chunks) * CHUNK + local.x;
    if (i >= p.width)
    {
        return;
    }
    uint r = row / p.heads;
    uint g = (row % p.heads) / p.group;
    ColumnTerms terms;
    terms.mBase = p.mOff + g * p.width + i;
    terms.wBase = p.wOff + row * p.ldw;
    float s = serialDot(terms, keysFor(r, p.queries, p.t, p.causal));
    out[p.outOff + row * p.ldOut + i] = p.alpha * s; // Gemmf.finish with beta == 0
}
