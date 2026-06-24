package com.zcqiand.expense.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于 Tesseract CLI 的 OCR 适配器。
 *
 * 不引入 Tess4J 的 JNI 绑定——直接用 ProcessBuilder 调系统装的 tesseract 命令行，
 * 部署形态最轻：只要容器/机器上 apt install tesseract-ocr tesseract-ocr-chi-sim
 * 即可，JVM 侧无需 native 库。
 *
 * 装配条件：app.ocr.engine=tesseract 才装配（matchIfMissing=false）。
 * 默认回落到 MockOcrClient，保证本机未装 tesseract 时测试与本地开发仍可跑。
 *
 * 用法：tesseract <image> stdout -l chi_sim+eng
 * 解析 stdout 拿识别文本，进程退出码非 0 视为失败抛 RuntimeException。
 */
@Component
@ConditionalOnProperty(name = "app.ocr.engine", havingValue = "tesseract", matchIfMissing = false)
public class TesseractOcrClient implements OcrClient {

    /** 默认语言包：中文简体 + 英文，覆盖国内增值税/打车票等场景。 */
    private static final String DEFAULT_LANG = "chi_sim+eng";

    private final String tesseractCommand;
    private final String lang;

    public TesseractOcrClient() {
        this("tesseract", DEFAULT_LANG);
    }

    /** 测试 / 自定义可注入命令与语言。包级可见，便于后续替换实现。 */
    TesseractOcrClient(String tesseractCommand, String lang) {
        this.tesseractCommand = tesseractCommand;
        this.lang = lang;
    }

    @Override
    public String recognize(String imagePath) {
        if (imagePath == null || imagePath.isBlank()) {
            throw new IllegalArgumentException("imagePath 不能为空");
        }
        Path image = Path.of(imagePath);
        if (!Files.isRegularFile(image)) {
            throw new RuntimeException("OCR 输入文件不存在: " + imagePath);
        }

        try {
            // 让 tesseract 把结果打到 stdout，避免落临时文件再读
            ProcessBuilder pb = new ProcessBuilder(
                    tesseractCommand, image.toString(), "stdout", "-l", lang);
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String output = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String err = new String(process.getErrorStream().readAllBytes(),
                        StandardCharsets.UTF_8);
                throw new RuntimeException("tesseract 退出码 " + exitCode + ": " + err.trim());
            }
            return output;
        } catch (IOException e) {
            throw new RuntimeException("调用 tesseract 失败（是否已安装 tesseract-ocr？）: "
                    + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OCR 被中断", e);
        }
    }
}
