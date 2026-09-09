package com.anxin.ocr.postprocess;

import java.util.ArrayList;
import java.util.List;


/**
 * CTC 解码
 * 把识别模型输出序列还原成字符索引
 */
public class CtcDecoder {

    /**
     * PP-OCRv6 CTC解码: blank token在最后一个index (vocabSize-1)
     */
    public List<Integer> decode(float[] output, int seqLen, int vocabSize) {
        int blankIndex = vocabSize - 1;

        List<Integer> indices = new ArrayList<>();

        for (int t = 0; t < seqLen; t++) {
            int maxIdx = 0;
            float maxVal = Float.NEGATIVE_INFINITY;
            for (int v = 0; v < vocabSize; v++) {
                float val = output[t * vocabSize + v];
                if (val > maxVal) {
                    maxVal = val;
                    maxIdx = v;
                }
            }
            indices.add(maxIdx);
        }

        List<Integer> result = new ArrayList<>();
        Integer prev = null;
        for (int idx : indices) {
            if (idx == blankIndex) {
                prev = idx;
                continue;
            }
            if (prev != null && prev == idx) {
                continue;
            }
            prev = idx;
            result.add(idx);
        }
        return result;
    }
}
