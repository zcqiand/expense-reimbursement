package com.zcqiand.expense.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zcqiand.expense.entity.Receipt;
import com.zcqiand.expense.repository.ReceiptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * OcrService 集成测试。
 *
 * 照搬 ExpenseServiceTests 范式：@SpringBootTest + H2 内嵌库 + @Transactional。
 * 关键点：app.ocr.engine=mock，让 Spring 装配 MockOcrClient 而非 TesseractOcrClient，
 * 这样不依赖本机装 tesseract 也能跑全链路。
 *
 * 验证：
 * - recognizeAndSave 正向：返回 Receipt，ocrText 非空，ocrStatus=SUCCESS，库中可查
 * - 容器装配：MockOcrClient 是默认实现（matchIfMissing=true）
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:expense-ocr-svc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "app.ocr.engine=mock"
})
class OcrServiceTests {

    @Autowired
    private OcrService ocrService;

    @Autowired
    private OcrClient ocrClient;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Test
    @DisplayName("容器默认装配 MockOcrClient（matchIfMissing=true）")
    void mockClientIsDefaultBean() {
        assertTrue(ocrClient instanceof MockOcrClient,
                "app.ocr.engine=mock 时应装配 MockOcrClient");
    }

    @Test
    @DisplayName("recognizeAndSave 正向：ocrText 非空、ocrStatus=SUCCESS、入库可查")
    void recognizeAndSaveHappyPath() {
        Receipt result = ocrService.recognizeAndSave(42L, "/tmp/invoice.png");

        assertNotNull(result.getId(), "应已持久化并拿到自增 id");
        assertNotNull(result.getOcrText(), "ocrText 不能为空");
        assertFalse(result.getOcrText().isBlank(), "ocrText 不能是空白");
        assertEquals(Receipt.STATUS_SUCCESS, result.getOcrStatus());
        assertEquals(42L, result.getExpenseReportId());
        assertEquals("/tmp/invoice.png", result.getFilePath());

        // 入库后能按 id 再查回来
        assertTrue(receiptRepository.findById(result.getId()).isPresent(),
                "持久化后应能在库里查到");
    }

    @Test
    @DisplayName("MockOcrClient.recognize 返回含金额/发票字样的确定性示例文本")
    void mockClientReturnsDeterministicText() {
        String text = ocrClient.recognize("/tmp/any.png");

        assertNotNull(text);
        assertFalse(text.isBlank());
        assertTrue(text.contains("金额") || text.contains("发票"),
                "MockOcrClient 返回的示例文本应含金额/发票字样，便于后续金额解析演示");
    }

    @Test
    @DisplayName("recognizeAndSave 同一报销单可多次调用（票据一对多）")
    void recognizeAndSaveMultipleForSameReport() {
        Receipt r1 = ocrService.recognizeAndSave(100L, "/tmp/a.png");
        Receipt r2 = ocrService.recognizeAndSave(100L, "/tmp/b.png");

        assertNotNull(r1.getId());
        assertNotNull(r2.getId());
        assertEquals(2, receiptRepository.findByExpenseReportId(100L).size());
    }
}
