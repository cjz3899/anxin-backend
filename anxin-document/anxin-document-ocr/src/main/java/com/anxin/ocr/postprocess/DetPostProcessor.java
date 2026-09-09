package com.anxin.ocr.postprocess;

import com.anxin.ocr.model.TextBox;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;


/**
 *  文本检测后处理类
 */
public class DetPostProcessor {

    private final float threshold;
    private final float unclipRatio;

    public DetPostProcessor(float threshold, float unclipRatio) {
        this.threshold = threshold;
        this.unclipRatio = unclipRatio;
    }

    public List<TextBox> process(float[] output, int modelH, int modelW, int origW, int origH) {
        boolean[][] bitmap = new boolean[modelH][modelW];

        for (int y = 0; y < modelH; y++) {
            for (int x = 0; x < modelW; x++) {
                float val = output[y * modelW + x];
                if (val > threshold) {
                    bitmap[y][x] = true;
                }
            }
        }

        List<List<Point2D.Float>> connectedComponents = findConnectedComponents(bitmap, modelH, modelW);

        List<TextBox> boxes = new ArrayList<>();
        float scaleX = (float) origW / modelW;
        float scaleY = (float) origH / modelH;

        for (List<Point2D.Float> component : connectedComponents) {
            Rectangle2D.Float rect = boundingRect(component);

            float area = rect.width * rect.height;
            float perimeter = 2 * (rect.width + rect.height);
            float distance = perimeter > 0 ? area * unclipRatio / perimeter : 0;

            float x1 = Math.max(0, rect.x - distance);
            float y1 = Math.max(0, rect.y - distance);
            float x2 = Math.min(modelW, rect.x + rect.width + distance);
            float y2 = Math.min(modelH, rect.y + rect.height + distance);

            List<Point2D.Float> points = List.of(
                    new Point2D.Float(x1 * scaleX, y1 * scaleY),
                    new Point2D.Float(x2 * scaleX, y1 * scaleY),
                    new Point2D.Float(x2 * scaleX, y2 * scaleY),
                    new Point2D.Float(x1 * scaleX, y2 * scaleY)
            );

            boxes.add(new TextBox(points, 1.0f));
        }

        boxes.sort(Comparator.comparingDouble((TextBox b) ->
                        Math.floor(b.getPoints().get(0).y / 20) * 20)
                .thenComparingDouble(b -> b.getPoints().get(0).x));

        return boxes;
    }

    private List<List<Point2D.Float>> findConnectedComponents(boolean[][] bitmap, int h, int w) {
        boolean[][] visited = new boolean[h][w];
        List<List<Point2D.Float>> components = new ArrayList<>();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (bitmap[y][x] && !visited[y][x]) {
                    List<Point2D.Float> component = new ArrayList<>();
                    floodFill(bitmap, visited, x, y, h, w, component);
                    if (component.size() > 10) {
                        components.add(component);
                    }
                }
            }
        }
        return components;
    }

    private void floodFill(boolean[][] bitmap, boolean[][] visited,
                           int startX, int startY, int h, int w, List<Point2D.Float> component) {
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});

        while (!stack.isEmpty()) {
            int[] pos = stack.pop();
            int x = pos[0];
            int y = pos[1];

            if (x < 0 || x >= w || y < 0 || y >= h) continue;
            if (visited[y][x] || !bitmap[y][x]) continue;

            visited[y][x] = true;
            component.add(new Point2D.Float(x, y));

            stack.push(new int[]{x + 1, y});
            stack.push(new int[]{x - 1, y});
            stack.push(new int[]{x, y + 1});
            stack.push(new int[]{x, y - 1});
        }
    }

    private Rectangle2D.Float boundingRect(List<Point2D.Float> points) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE;
        for (Point2D.Float p : points) {
            minX = Math.min(minX, p.x);
            minY = Math.min(minY, p.y);
            maxX = Math.max(maxX, p.x);
            maxY = Math.max(maxY, p.y);
        }
        return new Rectangle2D.Float(minX, minY, maxX - minX, maxY - minY);
    }
}
