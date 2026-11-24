#pragma METAL fp math_mode(safe)
#pragma METAL fp contract(off)
#include <metal_stdlib>
#include <metal_math>
#include <metal_texture>
using namespace metal;
struct PaletteParams_0
{
    uint jointCount_0;
    uint jointsOff_0;
    uint jointStride_0;
    uint palOff_0;
    uint scaled_0;
    uint gridX_0;
};

struct KernelContext_0
{
    PaletteParams_0 constant* p_0;
    float device* joints_0;
    float device* palette_0;
};

float J_0(uint field_0, uint j_0, KernelContext_0 thread* kernelContext_0)
{
    return *(kernelContext_0->joints_0+(kernelContext_0->p_0->jointsOff_0 + field_0 * kernelContext_0->p_0->jointStride_0 + j_0));
}

[[kernel]] void buildPalette(uint3 group_0 [[threadgroup_position_in_grid]], uint3 local_0 [[thread_position_in_threadgroup]], PaletteParams_0 constant* p_1 [[buffer(2)]], float device* joints_1 [[buffer(0)]], float device* palette_1 [[buffer(1)]])
{
    thread KernelContext_0 kernelContext_1;
    (&kernelContext_1)->p_0 = p_1;
    (&kernelContext_1)->joints_0 = joints_1;
    (&kernelContext_1)->palette_0 = palette_1;
    uint j_1 = (group_0.y * p_1->gridX_0 + group_0.x) * 64U + local_0.x;
    if(j_1 >= (p_1->jointCount_0))
    {
        return;
    }
    float _S1 = J_0(3U, j_1, &kernelContext_1);
    float _S2 = J_0(4U, j_1, &kernelContext_1);
    float _S3 = J_0(5U, j_1, &kernelContext_1);
    float _S4 = J_0(6U, j_1, &kernelContext_1);
    float _S5 = J_0(0U, j_1, &kernelContext_1);
    float _S6 = J_0(1U, j_1, &kernelContext_1);
    float _S7 = J_0(2U, j_1, &kernelContext_1);
    float sx_0;
    if(((&kernelContext_1)->p_0->scaled_0) != 0U)
    {
        float _S8 = J_0(7U, j_1, &kernelContext_1);
        sx_0 = _S8;
    }
    else
    {
        sx_0 = 1.0;
    }
    float sy_0;
    if(((&kernelContext_1)->p_0->scaled_0) != 0U)
    {
        float _S9 = J_0(8U, j_1, &kernelContext_1);
        sy_0 = _S9;
    }
    else
    {
        sy_0 = 1.0;
    }
    float sz_0;
    if(((&kernelContext_1)->p_0->scaled_0) != 0U)
    {
        float _S10 = J_0(9U, j_1, &kernelContext_1);
        sz_0 = _S10;
    }
    else
    {
        sz_0 = 1.0;
    }
    float xx_0 = _S1 * _S1;
    float yy_0 = _S2 * _S2;
    float zz_0 = _S3 * _S3;
    float xy_0 = _S1 * _S2;
    float xz_0 = _S1 * _S3;
    float yz_0 = _S2 * _S3;
    float wx_0 = _S4 * _S1;
    float wy_0 = _S4 * _S2;
    float wz_0 = _S4 * _S3;
    uint o_0 = (&kernelContext_1)->p_0->palOff_0 + j_1 * 12U;
    *((&kernelContext_1)->palette_0+o_0) = (1.0 - 2.0 * (yy_0 + zz_0)) * sx_0;
    *((&kernelContext_1)->palette_0+(o_0 + 1U)) = 2.0 * (xy_0 - wz_0) * sy_0;
    *((&kernelContext_1)->palette_0+(o_0 + 2U)) = 2.0 * (xz_0 + wy_0) * sz_0;
    *((&kernelContext_1)->palette_0+(o_0 + 3U)) = _S5;
    *((&kernelContext_1)->palette_0+(o_0 + 4U)) = 2.0 * (xy_0 + wz_0) * sx_0;
    *((&kernelContext_1)->palette_0+(o_0 + 5U)) = (1.0 - 2.0 * (xx_0 + zz_0)) * sy_0;
    *((&kernelContext_1)->palette_0+(o_0 + 6U)) = 2.0 * (yz_0 - wx_0) * sz_0;
    *((&kernelContext_1)->palette_0+(o_0 + 7U)) = _S6;
    *((&kernelContext_1)->palette_0+(o_0 + 8U)) = 2.0 * (xz_0 - wy_0) * sx_0;
    *((&kernelContext_1)->palette_0+(o_0 + 9U)) = 2.0 * (yz_0 + wx_0) * sy_0;
    *((&kernelContext_1)->palette_0+(o_0 + 10U)) = (1.0 - 2.0 * (xx_0 + yy_0)) * sz_0;
    *((&kernelContext_1)->palette_0+(o_0 + 11U)) = _S7;
    return;
}

