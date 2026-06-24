package com.zcqiand.expense.dto;

/**
 * 审批意见的结构化目标——第 16 章「精准控制大模型」的核心产物。
 *
 * 大模型被约束为只输出符合这个结构的 JSON：三个字段都非空才算合格。
 * 用 record 是因为它正是「不可变值对象」：生成后字段不再变动，需要改就
 * 重新生成一条。
 *
 * 字段语义：
 * - summary    一句话结论（建议批准 / 建议驳回 / 建议补充材料）
 * - reasoning  推理过程——模型读了报销单金额、事由、审批历史后给出的判断依据
 * - suggestion 给审批人的可操作建议（如「请补充发票编号」「金额异常需面谈」）
 */
public record ApprovalOpinion(String summary, String reasoning, String suggestion) {

    /**
     * 紧凑校验：三字段都非空白才算结构化输出合格。验证-修复循环据此决定
     * 是否进入 repair 重试。
     */
    public boolean isComplete() {
        return summary != null && !summary.isBlank()
                && reasoning != null && !reasoning.isBlank()
                && suggestion != null && !suggestion.isBlank();
    }
}
