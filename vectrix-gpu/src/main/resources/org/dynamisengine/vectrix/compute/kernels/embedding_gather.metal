#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
struct EmbeddingGatherParams_0
{
    uint rows_0;
    uint dim_0;
    uint tableOff_0;
    uint idxOff_0;
    uint outOff_0;
    uint count_0;
    uint gridX_0;
};

struct KernelContext_0
{
    EmbeddingGatherParams_0 constant* p_0;
    uint device* idx_0;
    float device* out_0;
    float device* table_0;
};

[[kernel]] void embeddingGather(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], EmbeddingGatherParams_0 constant* p_1 [[buffer(3)]], uint device* idx_1 [[buffer(1)]], float device* out_1 [[buffer(2)]], float device* table_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->idx_0 = idx_1;
    (&kernelContext_0)->out_0 = out_1;
    (&kernelContext_0)->table_0 = table_1;
    uint e_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 256U + local_0.x;
    if(e_0 >= (p_1->count_0 * p_1->dim_0))
    {
        return;
    }
    uint t_0 = e_0 / p_1->dim_0;
    uint d_0 = e_0 - t_0 * p_1->dim_0;
    uint device* _S1 = (&kernelContext_0)->idx_0+((&kernelContext_0)->p_0->idxOff_0 + t_0);
    uint r_0 = *_S1;
    float device* _S2 = (&kernelContext_0)->out_0+((&kernelContext_0)->p_0->outOff_0 + e_0);
    float _S3;
    if((*_S1) < ((&kernelContext_0)->p_0->rows_0))
    {
        _S3 = *((&kernelContext_0)->table_0+((&kernelContext_0)->p_0->tableOff_0 + r_0 * p_1->dim_0 + d_0));
    }
    else
    {
        _S3 = (as_type<float>((2143289344U)));
    }
    *_S2 = _S3;
    return;
}

