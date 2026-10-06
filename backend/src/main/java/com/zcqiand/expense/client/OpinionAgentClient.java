package com.zcqiand.expense.client;

import com.zcqiand.expense.dto.ApprovalOpinion;

/**
 * Opinion 生成代理端口——v2 编排内核搬入 Python sidecar（LangGraph 生成图），
 * Spring 只组装结构化上下文、经 HTTP 取回意见；prompt 工程与验证-修复循环
 * 全部随内核搬家（见 agent/ 与 spec docs/superpowers/specs）。
 *
 * 返回类型复用 v1 的 dto.ApprovalOpinion（三段意见 record + isComplete()），
 * Controller / 前端契约零改动（换芯不换壳）。
 */
public interface OpinionAgentClient {

    ApprovalOpinion generate(OpinionContext context);
}
