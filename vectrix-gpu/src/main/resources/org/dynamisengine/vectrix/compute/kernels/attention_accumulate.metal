#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
uint keysFor_0(uint r_0, uint queries_0, uint t_0, uint causal_0)
{
    uint _S1;
    if(causal_0 != 0U)
    {
        _S1 = t_0 - queries_0 + 1U + r_0;
    }
    else
    {
        _S1 = t_0;
    }
    return _S1;
}

struct AccumulateParams_0
{
    uint heads_0;
    uint group_0;
    uint kvHeads_0;
    uint queries_1;
    uint t_1;
    uint causal_1;
    uint width_0;
    uint scaled_0;
    float scale_0;
    uint wOff_0;
    uint ldw_0;
    uint xOff_0;
    uint ldx_0;
    uint destOff_0;
    uint ldDest_0;
    uint chunks_0;
    uint gridX_0;
};

struct KernelContext_0
{
    AccumulateParams_0 constant* p_0;
    float device* dest_0;
    float device* w_0;
    float device* x_0;
};

[[kernel]] void attentionAccumulate(uint3 group_1 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], AccumulateParams_0 constant* p_1 [[buffer(3)]], float device* dest_1 [[buffer(2)]], float device* w_1 [[buffer(0)]], float device* x_1 [[buffer(1)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->dest_0 = dest_1;
    (&kernelContext_0)->w_0 = w_1;
    (&kernelContext_0)->x_0 = x_1;
    uint gi_0 = group_1.y * p_1->gridX_0 + group_1.x;
    uint perKey_0 = p_1->kvHeads_0 * p_1->chunks_0;
    uint j_0 = gi_0 / perKey_0;
    if(j_0 >= ((&kernelContext_0)->p_0->t_1))
    {
        return;
    }
    uint _S2 = gi_0 % perKey_0;
    uint g_0 = _S2 / p_1->chunks_0;
    uint _S3 = gi_0 % p_1->chunks_0;
    uint i_0 = _S3 * 64U + local_0.x;
    if(i_0 >= ((&kernelContext_0)->p_0->width_0))
    {
        return;
    }
    uint di_0 = (&kernelContext_0)->p_0->destOff_0 + j_0 * (&kernelContext_0)->p_0->ldDest_0 + g_0 * (&kernelContext_0)->p_0->width_0 + i_0;
    float acc_0 = *((&kernelContext_0)->dest_0+di_0);
    uint r_1 = 0U;
    for(;;)
    {
        if(r_1 < ((&kernelContext_0)->p_0->queries_1))
        {
        }
        else
        {
            break;
        }
        if(j_0 < (keysFor_0(r_1, (&kernelContext_0)->p_0->queries_1, (&kernelContext_0)->p_0->t_1, (&kernelContext_0)->p_0->causal_1)))
        {
            float acc_1 = acc_0;
            uint hh_0 = 0U;
            for(;;)
            {
                if(hh_0 < ((&kernelContext_0)->p_0->group_0))
                {
                }
                else
                {
                    break;
                }
                uint row_0 = r_1 * (&kernelContext_0)->p_0->heads_0 + g_0 * (&kernelContext_0)->p_0->group_0 + hh_0;
                float wv_0 = *((&kernelContext_0)->w_0+((&kernelContext_0)->p_0->wOff_0 + row_0 * (&kernelContext_0)->p_0->ldw_0 + j_0));
                float c_0;
                if(((&kernelContext_0)->p_0->scaled_0) != 0U)
                {
                    c_0 = (&kernelContext_0)->p_0->scale_0 * wv_0;
                }
                else
                {
                    c_0 = wv_0;
                }
                float _S4 = fma(c_0, *((&kernelContext_0)->x_0+((&kernelContext_0)->p_0->xOff_0 + row_0 * (&kernelContext_0)->p_0->ldx_0 + i_0)), acc_1);
                uint _S5 = hh_0 + 1U;
                acc_1 = _S4;
                hh_0 = _S5;
            }
            acc_0 = acc_1;
        }
        r_1 = r_1 + 1U;
    }
    *((&kernelContext_0)->dest_0+di_0) = acc_0;
    return;
}

