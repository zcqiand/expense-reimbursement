package com.zcqiand.expense.controller;

import com.zcqiand.expense.dto.ApiResponse;
import com.zcqiand.expense.dto.ApprovalOpinion;
import com.zcqiand.expense.service.ApprovalOpinionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批意见生成 REST Controller——第 16 章案例的 HTTP 入口。
 *
 * 单端点：POST /api/expenses/{id}/opinion，调 ApprovalOpinionService 生成
 * 结构化意见并落库。异常（报销单不存在 / 无审批记录 / 模型输出不合规）由
 * GlobalExceptionHandler 统一翻译成 ApiResponse{success:false}。
 *
 * @Operation + @ApiResponse 注解是 CLAUDE.md 强制规范；@ApiResponses 用复数
 * 形式列多个状态码（200 / 404 / 409），让 Swagger UI 文档完整。
 */
@RestController
@RequestMapping("/api/expenses")
@Tag(name = "审批意见", description = "大模型结构化审批意见生成（第 16 章）")
public class ApprovalOpinionController {

    private final ApprovalOpinionService service;

    public ApprovalOpinionController(ApprovalOpinionService service) {
        this.service = service;
    }

    @PostMapping("/{id}/opinion")
    @Operation(
            summary = "为报销单生成结构化审批意见",
            description = "调 claude-opus-4-7，用 JSON Schema 约束输出 + 验证-修复循环，"
                    + "生成 summary/reasoning/suggestion 三字段意见并写入最新审批记录。"
                    + "对应第 16 章「精准控制大模型」案例。")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "意见生成成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "报销单不存在"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "报销单无审批记录 / 模型输出经多轮修复仍不合规")
    })
    public ApiResponse<ApprovalOpinion> generateOpinion(
            @Parameter(description = "报销单 ID", required = true)
            @PathVariable Long id) {
        return ApiResponse.ok(service.generateAndSave(id));
    }
}
