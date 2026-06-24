package com.zcqiand.expense.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zcqiand.expense.entity.ExpenseReport;
import com.zcqiand.expense.entity.ExpenseStatus;
import com.zcqiand.expense.repository.ExpenseReportRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * OcrReceiptController MockMvc 集成测试。
 *
 * 照搬 ExpenseReportControllerTests 范式：@SpringBootTest + @AutoConfigureMockMvc
 * + H2 内嵌库 + @Transactional，MockMvc 穿透 DispatcherServlet → Controller →
 * Service → Repository → H2 全栈。
 *
 * 关键点：app.ocr.engine=mock，让 MockOcrClient 接管 OCR，MockMultipartFile
 * 上传假图片字节即可，不必真有图片文件。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:expense-ocr-mvc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "app.ocr.engine=mock"
})
class OcrReceiptControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExpenseReportRepository expenseRepository;

    private Long savedReportId;

    @BeforeEach
    void setupReport() {
        ExpenseReport r = new ExpenseReport();
        r.setApplicantId(1L);
        r.setAmount(new BigDecimal("500.00"));
        r.setReason("出差打车");
        r.setStatus(ExpenseStatus.SUBMITTED);
        this.savedReportId = expenseRepository.save(r).getId();
    }

    @Test
    @DisplayName("POST /{id}/receipts 上传票据 → 200 + ApiResponse.ok + ocrText 非空")
    void uploadReceiptReturnsOcrText() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "invoice.png", "image/png", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/api/v1/expense-reports/{id}/receipts", savedReportId)
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.ocrStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.ocrText").isNotEmpty())
                .andExpect(jsonPath("$.data.expenseReportId").value(savedReportId))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("POST /{id}/receipts 上传后 ocrText 含金额/发票字样（MockOcrClient）")
    void uploadedOcrTextContainsAmountKeyword() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "receipt.jpg", "image/jpeg", new byte[]{9, 9});

        mockMvc.perform(multipart("/api/v1/expense-reports/{id}/receipts", savedReportId)
                        .file(file))
                .andExpect(status().isOk())
                // MockOcrClient 返回的固定文本应含金额或发票字样
                .andExpect(jsonPath("$.data.ocrText").value(
                        org.hamcrest.Matchers.containsString("金额")));
    }

    @Test
    @DisplayName("POST /{id}/receipts 缺 file 参数 → 400 + ApiResponse.error VALIDATION_FAILED")
    void uploadWithoutFileReturns400() throws Exception {
        mockMvc.perform(multipart("/api/v1/expense-reports/{id}/receipts", savedReportId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }
}
