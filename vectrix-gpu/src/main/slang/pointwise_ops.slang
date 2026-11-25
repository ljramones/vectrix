// The element-wise operations of nn.Pointwise, selected by an op code: one Java operation per element. exp and log
// are ReproducibleMath's (common.slang); sqrt, +, -, *, / are the device's, correctly rounded on IEEE devices;
// max and min are Java's Math.max/min. Unary ops ignore `b`. The codes match GpuPointwise.Op.
// Java twin: org.dynamisengine.vectrix.nn.Pointwise.
import common;

static const uint PW_EXP = 0;
static const uint PW_LOG = 1;
static const uint PW_SQRT = 2;
static const uint PW_NEG = 3;
static const uint PW_SQUARE = 4;
static const uint PW_ADD = 5;
static const uint PW_SUB = 6;
static const uint PW_MUL = 7;
static const uint PW_DIV = 8;
static const uint PW_MAX = 9;
static const uint PW_MIN = 10;

float pointwiseOp(uint op, float a, float b)
{
    switch (op)
    {
    case PW_EXP:
        return reproducibleExp(a);
    case PW_LOG:
        return reproducibleLog(a);
    case PW_SQRT:
        return sqrt(a);
    case PW_NEG:
        return asfloat(asuint(a) ^ 0x80000000u);
    case PW_SQUARE:
        return a * a;
    case PW_ADD:
        return a + b;
    case PW_SUB:
        return a - b;
    case PW_MUL:
        return a * b;
    case PW_DIV:
        return a / b;
    case PW_MAX:
        return jmax(a, b);
    default:
        return jmin(a, b);
    }
}
