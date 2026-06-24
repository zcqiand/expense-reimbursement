package com.zcqiand.expense.service;

/**
 * OCR 识别端口。第 37 章对外暴露的最小契约。
 *
 * 端口/适配器风格：业务侧（OcrService）只依赖这个接口，具体实现
 * （TesseractOcrClient 调真实 Tesseract / MockOcrClient 给确定性示例文本）
 * 由 Spring 按 app.ocr.engine 配置装配，互不感知。
 *
 * 入参用文件路径字符串而非 MultipartFile——把"读字节流"职责下沉到适配器，
 * 端口保持薄；TesseractOcrClient 用 ProcessBuilder 调 CLI 时本就需要文件路径。
 */
public interface OcrClient {

    /**
     * 对指定图片做 OCR，返回识别出的纯文本（可多行、含中文）。
     *
     * @param imagePath 图片在文件系统上的路径
     * @return OCR 识别结果文本；不返回 null
     * @throws RuntimeException 识别失败（如 tesseract 未安装、文件不存在）
     */
    String recognize(String imagePath);
}
