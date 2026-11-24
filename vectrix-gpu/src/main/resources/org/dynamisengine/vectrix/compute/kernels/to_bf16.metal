#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
bool isNanBits_0(uint b_0)
{
    return (b_0 & 2147483647U) > 2139095040U;
}

uint toBf16Bits_0(uint b_1)
{
    if(isNanBits_0(b_1))
    {
        return ((b_1 >> 16U) | 64U) & 65535U;
    }
    return ((b_1 + 32767U + ((b_1 >> 16U) & 1U)) >> 16U) & 65535U;
}

struct ToBf16Params_0
{
    uint xOff_0;
    uint hOff_0;
    uint n_0;
    uint gridX_0;
};

struct KernelContext_0
{
    ToBf16Params_0 constant* p_0;
    uint device* h_0;
    uint device* x_0;
};

[[kernel]] void toBf16(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], ToBf16Params_0 constant* p_1 [[buffer(2)]], uint device* h_1 [[buffer(1)]], uint device* x_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->h_0 = h_1;
    (&kernelContext_0)->x_0 = x_1;
    uint t_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    uint first_0 = (p_1->hOff_0) >> 1U;
    if(t_0 > (((p_1->hOff_0 + p_1->n_0 - 1U) >> 1U) - first_0))
    {
        return;
    }
    uint w_0 = first_0 + t_0;
    uint lo_0 = w_0 * 2U;
    bool partial_0;
    if(lo_0 < (p_1->hOff_0))
    {
        partial_0 = true;
    }
    else
    {
        partial_0 = (lo_0 + 1U - p_1->hOff_0) >= (p_1->n_0);
    }
    uint word_0;
    if(partial_0)
    {
        word_0 = *((&kernelContext_0)->h_0+w_0);
    }
    else
    {
        word_0 = 0U;
    }
    uint k_0 = 0U;
    for(;;)
    {
        if(k_0 < 2U)
        {
        }
        else
        {
            break;
        }
        uint i_0 = lo_0 + k_0;
        if(i_0 >= (p_1->hOff_0))
        {
            partial_0 = (i_0 - p_1->hOff_0) < (p_1->n_0);
        }
        else
        {
            partial_0 = false;
        }
        if(partial_0)
        {
            uint shift_0 = k_0 * 16U;
            word_0 = (word_0 & (~(65535U << shift_0))) | ((toBf16Bits_0(*((&kernelContext_0)->x_0+((&kernelContext_0)->p_0->xOff_0 + (i_0 - p_1->hOff_0))))) << shift_0);
        }
        k_0 = k_0 + 1U;
    }
    *((&kernelContext_0)->h_0+w_0) = word_0;
    return;
}

