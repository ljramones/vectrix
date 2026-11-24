#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
uint packedByte_0(uint device* buf_0, uint i_0)
{
    return ((*(buf_0+(i_0 >> 2U))) >> ((i_0 & 3U) * 8U)) & 255U;
}

float fp8Positive_0(uint k_0, uint e5m2_0)
{
    if(e5m2_0 != 0U)
    {
        uint e_0 = k_0 >> 2U;
        uint m_0 = k_0 & 3U;
        if(e_0 == 0U)
        {
            return float(m_0) * (as_type<float>((931135488U)));
        }
        return (as_type<float>((((e_0 + 112U) << 23U) | (m_0 << 21U))));
    }
    uint e_1 = k_0 >> 3U;
    uint m_1 = k_0 & 7U;
    if(e_1 == 0U)
    {
        return float(m_1) * (as_type<float>((989855744U)));
    }
    return (as_type<float>((((e_1 + 120U) << 23U) | (m_1 << 20U))));
}

uint fp8ToFloatBits_0(uint c_0, uint e5m2_1)
{
    uint mag_0 = c_0 & 127U;
    uint sign_0 = (c_0 & 128U) << 24U;
    if(e5m2_1 != 0U)
    {
        if(mag_0 == 124U)
        {
            return sign_0 | 2139095040U;
        }
        if(mag_0 > 124U)
        {
            return 2143289344U;
        }
    }
    else
    {
        if(mag_0 == 127U)
        {
            return 2143289344U;
        }
    }
    return sign_0 | (as_type<uint>((fp8Positive_0(mag_0, e5m2_1))));
}

struct FromFp8Params_0
{
    uint cOff_0;
    uint xOff_0;
    uint n_0;
    uint e5m2_2;
    uint gridX_0;
};

struct KernelContext_0
{
    FromFp8Params_0 constant* p_0;
    uint device* x_0;
    uint device* codes_0;
};

[[kernel]] void fromFp8(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], FromFp8Params_0 constant* p_1 [[buffer(2)]], uint device* x_1 [[buffer(1)]], uint device* codes_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->x_0 = x_1;
    (&kernelContext_0)->codes_0 = codes_1;
    uint i_1 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_1 >= (p_1->n_0))
    {
        return;
    }
    uint device* _S1 = (&kernelContext_0)->x_0+((&kernelContext_0)->p_0->xOff_0 + i_1);
    uint _S2 = packedByte_0((&kernelContext_0)->codes_0, (&kernelContext_0)->p_0->cOff_0 + i_1);
    *_S1 = fp8ToFloatBits_0(_S2, (&kernelContext_0)->p_0->e5m2_2);
    return;
}

