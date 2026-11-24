#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
struct ComposeParams_0
{
    uint count_0;
    uint parentsOff_0;
    uint parentsStride_0;
    uint localsOff_0;
    uint localsStride_0;
    uint outOff_0;
    uint outStride_0;
    uint gridX_0;
};

struct KernelContext_0
{
    ComposeParams_0 constant* p_0;
    float device* parents_0;
    float device* locals_0;
    float device* outT_0;
};

float P_0(uint field_0, uint i_0, KernelContext_0 thread* kernelContext_0)
{
    return *(kernelContext_0->parents_0+(kernelContext_0->p_0->parentsOff_0 + field_0 * kernelContext_0->p_0->parentsStride_0 + i_0));
}

float L_0(uint field_1, uint i_1, KernelContext_0 thread* kernelContext_1)
{
    return *(kernelContext_1->locals_0+(kernelContext_1->p_0->localsOff_0 + field_1 * kernelContext_1->p_0->localsStride_0 + i_1));
}

void store_0(uint field_2, uint i_2, float v_0, KernelContext_0 thread* kernelContext_2)
{
    *(kernelContext_2->outT_0+(kernelContext_2->p_0->outOff_0 + field_2 * kernelContext_2->p_0->outStride_0 + i_2)) = v_0;
    return;
}

[[kernel]] void compose(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], ComposeParams_0 constant* p_1 [[buffer(3)]], float device* parents_1 [[buffer(0)]], float device* locals_1 [[buffer(1)]], float device* outT_1 [[buffer(2)]])
{
    thread KernelContext_0 kernelContext_3;
    (&kernelContext_3)->p_0 = p_1;
    (&kernelContext_3)->parents_0 = parents_1;
    (&kernelContext_3)->locals_0 = locals_1;
    (&kernelContext_3)->outT_0 = outT_1;
    uint i_3 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_3 >= (p_1->count_0))
    {
        return;
    }
    float _S1 = P_0(7U, i_3, &kernelContext_3);
    float _S2 = P_0(8U, i_3, &kernelContext_3);
    float _S3 = P_0(9U, i_3, &kernelContext_3);
    float _S4 = L_0(0U, i_3, &kernelContext_3);
    float ltx_0 = _S4 * _S1;
    float _S5 = L_0(1U, i_3, &kernelContext_3);
    float lty_0 = _S5 * _S2;
    float _S6 = L_0(2U, i_3, &kernelContext_3);
    float ltz_0 = _S6 * _S3;
    float _S7 = P_0(3U, i_3, &kernelContext_3);
    float _S8 = P_0(4U, i_3, &kernelContext_3);
    float _S9 = P_0(5U, i_3, &kernelContext_3);
    float _S10 = P_0(6U, i_3, &kernelContext_3);
    float tx2_0 = 2.0 * (_S8 * ltz_0 - _S9 * lty_0);
    float ty2_0 = 2.0 * (_S9 * ltx_0 - _S7 * ltz_0);
    float tz2_0 = 2.0 * (_S7 * lty_0 - _S8 * ltx_0);
    float rx_0 = ltx_0 + _S10 * tx2_0 + (_S8 * tz2_0 - _S9 * ty2_0);
    float ry_0 = lty_0 + _S10 * ty2_0 + (_S9 * tx2_0 - _S7 * tz2_0);
    float rz_0 = ltz_0 + _S10 * tz2_0 + (_S7 * ty2_0 - _S8 * tx2_0);
    float _S11 = L_0(3U, i_3, &kernelContext_3);
    float _S12 = L_0(4U, i_3, &kernelContext_3);
    float _S13 = L_0(5U, i_3, &kernelContext_3);
    float _S14 = L_0(6U, i_3, &kernelContext_3);
    float _S15 = L_0(7U, i_3, &kernelContext_3);
    float _S16 = L_0(8U, i_3, &kernelContext_3);
    float _S17 = L_0(9U, i_3, &kernelContext_3);
    float _S18 = P_0(0U, i_3, &kernelContext_3);
    float _S19 = P_0(1U, i_3, &kernelContext_3);
    float _S20 = P_0(2U, i_3, &kernelContext_3);
    store_0(0U, i_3, _S18 + rx_0, &kernelContext_3);
    store_0(1U, i_3, _S19 + ry_0, &kernelContext_3);
    store_0(2U, i_3, _S20 + rz_0, &kernelContext_3);
    store_0(3U, i_3, _S10 * _S11 + _S7 * _S14 + _S8 * _S13 - _S9 * _S12, &kernelContext_3);
    store_0(4U, i_3, _S10 * _S12 - _S7 * _S13 + _S8 * _S14 + _S9 * _S11, &kernelContext_3);
    store_0(5U, i_3, _S10 * _S13 + _S7 * _S12 - _S8 * _S11 + _S9 * _S14, &kernelContext_3);
    store_0(6U, i_3, _S10 * _S14 - _S7 * _S11 - _S8 * _S12 - _S9 * _S13, &kernelContext_3);
    store_0(7U, i_3, _S1 * _S15, &kernelContext_3);
    store_0(8U, i_3, _S2 * _S16, &kernelContext_3);
    store_0(9U, i_3, _S3 * _S17, &kernelContext_3);
    return;
}

