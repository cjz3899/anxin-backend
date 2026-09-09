package com.anxin.ocr.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;


/**
 * 图片工具类。
 */
public class ImageUtils {

    public static BufferedImage readImage(byte[] data) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(data));
    }

    public static BufferedImage fourPointTransform(BufferedImage src, List<Point2D.Float> points) {
        Point2D.Float tl = points.get(0);
        Point2D.Float tr = points.get(1);
        Point2D.Float br = points.get(2);
        Point2D.Float bl = points.get(3);

        double widthTop = tl.distance(tr);
        double widthBottom = bl.distance(br);
        int maxWidth = (int) Math.round(Math.max(widthTop, widthBottom));

        double heightLeft = tl.distance(bl);
        double heightRight = tr.distance(br);
        int maxHeight = (int) Math.round(Math.max(heightLeft, heightRight));

        if (maxWidth <= 0) maxWidth = 1;
        if (maxHeight <= 0) maxHeight = 1;

        Point2D.Float[] srcPoints = {tl, tr, br, bl};
        Point2D.Float[] dstPoints = {
                new Point2D.Float(0, 0),
                new Point2D.Float(maxWidth, 0),
                new Point2D.Float(maxWidth, maxHeight),
                new Point2D.Float(0, maxHeight)
        };

        return perspectiveTransform(src, srcPoints, dstPoints, maxWidth, maxHeight);
    }

    private static BufferedImage perspectiveTransform(BufferedImage src,
                                                      Point2D.Float[] srcPts, Point2D.Float[] dstPts,
                                                      int width, int height) {
        double[][] matrix = computeHomographyMatrix(srcPts, dstPts);
        double[][] invMatrix = invertMatrix3x3(matrix);

        BufferedImage dst = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int srcW = src.getWidth();
        int srcH = src.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double denom = invMatrix[2][0] * x + invMatrix[2][1] * y + invMatrix[2][2];
                double srcX = (invMatrix[0][0] * x + invMatrix[0][1] * y + invMatrix[0][2]) / denom;
                double srcY = (invMatrix[1][0] * x + invMatrix[1][1] * y + invMatrix[1][2]) / denom;

                int sx = (int) Math.round(srcX);
                int sy = (int) Math.round(srcY);

                if (sx >= 0 && sx < srcW && sy >= 0 && sy < srcH) {
                    dst.setRGB(x, y, src.getRGB(sx, sy));
                }
            }
        }
        return dst;
    }

    private static double[][] computeHomographyMatrix(Point2D.Float[] src, Point2D.Float[] dst) {
        double[][] A = new double[8][8];
        double[] b = new double[8];

        for (int i = 0; i < 4; i++) {
            double sx = src[i].x, sy = src[i].y;
            double dx = dst[i].x, dy = dst[i].y;
            A[2 * i][0] = sx;
            A[2 * i][1] = sy;
            A[2 * i][2] = 1;
            A[2 * i][6] = -dx * sx;
            A[2 * i][7] = -dx * sy;
            b[2 * i] = dx;

            A[2 * i + 1][3] = sx;
            A[2 * i + 1][4] = sy;
            A[2 * i + 1][5] = 1;
            A[2 * i + 1][6] = -dy * sx;
            A[2 * i + 1][7] = -dy * sy;
            b[2 * i + 1] = dy;
        }

        double[] h = gaussianSolve(A, b);

        return new double[][]{
                {h[0], h[1], h[2]},
                {h[3], h[4], h[5]},
                {h[6], h[7], 1.0}
        };
    }

    private static double[] gaussianSolve(double[][] A, double[] b) {
        int n = b.length;
        double[][] aug = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, aug[i], 0, n);
            aug[i][n] = b[i];
        }

        for (int col = 0; col < n; col++) {
            int maxRow = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(aug[row][col]) > Math.abs(aug[maxRow][col])) {
                    maxRow = row;
                }
            }
            double[] tmp = aug[col];
            aug[col] = aug[maxRow];
            aug[maxRow] = tmp;

            double pivot = aug[col][col];
            if (Math.abs(pivot) < 1e-10) continue;

            for (int j = col; j <= n; j++) {
                aug[col][j] /= pivot;
            }

            for (int row = 0; row < n; row++) {
                if (row == col) continue;
                double factor = aug[row][col];
                for (int j = col; j <= n; j++) {
                    aug[row][j] -= factor * aug[col][j];
                }
            }
        }

        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = aug[i][n];
        }
        return x;
    }

    private static double[][] invertMatrix3x3(double[][] m) {
        double a = m[0][0], b = m[0][1], c = m[0][2];
        double d = m[1][0], e = m[1][1], f = m[1][2];
        double g = m[2][0], h = m[2][1], k = m[2][2];

        double det = a * (e * k - f * h) - b * (d * k - f * g) + c * (d * h - e * g);
        double invDet = 1.0 / det;

        return new double[][]{
                {(e * k - f * h) * invDet, (c * h - b * k) * invDet, (b * f - c * e) * invDet},
                {(f * g - d * k) * invDet, (a * k - c * g) * invDet, (c * d - a * f) * invDet},
                {(d * h - e * g) * invDet, (b * g - a * h) * invDet, (a * e - b * d) * invDet}
        };
    }
}