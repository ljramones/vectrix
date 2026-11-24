#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
float jabs_0(float x_0)
{
    return (as_type<float>(((as_type<uint>((x_0))) & 2147483647U)));
}

struct AabbParams_0
{
    uint count_0;
    uint entries_0;
    uint tOff_0;
    uint orderOff_0;
    uint inOff_0;
    uint inStride_0;
    uint outOff_0;
    uint outStride_0;
    uint gridX_0;
};

struct KernelContext_0
{
    AabbParams_0 constant* p_0;
    int device* order_0;
    float device* transforms_0;
    float device* inBox_0;
    float device* outBox_0;
};

[[kernel]] void transformAabb(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], AabbParams_0 constant* p_1 [[buffer(4)]], int device* order_1 [[buffer(1)]], float device* transforms_1 [[buffer(0)]], float device* inBox_1 [[buffer(2)]], float device* outBox_1 [[buffer(3)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->order_0 = order_1;
    (&kernelContext_0)->transforms_0 = transforms_1;
    (&kernelContext_0)->inBox_0 = inBox_1;
    (&kernelContext_0)->outBox_0 = outBox_1;
    uint i_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_0 >= (p_1->count_0))
    {
        return;
    }
    uint idx_0 = uint(*((&kernelContext_0)->order_0+((&kernelContext_0)->p_0->orderOff_0 + i_0)));
    if(idx_0 >= ((&kernelContext_0)->p_0->entries_0))
    {
        return;
    }
    uint o_0 = (&kernelContext_0)->p_0->tOff_0 + 12U * idx_0;
    float device* _S1 = (&kernelContext_0)->transforms_0+o_0;
    float device* _S2 = (&kernelContext_0)->transforms_0+(o_0 + 1U);
    float device* _S3 = (&kernelContext_0)->transforms_0+(o_0 + 2U);
    float device* _S4 = (&kernelContext_0)->transforms_0+(o_0 + 4U);
    float device* _S5 = (&kernelContext_0)->transforms_0+(o_0 + 5U);
    float device* _S6 = (&kernelContext_0)->transforms_0+(o_0 + 6U);
    float device* _S7 = (&kernelContext_0)->transforms_0+(o_0 + 8U);
    float device* _S8 = (&kernelContext_0)->transforms_0+(o_0 + 9U);
    float device* _S9 = (&kernelContext_0)->transforms_0+(o_0 + 10U);
    uint b_0 = (&kernelContext_0)->p_0->inOff_0 + idx_0;
    float device* _S10 = (&kernelContext_0)->inBox_0+b_0;
    float device* _S11 = (&kernelContext_0)->inBox_0+(b_0 + (&kernelContext_0)->p_0->inStride_0);
    float device* _S12 = (&kernelContext_0)->inBox_0+(b_0 + 2U * (&kernelContext_0)->p_0->inStride_0);
    float device* _S13 = (&kernelContext_0)->inBox_0+(b_0 + 3U * (&kernelContext_0)->p_0->inStride_0);
    float device* _S14 = (&kernelContext_0)->inBox_0+(b_0 + 4U * (&kernelContext_0)->p_0->inStride_0);
    float device* _S15 = (&kernelContext_0)->inBox_0+(b_0 + 5U * (&kernelContext_0)->p_0->inStride_0);
    float cx_0 = (*_S10 + *_S13) * 0.5;
    float cy_0 = (*_S11 + *_S14) * 0.5;
    float cz_0 = (*_S12 + *_S15) * 0.5;
    float ex_0 = (*_S13 - *_S10) * 0.5;
    float ey_0 = (*_S14 - *_S11) * 0.5;
    float ez_0 = (*_S15 - *_S12) * 0.5;
    float ncx_0 = *_S1 * cx_0 + *_S2 * cy_0 + *_S3 * cz_0 + *((&kernelContext_0)->transforms_0+(o_0 + 3U));
    float ncy_0 = *_S4 * cx_0 + *_S5 * cy_0 + *_S6 * cz_0 + *((&kernelContext_0)->transforms_0+(o_0 + 7U));
    float ncz_0 = *_S7 * cx_0 + *_S8 * cy_0 + *_S9 * cz_0 + *((&kernelContext_0)->transforms_0+(o_0 + 11U));
    float nex_0 = jabs_0(*_S1) * ex_0 + jabs_0(*_S2) * ey_0 + jabs_0(*_S3) * ez_0;
    float ney_0 = jabs_0(*_S4) * ex_0 + jabs_0(*_S5) * ey_0 + jabs_0(*_S6) * ez_0;
    float nez_0 = jabs_0(*_S7) * ex_0 + jabs_0(*_S8) * ey_0 + jabs_0(*_S9) * ez_0;
    uint d_0 = (&kernelContext_0)->p_0->outOff_0 + idx_0;
    uint t_0 = (&kernelContext_0)->p_0->outStride_0;
    *((&kernelContext_0)->outBox_0+d_0) = ncx_0 - nex_0;
    *((&kernelContext_0)->outBox_0+(d_0 + t_0)) = ncy_0 - ney_0;
    *((&kernelContext_0)->outBox_0+(d_0 + 2U * t_0)) = ncz_0 - nez_0;
    *((&kernelContext_0)->outBox_0+(d_0 + 3U * t_0)) = ncx_0 + nex_0;
    *((&kernelContext_0)->outBox_0+(d_0 + 4U * t_0)) = ncy_0 + ney_0;
    *((&kernelContext_0)->outBox_0+(d_0 + 5U * t_0)) = ncz_0 + nez_0;
    return;
}

