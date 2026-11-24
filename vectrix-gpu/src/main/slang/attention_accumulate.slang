// The key and value gradients of the attention backward pass, accumulated as Attention.backward and
// groupedQueryBackward do with Blasf.axpy: for key j, key/value head g and column i below width,
//   dest[j][g·width + i] = fma(c, x[row][i], dest[j][g·width + i])
// for each attention row of a query head that reads head g and attends to key j, in row order (queries in order,
// heads in order within each), with c = scale·w[row][j] or c = w[row][j]. Java twin:
// org.dynamisengine.vectrix.nn.Attention.
//
//   dK: w = ds, scaled, x = q:    Blasf.axpy(scale * ds_j, q, dK_j)
//   dV: w = p, not scaled, x = dOut: Blasf.axpy(p_j, dOut, dV_j)
// One thread per destination element runs the whole chain, so the additions happen in the CPU's order. Groups of
// 64 cover `chunks` 64-column chunks of each (key, head).
import common;
import attention_common;

struct AccumulateParams
{
    uint heads;
    uint group;
    uint kvHeads;
    uint queries;
    uint t;
    uint causal;
    uint width;
    uint scaled;
    float scale;
    uint wOff;
    uint ldw;
    uint xOff;
    uint ldx;
    uint destOff;
    uint ldDest;
    uint chunks;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> w;
[[vk::binding(1, 0)]] RWStructuredBuffer<float> x;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> dest;
[[vk::push_constant]] ConstantBuffer<AccumulateParams> p;

static const uint CHUNK = 64;

[shader("compute")]
[numthreads(64, 1, 1)]
void attentionAccumulate(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint gi = group.y * p.gridX + group.x;
    uint perKey = p.kvHeads * p.chunks;
    uint j = gi / perKey;
    if (j >= p.t)
    {
        return;
    }
    uint g = (gi % perKey) / p.chunks;
    uint i = (gi % p.chunks) * CHUNK + local.x;
    if (i >= p.width)
    {
        return;
    }
    uint di = p.destOff + j * p.ldDest + g * p.width + i;
    float acc = dest[di];
    for (uint r = 0; r < p.queries; r++)
    {
        if (j < keysFor(r, p.queries, p.t, p.causal))
        {
            for (uint hh = 0; hh < p.group; hh++)
            {
                uint row = r * p.heads + g * p.group + hh;
                float wv = w[p.wOff + row * p.ldw + j];
                float c = p.scaled != 0 ? p.scale * wv : wv;
                acc = fma(c, x[p.xOff + row * p.ldx + i], acc);
            }
        }
    }
    dest[di] = acc;
}
