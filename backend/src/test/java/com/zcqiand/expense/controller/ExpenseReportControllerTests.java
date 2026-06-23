package com.zcqiand.expense.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcqiand.expense.controller.ExpenseReportController.CreateExpenseRequest;
import com.zcqiand.expense.dto.ApprovalRequest;
import com.zcqiand.expense.entity.ApprovalDecision;
import com.zcqiand.expense.entity.ApprovalLevel;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报销单 Controller MockMvc 集成测试。
 *
 * 用 @SpringBootTest + @AutoConfigureMockMvc：真实启动 Spring 容器，
 * MockMvc 直接打 HTTP 请求穿过 DispatcherServlet → Controller → Service →
 * Repository → H2，全栈集成。
 *
 * 覆盖：
 * - 创建报销单返回 ApiResponse{success:true}
 * - 状态机端点：submit/approve/pay 正向流程
 * - 异常翻译：404（不存在）/ 409（状态冲突）/ 400（校验失败）都由
 *   GlobalExceptionHandler 翻译成统一 ApiResponse{success:false}
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:expense-mvc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
class ExpenseReportControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExpenseReportRepository repository;

    private ExpenseReport savedReport;

    @BeforeEach
    void setupReport() {
        ExpenseReport r = new ExpenseReport();
        r.setApplicantId(1L);
        r.setAmount(new BigDecimal("500.00"));
        r.setReason("出差打车");
        r.setStatus(ExpenseStatus.SUBMITTED);
        this.savedReport = repository.save(r);
    }

    @Test
    @DisplayName("POST /api/v1/expense-reports 创建成功返回 ApiResponse.ok")
    void createReturnsApiResponseOk() throws Exception {
        CreateExpenseRequest body = new CreateExpenseRequest(
                10L, new BigDecimal("888.00"), "晚餐招待");

        mockMvc.perform(post("/api/v1/expense-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.amount").value(888.00))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/expense-reports/{id} 找不到返回 404 + ApiResponse.error")
    void getNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/expense-reports/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EXPENSE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /{id}/approve 小额 MANAGER 通过 → 200 + APPROVED")
    void approveSmallAmountByManager() throws Exception {
        ApprovalRequest body = new ApprovalRequest(2L, ApprovalLevel.MANAGER,
                ApprovalDecision.APPROVED, null);

        mockMvc.perform(post("/api/v1/expense-reports/{id}/approve", savedReport.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    @DisplayName("POST /{id}/approve 申请人审批自己 → 400 ApiResponse.error VALIDATION_FAILED")
    void approveByApplicantSelfReturns400() throws Exception {
        ApprovalRequest body = new ApprovalRequest(1L, ApprovalLevel.MANAGER,
                ApprovalDecision.APPROVED, null);

        mockMvc.perform(post("/api/v1/expense-reports/{id}/approve", savedReport.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /{id}/pay 在 SUBMITTED 状态 → 409 ILLEGAL_STATE_TRANSITION")
    void payFromSubmittedReturns409() throws Exception {
        mockMvc.perform(post("/api/v1/expense-reports/{id}/pay", savedReport.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    @DisplayName("端到端：submit → approve → pay 三个 HTTP 端点串通")
    void endToEndHttpFlow() throws Exception {
        // 先重置为 DRAFT
        savedReport.setStatus(ExpenseStatus.DRAFT);
        repository.save(savedReport);
        Long id = savedReport.getId();

        // submit
        mockMvc.perform(post("/api/v1/expense-reports/{id}/submit", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));

        // approve
        ApprovalRequest approveBody = new ApprovalRequest(2L, ApprovalLevel.MANAGER,
                ApprovalDecision.APPROVED, null);
        mockMvc.perform(post("/api/v1/expense-reports/{id}/approve", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // pay
        mockMvc.perform(post("/api/v1/expense-reports/{id}/pay", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"));
    }
}
