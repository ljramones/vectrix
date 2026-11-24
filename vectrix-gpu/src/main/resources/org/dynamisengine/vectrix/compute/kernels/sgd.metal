#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
struct SgdParams_0
{
    uint n_0;
    uint pOff_0;
    uint gOff_0;
    uint bOff_0;
    float negLr_0;
    float momentum_0;
    float weightDecay_0;
    uint nesterov_0;
    uint gridX_0;
};

struct KernelContext_0
{
    SgdParams_0 constant* p_0;
    float device* param_0;
    float device* grad_0;
    float device* buf_0;
};

[[kernel]] void sgd(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], SgdParams_0 constant* p_1 [[buffer(3)]], float device* param_1 [[buffer(0)]], float device* grad_1 [[buffer(1)]], float device* buf_1 [[buffer(2)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->param_0 = param_1;
    (&kernelContext_0)->grad_0 = grad_1;
    (&kernelContext_0)->buf_0 = buf_1;
    uint i_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 256U + local_0.x;
    if(i_0 >= (p_1->n_0))
    {
        return;
    }
    float device* _S1 = (&kernelContext_0)->param_0+((&kernelContext_0)->p_0->pOff_0 + i_0);
    float pi_0 = *_S1;
    float d_0 = fma((&kernelContext_0)->p_0->weightDecay_0, *_S1, *((&kernelContext_0)->grad_0+((&kernelContext_0)->p_0->gOff_0 + i_0)));
    float b_0 = fma((&kernelContext_0)->p_0->momentum_0, *((&kernelContext_0)->buf_0+((&kernelContext_0)->p_0->bOff_0 + i_0)), d_0);
    *((&kernelContext_0)->buf_0+((&kernelContext_0)->p_0->bOff_0 + i_0)) = b_0;
    float step_0;
    if(((&kernelContext_0)->p_0->nesterov_0) != 0U)
    {
        step_0 = fma((&kernelContext_0)->p_0->momentum_0, b_0, d_0);
    }
    else
    {
        step_0 = b_0;
    }
    *((&kernelContext_0)->param_0+((&kernelContext_0)->p_0->pOff_0 + i_0)) = fma((&kernelContext_0)->p_0->negLr_0, step_0, pi_0);
    return;
}

