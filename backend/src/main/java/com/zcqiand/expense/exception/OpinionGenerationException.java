package com.zcqiand.expense.exception;

/**
 * 审批意见生成相关业务异常 → HTTP 409。
 *
 * 触发场景：
 * - 报销单尚无任何审批记录，生成意见缺少上下文（OPINION_NO_APPROVAL_RECORD）
 * - 模型连续返回非法 JSON、超出验证-修复循环上限仍不合规（OPINION_MODEL_OUTPUT_INVALID）
 *
 * 用静态工厂方法封装两种语义的 code/默认 message，构造器私有——让调用点
 * 读起来像业务事件而非裸 new，code 不会拼错。
 */
public class OpinionGenerationException extends BusinessException {

    /** 报销单无审批记录：意见生成缺少前置上下文。 */
    public static OpinionGenerationException noApprovalRecord(Long expenseId) {
        return new OpinionGenerationException(409, "OPINION_NO_APPROVAL_RECORD",
                "报销单尚无审批记录，无法生成审批意见: id=" + expenseId);
    }

    /** 模型输出经多轮 repair 仍不合规。 */
    public static OpinionGenerationException modelOutputInvalid(String detail) {
        return new OpinionGenerationException(409, "OPINION_MODEL_OUTPUT_INVALID",
                "模型输出经多轮修复仍不合规: " + detail);
    }

    private OpinionGenerationException(int httpStatus, String code, String message) {
        super(httpStatus, code, message);
    }
}
