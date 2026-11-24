#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
struct AdamWParams_0
{
    uint n_0;
    uint pOff_0;
    uint gOff_0;
    uint mOff_0;
    uint vOff_0;
    float negLr_0;
    float beta1_0;
    float beta2_0;
    float eps_0;
    float bc1_0;
    float bc2_0;
    float a1_0;
    float a2_0;
    float decay_0;
    uint gridX_0;
};

struct KernelContext_0
{
    AdamWParams_0 constant* p_0;
    float device* grad_0;
    float device* m_0;
    float device* v_0;
    float device* param_0;
};

[[kernel]] void adamw(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], AdamWParams_0 constant* p_1 [[buffer(4)]], float device* grad_1 [[buffer(1)]], float device* m_1 [[buffer(2)]], float device* v_1 [[buffer(3)]], float device* param_1 [[buffer(0)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->grad_0 = grad_1;
    (&kernelContext_0)->m_0 = m_1;
    (&kernelContext_0)->v_0 = v_1;
    (&kernelContext_0)->param_0 = param_1;
    uint i_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 256U + local_0.x;
    if(i_0 >= (p_1->n_0))
    {
        return;
    }
    float device* _S1 = (&kernelContext_0)->grad_0+((&kernelContext_0)->p_0->gOff_0 + i_0);
    float mi_0 = fma((&kernelContext_0)->p_0->beta1_0, *((&kernelContext_0)->m_0+((&kernelContext_0)->p_0->mOff_0 + i_0)), (&kernelContext_0)->p_0->a1_0 * *_S1);
    float vi_0 = fma((&kernelContext_0)->p_0->beta2_0, *((&kernelContext_0)->v_0+((&kernelContext_0)->p_0->vOff_0 + i_0)), (&kernelContext_0)->p_0->a2_0 * *_S1 * *_S1);
    *((&kernelContext_0)->m_0+((&kernelContext_0)->p_0->mOff_0 + i_0)) = mi_0;
    *((&kernelContext_0)->v_0+((&kernelContext_0)->p_0->vOff_0 + i_0)) = vi_0;
    *((&kernelContext_0)->param_0+((&kernelContext_0)->p_0->pOff_0 + i_0)) = fma((&kernelContext_0)->p_0->negLr_0, mi_0 / (&kernelContext_0)->p_0->bc1_0 / (sqrt(vi_0 / (&kernelContext_0)->p_0->bc2_0) + (&kernelContext_0)->p_0->eps_0), *((&kernelContext_0)->param_0+((&kernelContext_0)->p_0->pOff_0 + i_0)) * (&kernelContext_0)->p_0->decay_0);
    return;
}

