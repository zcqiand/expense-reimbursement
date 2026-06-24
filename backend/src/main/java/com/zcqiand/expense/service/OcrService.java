package com.zcqiand.expense.service;

import com.zcqiand.expense.entity.Receipt;
import com.zcqiand.expense.repository.ReceiptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OCR 票据识别应用服务。第 37 章业务编排的核心。
 *
 * 职责：调 OCR 引擎 → 把结果（成功或失败）落库为一条 Receipt → 返回。
 *
 * 设计取舍：
 * - 构造注入 OcrClient：Spring 按 app.ocr.engine 在 TesseractOcrClient 与
 *   MockOcrClient 间选唯一实现注入，OcrService 不感知具体引擎
 * - 失败也落库（ocrStatus=FAILED）：审计需要——失败要可追溯，且让前端能在
 *   详情页看到"这张票 OCR 过但失败了"，便于人工补录
 * - @Transactional：保证 save 与异常路径的一致性；client 抛异常时 catch 住、
 *   存一条 FAILED 记录后再返回（不向上抛，避免 Controller 误判为 500）
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final OcrClient ocrClient;
    private final ReceiptRepository receiptRepository;

    public OcrService(OcrClient ocrClient, ReceiptRepository receiptRepository) {
        this.ocrClient = ocrClient;
        this.receiptRepository = receiptRepository;
    }

    /**
     * 对指定报销单下的某张票据图片做 OCR 识别并落库。
     *
     * @param expenseReportId 归属的报销单 ID
     * @param filePath         票据图片在文件系统上的路径
     * @return 已持久化的 Receipt（成功 ocrStatus=SUCCESS，失败 ocrStatus=FAILED）
     */
    @Transactional
    public Receipt recognizeAndSave(Long expenseReportId, String filePath) {
        try {
            String text = ocrClient.recognize(filePath);
            Receipt receipt = new Receipt();
            receipt.setExpenseReportId(expenseReportId);
            receipt.setFilePath(filePath);
            receipt.setOcrText(text);
            receipt.setOcrStatus(Receipt.STATUS_SUCCESS);
            return receiptRepository.save(receipt);
        } catch (RuntimeException ex) {
            // OCR 引擎抛异常（如 tesseract 未装 / 文件读不出）——失败也留痕
            log.warn("OCR 识别失败 expenseReportId={} filePath={} reason={}",
                    expenseReportId, filePath, ex.getMessage());
            Receipt failed = new Receipt();
            failed.setExpenseReportId(expenseReportId);
            failed.setFilePath(filePath);
            failed.setOcrText(null);
            failed.setOcrStatus(Receipt.STATUS_FAILED);
            return receiptRepository.save(failed);
        }
    }
}
