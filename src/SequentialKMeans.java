import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SequentialKMeans: Phiên bản phân cụm K-means tuần tự (Baseline).
 * Chạy trên 1 luồng duy nhất, đọc tuần tự từng dòng trong file,
 * cập nhật tâm sau mỗi vòng lặp.
 *
 * Dùng làm đối chứng chuẩn (ground truth về hiệu năng T_tuần_tự)
 * để tính toán Speedup và Efficiency cho phiên bản song song.
 */
public class SequentialKMeans {
    private final File file;
    private final int k;
    private final int dim;
    private final int skipCols;
    private final int maxIter;
    private final double eps;

    public static class Result {
        public final double[][] centroids;
        public final long[] clusterCounts;
        public final double totalSSE;
        public final int iterations;
        public final long totalElapsedMs;
        public final List<Long> iterationTimesMs;

        public Result(double[][] centroids, long[] clusterCounts, double totalSSE,
                      int iterations, long totalElapsedMs, List<Long> iterationTimesMs) {
            this.centroids = centroids;
            this.clusterCounts = clusterCounts;
            this.totalSSE = totalSSE;
            this.iterations = iterations;
            this.totalElapsedMs = totalElapsedMs;
            this.iterationTimesMs = iterationTimesMs;
        }
    }

    public SequentialKMeans(File file, int k, int dim, int skipCols, int maxIter, double eps) {
        this.file = file;
        this.k = k;
        this.dim = dim;
        this.skipCols = skipCols;
        this.maxIter = maxIter;
        this.eps = eps;
    }

    public Result run(double[][] initialCentroids, boolean verbose) throws IOException {
        double[][] currentCentroids = new double[k][dim];
        for (int i = 0; i < k; i++) {
            System.arraycopy(initialCentroids[i], 0, currentCentroids[i], 0, dim);
        }

        double[] pointBuffer = new double[dim];
        List<Long> iterationTimes = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        long[] finalCounts = new long[k];
        double finalSSE = 0.0;
        int iter = 0;

        while (iter < maxIter) {
            iter++;
            long iterStart = System.currentTimeMillis();

            double[][] clusterSums = new double[k][dim];
            long[] clusterCounts = new long[k];
            double sse = 0.0;

            // Đọc tuần tự toàn bộ file từ đầu đến cuối
            try (BufferedReader br = new BufferedReader(new FileReader(file), 65536)) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }

                    if (!MapTask.parseCsvLine(line, pointBuffer, dim, skipCols)) {
                        continue;
                    }

                    // Tìm tâm gần nhất
                    int bestC = -1;
                    double minDistSq = Double.MAX_VALUE;
                    for (int c = 0; c < k; c++) {
                        double dSq = 0.0;
                        double[] center = currentCentroids[c];
                        for (int d = 0; d < dim; d++) {
                            double diff = pointBuffer[d] - center[d];
                            dSq += diff * diff;
                        }
                        if (dSq < minDistSq) {
                            minDistSq = dSq;
                            bestC = c;
                        }
                    }

                    if (bestC != -1) {
                        clusterCounts[bestC]++;
                        sse += minDistSq;
                        double[] sum = clusterSums[bestC];
                        for (int d = 0; d < dim; d++) {
                            sum[d] += pointBuffer[d];
                        }
                    }
                }
            }

            // Tính tâm mới và độ dịch chuyển
            double[][] newCentroids = new double[k][dim];
            double maxShift = 0.0;

            for (int c = 0; c < k; c++) {
                if (clusterCounts[c] > 0) {
                    double shiftSq = 0.0;
                    for (int d = 0; d < dim; d++) {
                        newCentroids[c][d] = clusterSums[c][d] / clusterCounts[c];
                        double diff = newCentroids[c][d] - currentCentroids[c][d];
                        shiftSq += diff * diff;
                    }
                    double shift = Math.sqrt(shiftSq);
                    if (shift > maxShift) {
                        maxShift = shift;
                    }
                } else {
                    // Cụm rỗng: giữ nguyên tâm cũ
                    System.arraycopy(currentCentroids[c], 0, newCentroids[c], 0, dim);
                }
            }

            long iterElapsed = System.currentTimeMillis() - iterStart;
            iterationTimes.add(iterElapsed);

            if (verbose) {
                System.out.printf("[Tuần tự] Vòng %2d | Thời gian: %6d ms | SSE: %14.4f | Shift: %.6f%n",
                        iter, iterElapsed, sse, maxShift);
            }

            currentCentroids = newCentroids;
            finalCounts = clusterCounts;
            finalSSE = sse;

            // Kiểm tra điều kiện hội tụ
            if (maxShift < eps) {
                if (verbose) {
                    System.out.printf("[Tuần tự] Thuật toán hội tụ tại vòng %d (shift = %.6f < eps = %e)%n",
                            iter, maxShift, eps);
                }
                break;
            }
        }

        long totalElapsed = System.currentTimeMillis() - startTime;
        return new Result(currentCentroids, finalCounts, finalSSE, iter, totalElapsed, iterationTimes);
    }
}

