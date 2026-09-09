package com.anxin.ocr.preprocess;

import com.anxin.ocr.model.DetPreprocessResult;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 文本检测预处理器
 * 把原始图片转成神经网络能吃的标准格式数据。
 */
public class DetPreprocessor {

    private static final float[] MEAN = {0.485f, 0.456f, 0.406f};
    private static final float[] STD = {0.229f, 0.224f, 0.225f};

    public DetPreprocessResult process(BufferedImage image) {
        int shortSide = 960;
        int w = image.getWidth();
        int h = image.getHeight();
        float scale = (float) shortSide / Math.min(w, h);
        int newW = Math.round(w * scale);
        int newH = Math.round(h * scale);
        newW = Math.max(32, (newW / 32) * 32);
        newH = Math.max(32, (newH / 32) * 32);

        BufferedImage resized = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, newW, newH, null);
        g.dispose();

        int channel = 3;
        int n = channel * newH * newW;
        float[] data = new float[n];

        for (int c = 0; c < channel; c++) {
            for (int y = 0; y < newH; y++) {
                for (int x = 0; x < newW; x++) {
                    int rgb = resized.getRGB(x, y);
                    int val;
                    switch (c) {
                        case 0 -> val = (rgb >> 16) & 0xFF;
                        case 1 -> val = (rgb >> 8) & 0xFF;
                        default -> val = rgb & 0xFF;
                    }
                    data[c * newH * newW + y * newW + x] = (val / 255.0f - MEAN[c]) / STD[c];
                }
            }
        }
        return new DetPreprocessResult(data, newW, newH);
    }
}