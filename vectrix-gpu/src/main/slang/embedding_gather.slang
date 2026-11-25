// nn.Embedding.gather: out[outOff + t·dim + d] = table[tableOff + idx[idxOff + t]·dim + d], one thread per element.
// An index outside [0, rows) (the CPU throws) gives a NaN output row. Java twin: org.dynamisengine.vectrix.nn.Embedding.

struct EmbeddingGatherParams
{
    uint rows;
    uint dim;
    uint tableOff;
    uint idxOff;
    uint outOff;
    uint count;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> table;
[[vk::binding(1, 0)]] RWStructuredBuffer<uint> idx;
[[vk::binding(2, 0)]] RWStructuredBuffer<float> out;
[[vk::push_constant]] ConstantBuffer<EmbeddingGatherParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void embeddingGather(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint e = (group.y * p.gridX + group.x) * 256 + local.x;
    if (e >= p.count * p.dim)
    {
        return;
    }
    uint t = e / p.dim;
    uint d = e - t * p.dim;
    uint r = idx[p.idxOff + t];  // a negative int reads as a large uint
    out[p.outOff + e] = r < p.rows ? table[p.tableOff + r * p.dim + d] : asfloat(0x7FC00000u);
}
