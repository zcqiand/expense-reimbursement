package com.zcqiand.expense.controller;

import com.zcqiand.expense.dto.ApiResponse;
import com.zcqiand.expense.entity.Receipt;
import com.zcqiand.expense.service.OcrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR 票据上传 Controller。第 37 章 REST 入口。
 *
 * 端点：POST /api/v1/expense-reports/{id}/receipts（multipart/form-data，字段名 file）
 * 把上传的图片写到系统临时目录，调 OcrService.recognizeAndSave 做识别并落库，
 * 返回 ApiResponse&lt;Receipt&gt;。异常由 GlobalExceptionHandler 统一翻译。
 *
 * 设计取舍：
 * - 路径前缀 /api/v1/expense-reports 与 ExpenseReportController 对齐——票据
 *   是报销单的子资源，RESTful 嵌套路由更清晰
 * - 上传文件先落临时盘再传路径给 OcrService：与 OcrClient 端口（吃 String 路径）
 *   契约一致，TesseractOcrClient 用 ProcessBuilder 时本就需要文件路径
 * - 构造器注入（CLAUDE.md 强制规范）
 */
@RestController
@RequestMapping("/api/v1/expense-reports")
@Tag(name = "票据 OCR", description = "报销单上传票据并做 OCR 识别")
public class OcrReceiptController {

    private static final String UPLOAD_PARAM = "file";

    private final OcrService ocrService;

    public OcrReceiptController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @PostMapping("/{id}/receipts")
    @Operation(summary = "上传票据图片并做 OCR 识别，结果落库为 Receipt")
    public ApiResponse<Receipt> uploadReceipt(@PathVariable Long id,
                                              @RequestParam(UPLOAD_PARAM) MultipartFile file)
            throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file 参数不能为空");
        }

        // 把上传字节写到临时文件——OcrClient 端口吃文件路径，统一适配 Tesseract CLI
        Path temp = Files.createTempFile("expense-receipt-", suffixOf(file));
        try {
            file.transferTo(temp.toFile());
            Receipt receipt = ocrService.recognizeAndSave(id, temp.toAbsolutePath().toString());
            return ApiResponse.ok(receipt);
        } finally {
            // 识别完即清——避免临时目录堆积；失败也走 finally 保证清理
            Files.deleteIfExists(temp);
        }
    }

    /** 从上传文件名或 Content-Type 推断后缀，保留原格式便于 Tesseract 选语言包。 */
    private static String suffixOf(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original != null) {
            int dot = original.lastIndexOf('.');
            if (dot >= 0 && dot < original.length() - 1) {
                return original.substring(dot);
            }
        }
        // 兜底：按常见图片 mime 给后缀
        String contentType = file.getContentType();
        if (contentType != null) {
            if (contentType.contains("png")) {
                return ".png";
            }
            if (contentType.contains("jpeg") || contentType.contains("jpg")) {
                return ".jpg";
            }
        }
        return ".img";
    }
}
