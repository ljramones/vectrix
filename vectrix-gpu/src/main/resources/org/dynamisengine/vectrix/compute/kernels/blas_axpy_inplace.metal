#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
struct AxpyInPlaceParams_0
{
    float a_0;
    uint n_0;
    uint xOff_0;
    uint gridX_0;
};

struct KernelContext_0
{
    AxpyInPlaceParams_0 constant* p_0;
    float device* x_0;
};

[[kernel]] void axpyInPlace(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], AxpyInPlaceParams_0 constant* p_1 [[buffer(1)]], float device* x_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->x_0 = x_1;
    uint i_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 256U + local_0.x;
    if(i_0 >= (p_1->n_0))
    {
        return;
    }
    float device* _S1 = (&kernelContext_0)->x_0+((&kernelContext_0)->p_0->xOff_0 + i_0);
    *((&kernelContext_0)->x_0+((&kernelContext_0)->p_0->xOff_0 + i_0)) = fma((&kernelContext_0)->p_0->a_0, *_S1, *_S1);
    return;
}

