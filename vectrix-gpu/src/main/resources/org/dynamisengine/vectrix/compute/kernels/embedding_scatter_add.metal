#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
struct EmbeddingScatterParams_0
{
    uint dim_0;
    uint dOutOff_0;
    uint tableOff_0;
    uint planOff_0;
    uint unique_0;
    uint gridX_0;
};

struct KernelContext_0
{
    EmbeddingScatterParams_0 constant* p_0;
    uint device* plan_0;
    float device* dTable_0;
    float device* dOut_0;
};

[[kernel]] void embeddingScatterAdd(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], EmbeddingScatterParams_0 constant* p_1 [[buffer(3)]], uint device* plan_1 [[buffer(1)]], float device* dTable_1 [[buffer(2)]], float device* dOut_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->plan_0 = plan_1;
    (&kernelContext_0)->dTable_0 = dTable_1;
    (&kernelContext_0)->dOut_0 = dOut_1;
    uint e_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 256U + local_0.x;
    if(e_0 >= (p_1->unique_0 * p_1->dim_0))
    {
        return;
    }
    uint k_0 = e_0 / p_1->dim_0;
    uint d_0 = e_0 - k_0 * p_1->dim_0;
    uint device* _S1 = (&kernelContext_0)->plan_0+((&kernelContext_0)->p_0->planOff_0 + p_1->unique_0 + k_0);
    uint _S2 = *((&kernelContext_0)->plan_0+((&kernelContext_0)->p_0->planOff_0 + p_1->unique_0 + k_0 + 1U));
    uint _S3 = (&kernelContext_0)->p_0->planOff_0 + 2U * p_1->unique_0 + 1U;
    uint at_0 = (&kernelContext_0)->p_0->tableOff_0 + *((&kernelContext_0)->plan_0+((&kernelContext_0)->p_0->planOff_0 + k_0)) * p_1->dim_0 + d_0;
    float acc_0 = *((&kernelContext_0)->dTable_0+at_0);
    uint j_0 = *_S1;
    for(;;)
    {
        if(j_0 < _S2)
        {
        }
        else
        {
            break;
        }
        float _S4 = acc_0 + *((&kernelContext_0)->dOut_0+((&kernelContext_0)->p_0->dOutOff_0 + *((&kernelContext_0)->plan_0+(_S3 + j_0)) * p_1->dim_0 + d_0));
        uint _S5 = j_0 + 1U;
        acc_0 = _S4;
        j_0 = _S5;
    }
    *((&kernelContext_0)->dTable_0+at_0) = acc_0;
    return;
}

