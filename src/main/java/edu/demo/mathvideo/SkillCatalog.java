package edu.demo.mathvideo;

import edu.demo.mathvideo.PrecisionModels.Skill;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class SkillCatalog {
    private final List<Skill> skills = List.of(
        s("signed-add", "有理数加减中的符号判断", "有理数", 7),
        s("negative-square", "区分负数的平方与相反数", "有理数", 7),
        s("remove-brackets", "去括号时正确变号", "整式", 7),
        s("combine-terms", "识别并合并同类项", "整式", 7),
        s("equation-transpose", "移项时变号与等式两边同运算", "一元一次方程", 7, "signed-add"),
        s("equation-fraction", "方程去分母时不漏乘", "一元一次方程", 7, "remove-brackets"),
        s("word-equation", "从题意找到等量关系并列方程", "方程应用", 7),
        s("parallel-angle", "由平行线判断同位角与内错角", "相交线与平行线", 7),
        s("coordinate-point", "读写坐标与判断点所在象限", "平面直角坐标系", 7),
        s("linear-system", "用代入或加减消元解二元一次方程组", "二元一次方程组", 7, "equation-transpose"),
        s("inequality-sign", "不等式乘除负数时改变不等号方向", "不等式", 7, "signed-add"),
        s("triangle-angle", "运用三角形内角和与外角关系", "三角形", 8, "parallel-angle"),
        s("congruence", "选择三角形全等判定条件", "全等三角形", 8),
        s("isosceles", "运用等腰三角形的边角关系", "等腰三角形", 8),
        s("power-rules", "区分同底数幂乘法与幂的乘方", "整式乘除", 8),
        s("factor-common", "提取公因式", "因式分解", 8, "combine-terms"),
        s("factor-formula", "识别平方差与完全平方公式", "因式分解", 8),
        s("quadratic-completing-square", "配方法中一次项系数减半与正负号", "一元二次方程配方法", 9, "factor-formula"),
        s("fraction-domain", "确定分式有意义的条件", "分式", 8),
        s("fraction-equation-check", "解分式方程后检验增根", "分式方程", 8, "equation-fraction"),
        s("radical-simplify", "化简二次根式并确定取值范围", "二次根式", 8),
        s("pythagoras", "用勾股定理建立边长关系", "勾股定理", 8),
        s("parallelogram", "选择平行四边形的性质与判定", "四边形", 8),
        s("linear-function-slope", "判断一次函数的增减性和图象位置", "一次函数", 8),
        s("linear-function-fit", "用待定系数法求一次函数解析式", "一次函数", 8, "linear-system"),
        s("quadratic-coefficients", "化成一般式并正确识别 a、b、c", "一元二次方程", 9, "equation-transpose"),
        s("quadratic-factor", "因式分解法解一元二次方程", "一元二次方程", 9, "factor-formula"),
        s("quadratic-formula", "正确代入一元二次方程求根公式", "一元二次方程", 9, "quadratic-coefficients", "radical-simplify"),
        s("discriminant-compute", "计算判别式时正确处理负号与平方", "一元二次方程", 9, "negative-square", "quadratic-coefficients"),
        s("discriminant-count", "根据判别式的正负判断实数根情况", "一元二次方程", 9, "discriminant-compute"),
        s("root-coefficients", "使用两根之和与两根之积", "一元二次方程", 9, "quadratic-coefficients"),
        s("quadratic-vertex", "配方求二次函数顶点与对称轴", "二次函数", 9, "factor-formula"),
        s("quadratic-maximum", "根据顶点及自变量范围求最值", "二次函数", 9, "quadratic-vertex"),
        s("inverse-function", "根据点坐标求反比例函数及判断象限", "反比例函数", 9, "coordinate-point"),
        s("circle-angle", "运用圆周角与圆心角关系", "圆", 9),
        s("circle-tangent", "运用切线与半径垂直的关系", "圆", 9),
        s("similarity", "寻找对应边并列相似比例式", "相似三角形", 9),
        s("trigonometry", "选择锐角三角函数求直角三角形边角", "锐角三角函数", 9),
        s("probability-tree", "用列表或树状图计算等可能事件概率", "概率", 9),
        s("statistics", "区分平均数、中位数与众数", "统计", 8)
    );
    private static Skill s(String id, String name, String topic, int grade, String... prerequisites) {
        return new Skill(id, name, topic, grade, List.of(prerequisites));
    }
    public List<Skill> all() { return skills; }
    public Skill get(String id) { return skills.stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow(() -> new IllegalArgumentException("未收录的初中数学卡点")); }
    public boolean contains(String id) { return skills.stream().anyMatch(s -> s.id().equals(id)); }
}
