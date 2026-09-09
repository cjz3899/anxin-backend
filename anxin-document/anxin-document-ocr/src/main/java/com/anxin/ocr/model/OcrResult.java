package com.anxin.ocr.model;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * OCR 结果模型。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OcrResult {
    private String text;
    private float confidence;
}