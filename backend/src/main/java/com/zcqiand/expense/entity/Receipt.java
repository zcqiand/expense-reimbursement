package com.zcqiand.expense.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * 票据实体。第 37 章 OCR 票据识别的数据载体。
 *
 * 表字段与 Flyway V4__add_receipt.sql 一一对应——schema 真源在 Flyway 脚本，
 * Entity 是 JPA 视角的映射；任何字段变更必须先加 Flyway 迁移再改 Entity。
 *
 * 设计取舍：
 * - expenseReportId 用 Long 而非 @ManyToOne 关联报销单——OCR 与报销单生命周期
 *   解耦，弱关联更便于票据在报销单状态流转之外独立落地
 * - ocrText 不用 @Lob：Tesseract 输出的票据正文可能很长（多行 + 中文），
 *   长度诉求由 PostgreSQL text 列本身满足（text 无长度上限）。@Lob 在
 *   Hibernate 6 + PG 方言把 String 映射为 oid 大对象，与 Flyway 的 text
 *   列冲突（ddl-auto=validate 报 wrong column type，首航 2026-10-06）；
 *   无 @Lob 的 String 走 VARCHAR 语义与 text 列精确匹配
 * - ocrStatus 用 String 而非 enum：保持与表里 VARCHAR(20) CHECK 约束一致，
 *   枚举值变更不必动 schema；Service 层负责状态合法性
 * - createdAt 由 @PrePersist 维护（对齐 ExpenseReport 风格），无 updatedAt——
 *   票据 OCR 结果是一次性写入，不更新
 */
@Entity
@Table(name = "receipt")
public class Receipt {

    /** 识别结果状态常量。Service 层写入 ocrStatus 时统一走这三个值。 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_report_id", nullable = false)
    private Long expenseReportId;

    @Column(name = "file_path", nullable = false, length = 1000)
    private String filePath;

    @Column(name = "ocr_text")
    private String ocrText;

    @Column(name = "ocr_status", nullable = false, length = 20)
    private String ocrStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
        if (this.ocrStatus == null) {
            this.ocrStatus = STATUS_PENDING;
        }
    }

    // -------- accessors --------

    public Long getId() {
        return id;
    }

    public Long getExpenseReportId() {
        return expenseReportId;
    }

    public void setExpenseReportId(Long expenseReportId) {
        this.expenseReportId = expenseReportId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getOcrText() {
        return ocrText;
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }

    public String getOcrStatus() {
        return ocrStatus;
    }

    public void setOcrStatus(String ocrStatus) {
        this.ocrStatus = ocrStatus;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
