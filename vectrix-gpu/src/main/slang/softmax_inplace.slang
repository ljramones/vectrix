// Softmax over rows in place: each row of x is replaced by its softmax. See softmax_row.slang.
import softmax_row;

struct SoftmaxInPlaceParams
{
    uint rows;
    uint n;
    uint xOff;
    uint ldx;
    uint gridX;
};

[[vk::binding(0, 0)]] RWStructuredBuffer<float> x;
[[vk::push_constant]] ConstantBuffer<SoftmaxInPlaceParams> p;

[shader("compute")]
[numthreads(256, 1, 1)]
void softmaxInPlace(uint3 group : SV_GroupID, uint3 local : SV_GroupThreadID)
{
    uint row = group.y * p.gridX + group.x;
    if (row >= p.rows)
    {
        return;
    }
    uint base = p.xOff + row * p.ldx;
    softmaxRow(x, base, x, base, p.n, local.x);
}
