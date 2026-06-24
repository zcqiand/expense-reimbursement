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
 * 审计日志实体。第 38 章报表聚合 + 审计追溯的数据载体。
 *
 * 表字段与 Flyway V5__add_audit_log.sql 一一对应——schema 真源在 Flyway 脚本，
 * Entity 是 JPA 视角的映射；任何字段变更必须先加 Flyway 迁移再改 Entity。
 *
 * 设计取舍：
 * - actionType / entityType 用 String 而非 enum：保持与 expense_report.status /
 *   receipt.ocr_status 同一风格——枚举值变更不必动 schema，靠 SQL CHECK 兜底，
 *   Entity 侧提供 public static final String 常量给业务层引用
 * - detail 用 VARCHAR(2000) 且可空：审计流水主体靠 (actionType, entityType,
 *   entityId, operatorId) 四元组定位，detail 是给运营查证用的自由文本，可空
 * - createdAt 由 @PrePersist 维护，无 updatedAt——审计日志只追加不可改，是
 *   合规审计的硬性要求（任何 update / delete 都会破坏追溯链）
 * - 弱关联：通过 (entityType, entityId) 定位业务实体而非外键——审计要能追溯
 *   已删除的实体，硬外键反而坏事
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    // -------- actionType 取值常量：业务层写入 actionType 时统一引用 --------

    /** 提交报销单（DRAFT → SUBMITTED）。 */
    public static final String ACTION_SUBMIT = "SUBMIT";
    /** 审批通过 / 拒绝报销单。 */
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_REJECT = "REJECT";
    /** 财务付款（APPROVED → PAID）。 */
    public static final String ACTION_PAY = "PAY";
    /** OCR 票据识别（PENDING → SUCCESS / FAILED）。 */
    public static final String ACTION_OCR = "OCR";

    // -------- entityType 取值常量 --------

    /** 被审计实体是报销单。 */
    public static final String ENTITY_EXPENSE_REPORT = "EXPENSE_REPORT";
    /** 被审计实体是票据。 */
    public static final String ENTITY_RECEIPT = "RECEIPT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType;

    @Column(name = "entity_type", nullable = false, length = 30)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "detail", length = 2000)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

    // -------- accessors --------

    public Long getId() {
        return id;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
