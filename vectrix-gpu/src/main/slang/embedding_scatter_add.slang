// nn.Embedding.scatterAdd: dTable[row][d] += dOut[t][d] for the tokens t of each row, in increasing t, so each row's
// sum has the CPU's order and no two threads write one element (no atomics). One thread per (row of the plan, d).
// The plan (GpuEmbedding.Plan, built on the host from the indices) is rowIds[u], starts[u + 1], tokens[count], from
// planOff: the tokens of rowIds[k] are tokens[starts[k] .. starts[k + 1]), ascending.
// Java twin: org.dynamisengine.vectrix.nn.Embedding.

struct EmbeddingScatterParams
{
    uint dim;
    uint dOutOff;
    uint tableOff;
    uint planOff;
    uint unique;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> dOut;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint> plan;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> dTable;
[[vk::push_constant]] ConstantBuffer<EmbeddingScatterParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void embeddingScatterAdd(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint e = (group.y * p.gridX + group.x) * 256 + local.x;
    if (e >= p.unique * p.dim)
    {
        return;
    }
    uint k = e / p.dim;
    uint d = e - k * p.dim;
    uint row = plan[p.planOff + k];
    uint first = plan[p.planOff + p.unique + k];
    uint end = plan[p.planOff + p.unique + k + 1];
    uint tokens = p.planOff + 2 * p.unique + 1;
    uint at = p.tableOff + row * p.dim + d;
    float acc = dTable[at];
    for (uint j = first; j < end; j++)
    {
        acc = acc + dOut[p.dOutOff + plan[tokens + j] * p.dim + d];
    }
    dTable[at] = acc;
}
