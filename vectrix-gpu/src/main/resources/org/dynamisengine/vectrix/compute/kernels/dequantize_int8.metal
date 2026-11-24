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

int packedInt8_0(uint device* buf_1, uint i_1)
{
    uint _S1 = packedByte_0(buf_1, i_1);
    return int(_S1 << 24U) >> 24U;
}

uint int8Dequantize_0(int q_0, uint sb_0)
{
    uint mag_0 = sb_0 & 2147483647U;
    bool _S2;
    if(mag_0 == 0U)
    {
        _S2 = true;
    }
    else
    {
        _S2 = mag_0 >= 8388608U;
    }
    if(_S2)
    {
        return (as_type<uint>((float(q_0) * (as_type<float>((sb_0))))));
    }
    bool _S3 = q_0 < int(0);
    uint _S4;
    if(_S3)
    {
        _S4 = 2147483648U;
    }
    else
    {
        _S4 = 0U;
    }
    uint sign_0 = (sb_0 ^ _S4) & 2147483648U;
    int _S5;
    if(_S3)
    {
        _S5 = - q_0;
    }
    else
    {
        _S5 = q_0;
    }
    uint prod_0 = uint(_S5) * mag_0;
    if(prod_0 < 16777216U)
    {
        return sign_0 | prod_0;
    }
    return sign_0 | ((as_type<uint>((float(prod_0)))) - 1249902592U);
}

struct DequantizeInt8Params_0
{
    uint qOff_0;
    uint sOff_0;
    uint xOff_0;
    uint n_0;
    uint gridX_0;
};

struct KernelContext_0
{
    DequantizeInt8Params_0 constant* p_0;
    uint device* x_0;
    uint device* q_1;
    uint device* scales_0;
};

[[kernel]] void dequantizeInt8(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], DequantizeInt8Params_0 constant* p_1 [[buffer(3)]], uint device* x_1 [[buffer(2)]], uint device* q_2 [[buffer(0)]], uint device* scales_1 [[buffer(1)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->x_0 = x_1;
    (&kernelContext_0)->q_1 = q_2;
    (&kernelContext_0)->scales_0 = scales_1;
    uint i_2 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_2 >= (p_1->n_0))
    {
        return;
    }
    uint device* _S6 = (&kernelContext_0)->x_0+((&kernelContext_0)->p_0->xOff_0 + i_2);
    int _S7 = packedInt8_0((&kernelContext_0)->q_1, (&kernelContext_0)->p_0->qOff_0 + i_2);
    *_S6 = int8Dequantize_0(_S7, *((&kernelContext_0)->scales_0+((&kernelContext_0)->p_0->sOff_0 + i_2 / 32U)));
    return;
}

