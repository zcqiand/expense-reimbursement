package com.zcqiand.expense.service;

/**
 * 大模型客户端端口（六边形架构的出站端口）。
 *
 * 定义这个接口而非直接在 Service 里 new AnthropicClient，有两个好处：
 * - 测试可注入打桩实现（ApprovalOpinionServiceTests 用 stub bean 覆盖真实
 *   AnthropicOpinionClient），无需 mock 整个 SDK、也不发真实网络请求
 * - 未来换模型供应商（如换成本地推理服务）只新增一个实现类，Service 不动
 *
 * 单方法 chat(systemPrompt, userPrompt) 返回模型的纯文本输出——JSON 解析
 * 与校验留在 Service 层，让「结构化输出约束」与「验证-修复循环」在 Service
 * 里可见、可测、可读。
 */
public interface OpinionModelClient {

    /**
     * 发一次对话请求，返回模型的纯文本回复。
     *
     * @param systemPrompt 系统提示（角色 + 输出格式约束）
     * @param userPrompt   用户提示（报销单上下文 + 任务）
     * @return 模型的文本回复（理论上应是一段 JSON，但解析与校验由调用方负责）
     */
    String chat(String systemPrompt, String userPrompt);
}
