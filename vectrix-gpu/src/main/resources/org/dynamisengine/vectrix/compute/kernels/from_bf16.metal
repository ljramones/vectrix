#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
uint packedHalf_0(uint device* buf_0, uint i_0)
{
    return ((*(buf_0+(i_0 >> 1U))) >> ((i_0 & 1U) * 16U)) & 65535U;
}

struct FromBf16Params_0
{
    uint hOff_0;
    uint xOff_0;
    uint n_0;
    uint gridX_0;
};

struct KernelContext_0
{
    FromBf16Params_0 constant* p_0;
    uint device* x_0;
    uint device* h_0;
};

[[kernel]] void fromBf16(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], FromBf16Params_0 constant* p_1 [[buffer(2)]], uint device* x_1 [[buffer(1)]], uint device* h_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->x_0 = x_1;
    (&kernelContext_0)->h_0 = h_1;
    uint i_1 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_1 >= (p_1->n_0))
    {
        return;
    }
    uint device* _S1 = (&kernelContext_0)->x_0+((&kernelContext_0)->p_0->xOff_0 + i_1);
    uint _S2 = packedHalf_0((&kernelContext_0)->h_0, (&kernelContext_0)->p_0->hOff_0 + i_1);
    *_S1 = _S2 << 16U;
    return;
}

