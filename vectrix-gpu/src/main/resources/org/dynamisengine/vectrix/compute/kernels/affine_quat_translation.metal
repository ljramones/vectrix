#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
struct QuatTranslationParams_0
{
    uint count_0;
    uint qOff_0;
    uint tOff_0;
    uint tStride_0;
    uint outOff_0;
    uint gridX_0;
};

struct KernelContext_0
{
    QuatTranslationParams_0 constant* p_0;
    float device* rotation_0;
    float device* translation_0;
    float device* outAffine_0;
};

[[kernel]] void quatTranslationToAffine(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], QuatTranslationParams_0 constant* p_1 [[buffer(3)]], float device* rotation_1 [[buffer(0)]], float device* translation_1 [[buffer(1)]], float device* outAffine_1 [[buffer(2)]])
{
    thread KernelContext_0 kernelContext_0;
    (&kernelContext_0)->p_0 = p_1;
    (&kernelContext_0)->rotation_0 = rotation_1;
    (&kernelContext_0)->translation_0 = translation_1;
    (&kernelContext_0)->outAffine_0 = outAffine_1;
    uint i_0 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(i_0 >= (p_1->count_0))
    {
        return;
    }
    uint q_0 = (&kernelContext_0)->p_0->qOff_0 + 4U * i_0;
    float device* _S1 = (&kernelContext_0)->rotation_0+q_0;
    float device* _S2 = (&kernelContext_0)->rotation_0+(q_0 + 1U);
    float device* _S3 = (&kernelContext_0)->rotation_0+(q_0 + 2U);
    float device* _S4 = (&kernelContext_0)->rotation_0+(q_0 + 3U);
    float tx_0 = *((&kernelContext_0)->translation_0+((&kernelContext_0)->p_0->tOff_0 + i_0));
    float ty_0 = *((&kernelContext_0)->translation_0+((&kernelContext_0)->p_0->tOff_0 + (&kernelContext_0)->p_0->tStride_0 + i_0));
    float tz_0 = *((&kernelContext_0)->translation_0+((&kernelContext_0)->p_0->tOff_0 + 2U * (&kernelContext_0)->p_0->tStride_0 + i_0));
    float dqx_0 = *_S1 + *_S1;
    float dqy_0 = *_S2 + *_S2;
    float dqz_0 = *_S3 + *_S3;
    float q00_0 = dqx_0 * *_S1;
    float q11_0 = dqy_0 * *_S2;
    float q22_0 = dqz_0 * *_S3;
    float q01_0 = dqx_0 * *_S2;
    float q02_0 = dqx_0 * *_S3;
    float q03_0 = dqx_0 * *_S4;
    float q12_0 = dqy_0 * *_S3;
    float q13_0 = dqy_0 * *_S4;
    float q23_0 = dqz_0 * *_S4;
    uint o_0 = (&kernelContext_0)->p_0->outOff_0 + 12U * i_0;
    *((&kernelContext_0)->outAffine_0+o_0) = 1.0 - (q11_0 + q22_0);
    *((&kernelContext_0)->outAffine_0+(o_0 + 1U)) = q01_0 - q23_0;
    *((&kernelContext_0)->outAffine_0+(o_0 + 2U)) = q02_0 + q13_0;
    *((&kernelContext_0)->outAffine_0+(o_0 + 3U)) = tx_0;
    *((&kernelContext_0)->outAffine_0+(o_0 + 4U)) = q01_0 + q23_0;
    *((&kernelContext_0)->outAffine_0+(o_0 + 5U)) = 1.0 - (q22_0 + q00_0);
    *((&kernelContext_0)->outAffine_0+(o_0 + 6U)) = q12_0 - q03_0;
    *((&kernelContext_0)->outAffine_0+(o_0 + 7U)) = ty_0;
    *((&kernelContext_0)->outAffine_0+(o_0 + 8U)) = q02_0 - q13_0;
    *((&kernelContext_0)->outAffine_0+(o_0 + 9U)) = q12_0 + q03_0;
    *((&kernelContext_0)->outAffine_0+(o_0 + 10U)) = 1.0 - (q11_0 + q00_0);
    *((&kernelContext_0)->outAffine_0+(o_0 + 11U)) = tz_0;
    return;
}

