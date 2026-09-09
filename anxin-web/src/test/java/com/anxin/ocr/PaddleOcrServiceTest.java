package com.anxin.ocr;

import com.anxin.ocr.service.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@SpringBootTest
class PaddleOcrServiceTest {

    @Resource
    private OcrService ocrService;

    @Test
    void recognizeImage() throws IOException {
        String imagePath = "D:\\JavaCode\\anxin-backend\\anxin-web\\src\\test\\java\\com\\anxin\\ocr\\testdocument\\test1.png";

        log.info("测试图片路径: {}", imagePath);
        byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
        log.info("读取到的图片字节数: {}", imageBytes.length);

        log.info("开始调用 OCR 识别...");
        String result = ocrService.recognize(imageBytes, "image/png");
        log.info("OCR 识别结果: {}", result);

        System.out.println("=== OCR 识别结果 ===");
        System.out.println(result);
        System.out.println("==================");

        assertFalse(result.isBlank(), "识别结果不应为空");
    }
}
