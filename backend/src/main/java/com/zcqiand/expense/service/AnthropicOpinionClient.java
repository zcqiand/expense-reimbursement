package com.zcqiand.expense.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpinionModelClient 的 Anthropic 实现——调 Claude claude-opus-4-7。
 *
 * 这是 ch16 的「精准控制」里唯一真正碰外部模型的组件。设计要点：
 *
 * - client 用 AnthropicOkHttpClient.fromEnv()：fromEnv() 是 OkHttp 实现类的
 *   静态工厂，读 ANTHROPIC_API_KEY 环境变量，不把密钥写进代码或配置
 *   （CLAUDE.md：敏感信息走环境变量）；返回 AnthropicClient 接口类型
 * - 模型锁定 Model.CLAUDE_OPUS_4_7：本书 version-lock 一致性要求，与 ch16
 *   正文引用的模型 id 对齐（SDK 2.40.1 已核实存在此枚举常量）
 * - 不在这里做 JSON 解析或重试：只负责「把 system+user 发出去、把文本收
 *   回来」，让结构化约束、验证、修复循环都集中在 ApprovalOpinionService，
 *   那是 ch16 要讲透的地方
 * - 内容提取：msg.content() 是 List<ContentBlock>，混合 Text/ToolUse 等块；
 *   过滤出 Text 块、拼成一段文本返回。绝大多数情况只会有一个 Text 块
 *
 * @ConditionalOnProperty(app.opinion.anthropic.enabled)：缺 ANTHROPIC_API_KEY
 * 的环境（如 CI、单测）里这个 bean 不创建——ApprovalOpinionService 注入的是
 * 测试提供的 @Primary stub bean。生产环境在 application.yml 或环境变量里把
 * 它打开（默认 true）即可。这不是 @Lazy 取巧，而是显式的环境装配开关。
 *
 * 注意：本类持有的是 SDK 客户端单例，线程安全（OkHttp client 复用连接池），
 * 所以单例即可。
 */
@Component
@ConditionalOnProperty(name = "app.opinion.anthropic.enabled", havingValue = "true", matchIfMissing = true)
public class AnthropicOpinionClient implements OpinionModelClient {

    private static final long MAX_TOKENS = 1024L;

    private final AnthropicClient client;

    public AnthropicOpinionClient() {
        // fromEnv() 读 ANTHROPIC_API_KEY 并组装 OkHttp transport。
        this.client = AnthropicOkHttpClient.fromEnv();
    }

    /** 测试或定制注入可传入已构造的 client；生产走无参构造器。 */
    AnthropicOpinionClient(AnthropicClient client) {
        this.client = client;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        MessageCreateParams params = MessageCreateParams.builder()
                .maxTokens(MAX_TOKENS)
                .addSystemMessage(systemPrompt)
                .addUserMessage(userPrompt)
                .model(Model.CLAUDE_OPUS_4_7)
                .build();

        Message message = client.messages().create(params);

        // content() 是混合块列表；取所有 Text 块的文本拼起来。
        // 正常一次回复只有一个 Text 块，拼接是防御性写法。
        return message.content().stream()
                .filter(ContentBlock::isText)
                .map(block -> block.asText().text())
                .collect(Collectors.joining());
    }
}
