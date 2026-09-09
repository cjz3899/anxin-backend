package com.anxin.ocr.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.awt.geom.Point2D;
import java.util.List;


/**
 * 文本框模型。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TextBox {
    private List<Point2D.Float> points;
    private float score;
}