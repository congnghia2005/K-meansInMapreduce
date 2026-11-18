import java.io.File;
import java.util.concurrent.Callable;

public class MapTask implements Callable<PartialResult> {
    private final File file;
    private final Split split;
    private final double[][] centroids;
    private final int dim;
    private final int skipCols;

    public MapTask(File file, Split split, double[][] centroids, int dim, int skipCols) {
        this.file = file;
        this.split = split;
        this.centroids = centroids;
        this.dim = dim;
        this.skipCols = skipCols;
    }

    @Override
    public PartialResult call() throws Exception {
        long startMs = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();

        int k = centroids.length;
        PartialResult partialResult = new PartialResult(split.getSplitIndex(), k, dim);
        double[] pointBuffer = new double[dim];

        try (SplitReader reader = new SplitReader(file, split.getStartByte(), split.getEndByte())) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // Parse dòng CSV thành vector tọa độ
                boolean success = parseCsvLine(line, pointBuffer, dim, skipCols);
                if (!success) {
                    continue;
                }

                // Tìm tâm cụm gần nhất (Squared Euclidean Distance)
                int bestCluster = -1;
                double minDistanceSq = Double.MAX_VALUE;

                for (int c = 0; c < k; c++) {
                    double distSq = 0.0;
                    double[] center = centroids[c];
                    for (int d = 0; d < dim; d++) {
                        double diff = pointBuffer[d] - center[d];
                        distSq += diff * diff;
                    }

                    if (distSq < minDistanceSq) {
                        minDistanceSq = distSq;
                        bestCluster = c;
                    }
                }

                if (bestCluster != -1) {
                    // Tích lũy cục bộ trong bộ nhớ riêng của mapper (Combiner)
                    partialResult.addPoint(bestCluster, pointBuffer, minDistanceSq);
                }
            }
        }

        long endMs = System.currentTimeMillis();
        partialResult.setExecutionMeta(threadName, startMs, endMs);
        return partialResult;
    }

    /**
     * Phân tách dòng CSV số học nhanh và an toàn, bỏ qua các cột đầu (như nhãn/ID).
     */
    public static boolean parseCsvLine(String line, double[] buffer, int dim, int skipCols) {
        int len = line.length();
        int col = 0;
        int targetIdx = 0;
        int tokenStart = 0;

        for (int i = 0; i <= len; i++) {
            if (i == len || line.charAt(i) == ',') {
                if (col >= skipCols && targetIdx < dim) {
                    String token = line.substring(tokenStart, i).trim();
                    if (token.isEmpty()) {
                        return false;
                    }
                    try {
                        buffer[targetIdx++] = Double.parseDouble(token);
                    } catch (NumberFormatException e) {
                        // Bỏ qua dòng tiêu đề (header) nếu gặp ký tự chữ
                        return false;
                    }
                }
                col++;
                tokenStart = i + 1;
            }
        }

        return targetIdx == dim;
    }
}
