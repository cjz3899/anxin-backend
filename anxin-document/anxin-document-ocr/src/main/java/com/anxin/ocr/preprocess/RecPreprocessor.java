package com.anxin.ocr.preprocess;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 识别模型预处理
 */
public class RecPreprocessor {

    private static final float[] MEAN = {0.5f, 0.5f, 0.5f};
    private static final float[] STD = {0.5f, 0.5f, 0.5f};
    private static final int IMG_HEIGHT = 48;
    private static final int IMG_MAX_WIDTH = 320;

    public float[] process(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();

        float ratio = (float) IMG_HEIGHT / h;
        int resizedW = Math.min(Math.round(w * ratio), IMG_MAX_WIDTH);
        int resizedH = IMG_HEIGHT;

        BufferedImage resized = new BufferedImage(resizedW, resizedH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(image, 0, 0, resizedW, resizedH, null);
        g.dispose();


        int n = 3 * IMG_HEIGHT * IMG_MAX_WIDTH;
        float[] data = new float[n];

        for (int c = 0; c < 3; c++) {
            for (int y = 0; y < resizedH; y++) {
                for (int x = 0; x < resizedW; x++) {
                    int rgb = resized.getRGB(x, y);
                    int val;
                    switch (c) {
                        case 0 -> val = (rgb >> 16) & 0xFF;
                        case 1 -> val = (rgb >> 8) & 0xFF;
                        default -> val = rgb & 0xFF;
                    }
                    data[c * IMG_HEIGHT * IMG_MAX_WIDTH + y * IMG_MAX_WIDTH + x] =
                            (val / 255.0f - MEAN[c]) / STD[c];
                }
            }
        }
        return data;
    }

    public int getInputWidth() {
        return IMG_MAX_WIDTH;
    }

    public int getInputHeight() {
        return IMG_HEIGHT;
    }
}