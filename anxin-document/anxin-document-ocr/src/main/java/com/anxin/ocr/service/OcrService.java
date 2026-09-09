package com.anxin.ocr.service;


/**
 * OCR 服务接口。
 */
public interface OcrService {

    String recognize(byte[] imageBytes, String mimeType);
}