#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
struct NormParamGradsParams_0
{
    uint rows_0;
    uint n_0;
    uint xOff_0;
    uint ldx_0;
    uint statsOff_0;
    uint dyOff_0;
    uint lddy_0;
    uint dgOff_0;
    uint dbOff_0;
    uint flags_0;
    uint gridX_0;
};

struct KernelContext_0
{
    NormParamGradsParams_0 constant* p_0;
    float device* dGamma_0;
    float device* dBeta_0;
    float device* x_0;
    float device* dy_0;
    float device* stats_0;
};

[[kernel]] void normParamGrads(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], NormParamGradsParams_0 constant* p_1 [[buffer(5)]], float device* dGamma_1 [[buffer(3)]], float device* dBeta_1 [[buffer(4)]], float device* x_1 [[buffer(0)]], float device* dy_1 [[buffer(2)]], float device* stats_1 [[buffer(1)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->dGamma_0 = dGamma_1;
    (&kernelContext_0)->dBeta_0 = dBeta_1;
    (&kernelContext_0)->x_0 = x_1;
    (&kernelContext_0)->dy_0 = dy_1;
    (&kernelContext_0)->stats_0 = stats_1;
    uint i_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_0 >= (p_1->n_0))
    {
        return;
    }
    bool hasDGamma_0 = (((&kernelContext_0)->p_0->flags_0) & 1U) != 0U;
    bool hasDBeta_0 = (((&kernelContext_0)->p_0->flags_0) & 2U) != 0U;
    bool _S1 = (((&kernelContext_0)->p_0->flags_0) & 4U) != 0U;
    uint r_0;
    if(hasDGamma_0)
    {
        r_0 = (&kernelContext_0)->p_0->dgOff_0 + i_0;
    }
    else
    {
        r_0 = 0U;
    }
    float _S2 = *((&kernelContext_0)->dGamma_0+r_0);
    if(hasDBeta_0)
    {
        r_0 = (&kernelContext_0)->p_0->dbOff_0 + i_0;
    }
    else
    {
        r_0 = 0U;
    }
    float device* _S3 = (&kernelContext_0)->dBeta_0+r_0;
    float dg_0 = _S2;
    float db_0 = *_S3;
    r_0 = 0U;
    for(;;)
    {
        if(r_0 < ((&kernelContext_0)->p_0->rows_0))
        {
        }
        else
        {
            break;
        }
        float xv_0 = *((&kernelContext_0)->x_0+((&kernelContext_0)->p_0->xOff_0 + r_0 * (&kernelContext_0)->p_0->ldx_0 + i_0));
        float d_0 = *((&kernelContext_0)->dy_0+((&kernelContext_0)->p_0->dyOff_0 + r_0 * (&kernelContext_0)->p_0->lddy_0 + i_0));
        float h_0;
        if(_S1)
        {
            h_0 = xv_0 * *((&kernelContext_0)->stats_0+((&kernelContext_0)->p_0->statsOff_0 + r_0));
        }
        else
        {
            uint _S4 = 2U * r_0;
            h_0 = (xv_0 - *((&kernelContext_0)->stats_0+((&kernelContext_0)->p_0->statsOff_0 + _S4))) * *((&kernelContext_0)->stats_0+((&kernelContext_0)->p_0->statsOff_0 + _S4 + 1U));
        }
        float _S5 = fma(d_0, h_0, dg_0);
        float _S6 = db_0 + d_0;
        uint _S7 = r_0 + 1U;
        dg_0 = _S5;
        db_0 = _S6;
        r_0 = _S7;
    }
    if(hasDGamma_0)
    {
        *((&kernelContext_0)->dGamma_0+((&kernelContext_0)->p_0->dgOff_0 + i_0)) = dg_0;
    }
    if(hasDBeta_0)
    {
        *((&kernelContext_0)->dBeta_0+((&kernelContext_0)->p_0->dbOff_0 + i_0)) = db_0;
    }
    return;
}

