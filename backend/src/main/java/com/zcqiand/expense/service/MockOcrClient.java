package com.zcqiand.expense.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OCR 确定性桩实现。第 37 章"无 Tesseract 也能跑全链路"的默认回落。
 *
 * 装配条件：app.ocr.engine=mock 装配；matchIfMissing=true——未显式配置时
 * 默认回落到这个实现，保证本机未装 tesseract / CI 不带 native 依赖时
 * Spring 上下文仍能起来、测试仍能跑。
 *
 * recognize 返回固定文本（含"金额"/"发票"字样）：
 * - 确定性：每次返回同样字符串，便于断言
 * - 非空 + 含金额/发票：模拟真实 OCR 输出的形态，后续章节演示金额解析时
 *   不必改 Mock 即可继续用
 */
@Component
@ConditionalOnProperty(name = "app.ocr.engine", havingValue = "mock", matchIfMissing = true)
public class MockOcrClient implements OcrClient {

    /** 固定示例文本：模拟一张打车票/餐饮发票被识别后的输出。 */
    static final String SAMPLE_TEXT =
            "发票代码: 011002100111\r\n"
            + "发票号码: 48371926\r\n"
            + "开票日期: 2026-06-20\r\n"
            + "金额: 128.50\r\n"
            + "税额: 7.71\r\n"
            + "价税合计: 136.21";

    @Override
    public String recognize(String imagePath) {
        // 忽略入参——桩就是桩，不读文件、不调任何外部依赖
        return SAMPLE_TEXT;
    }
}
