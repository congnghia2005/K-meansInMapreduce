import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Benchmark: Bộ công cụ đo kiểm và đánh giá hiệu năng song song.
 *
 * Chức năng:
 * 1. Chạy thuật toán tuần tự (Baseline) làm mốc đo lường T_tuần_tự.
 * 2. Tự động kiểm thử thuật toán song song với các cấu hình số luồng:
 *    1, 2, 4, 8,... đến số nhân CPU thực tế của máy.
 * 3. Cho phép lặp lại mỗi cấu hình nhiều lần (repetitions) để lấy trung bình,
 *    loại trừ sai số do bộ đệm hệ điều hành (OS cache) và JVM JIT Warm-up.
 * 4. Tính toán Speedup = T_tuần_tự / T_song_song và Hiệu suất = Speedup / Số_luồng.
 * 5. Tự động đối soát tính đúng đắn (Verification): kiểm tra tâm cụm của bản song song
 *    có trùng khớp với bản tuần tự (trong ngưỡng sai số epsilon) hay không.
 * 6. Xuất toàn bộ bảng kết quả ra file results.csv để phục vụ vẽ đồ thị trong báo cáo.
 */
public class Benchmark {
    private final File file;
    private final int k;
    private final int dim;
    private final int skipCols;
    private final int maxIter;
    private final double eps;
    private final int splits;
    private final int repetitions;
    private final double[][] initialCentroids;

    public static class Entry {
        public final String configName;
        public final int threads;
        public final double avgTimeMs;
        public final double minTimeMs;
        public final double speedup;
        public final double efficiency;
        public final double sse;
        public final boolean verified;
        public final double maxCentroidDiff;

        public Entry(String configName, int threads, double avgTimeMs, double minTimeMs,
                     double speedup, double efficiency, double sse, boolean verified, double maxCentroidDiff) {
            this.configName = configName;
            this.threads = threads;
            this.avgTimeMs = avgTimeMs;
            this.minTimeMs = minTimeMs;
            this.speedup = speedup;
            this.efficiency = efficiency;
            this.sse = sse;
            this.verified = verified;
            this.maxCentroidDiff = maxCentroidDiff;
        }
    }

    public Benchmark(File file, int k, int dim, int skipCols, int maxIter, double eps,
                     int splits, int repetitions, double[][] initialCentroids) {
        this.file = file;
        this.k = k;
        this.dim = dim;
        this.skipCols = skipCols;
        this.maxIter = maxIter;
        this.eps = eps;
        this.splits = splits;
        this.repetitions = repetitions;
        this.initialCentroids = initialCentroids;
    }

    public void run() throws Exception {
        System.out.println("================================================================================");
        System.out.println("                       BẮT ĐẦU CHẾ ĐỘ BENCHMARK HIỆU NĂNG                       ");
        System.out.println("================================================================================");
        System.out.printf("Tệp dữ liệu      : %s (%.2f MB)%n", file.getName(), file.length() / (1024.0 * 1024.0));
        System.out.printf("Cấu hình K-means : K = %d, Dim = %d, MaxIter = %d, Eps = %e%n", k, dim, maxIter, eps);
        System.out.printf("Số split cố định : %d%n", splits);
        System.out.printf("Số lần lặp đo    : %d lần / cấu hình%n", repetitions);
        System.out.println("--------------------------------------------------------------------------------");

        List<Entry> benchmarkResults = new ArrayList<>();

        // 1. Chạy phiên bản Tuần tự (Baseline)
        System.out.println("\n>>> [1/2] Đang đo phiên bản TUẦN TỰ (Sequential Baseline)...");
        double seqTotalTime = 0.0;
        double seqMinTime = Double.MAX_VALUE;
        SequentialKMeans.Result lastSeqResult = null;

        for (int r = 1; r <= repetitions; r++) {
            System.out.printf("  - Lần chạy %d/%d: ", r, repetitions);
            SequentialKMeans seqKMeans = new SequentialKMeans(file, k, dim, skipCols, maxIter, eps);
            lastSeqResult = seqKMeans.run(initialCentroids, false);
            seqTotalTime += lastSeqResult.totalElapsedMs;
            if (lastSeqResult.totalElapsedMs < seqMinTime) {
                seqMinTime = lastSeqResult.totalElapsedMs;
            }
            System.out.printf("%d ms (Hội tụ sau %d vòng, SSE = %.4f)%n",
                    lastSeqResult.totalElapsedMs, lastSeqResult.iterations, lastSeqResult.totalSSE);
        }

        double seqAvgTime = seqTotalTime / repetitions;
        benchmarkResults.add(new Entry("Tuần tự (Baseline)", 1, seqAvgTime, seqMinTime, 1.0, 1.0,
                lastSeqResult.totalSSE, true, 0.0));

        // 2. Chạy phiên bản Song song với các số luồng khác nhau
        int maxProcessors = Runtime.getRuntime().availableProcessors();
        List<Integer> threadCounts = new ArrayList<>();
        int t = 1;
        while (t <= maxProcessors) {
            threadCounts.add(t);
            t *= 2;
        }
        if (!threadCounts.contains(maxProcessors)) {
            threadCounts.add(maxProcessors);
        }

        System.out.println("\n>>> [2/2] Đang đo phiên bản SONG SONG (Parallel MapReduce)...");
        for (int numThreads : threadCounts) {
            System.out.printf("%n=== Kiểm thử với %d Luồng (Threads) ===%n", numThreads);
            double parTotalTime = 0.0;
            double parMinTime = Double.MAX_VALUE;
            KMeansMR.ParallelResult lastParResult = null;

            for (int r = 1; r <= repetitions; r++) {
                System.out.printf("  - Lần chạy %d/%d: ", r, repetitions);
                lastParResult = KMeansMR.executeParallel(file, k, dim, skipCols, maxIter, eps,
                        numThreads, splits, initialCentroids, false);
                parTotalTime += lastParResult.totalElapsedMs;
                if (lastParResult.totalElapsedMs < parMinTime) {
                    parMinTime = lastParResult.totalElapsedMs;
                }
                System.out.printf("%d ms (Hội tụ sau %d vòng, SSE = %.4f)%n",
                        lastParResult.totalElapsedMs, lastParResult.iterations, lastParResult.totalSSE);
            }

            double parAvgTime = parTotalTime / repetitions;
            double speedup = seqAvgTime / parAvgTime;
            double efficiency = speedup / numThreads;

            // Kiểm tra tính đúng đắn so với bản tuần tự
            double maxDiff = calculateMaxCentroidDiff(lastSeqResult.centroids, lastParResult.centroids, k, dim);
            boolean verified = maxDiff < 1e-3;

            benchmarkResults.add(new Entry("Song song (" + numThreads + " luồng)", numThreads,
                    parAvgTime, parMinTime, speedup, efficiency, lastParResult.totalSSE, verified, maxDiff));
        }

        // 3. In bảng tổng kết hiệu năng
        printSummaryTable(benchmarkResults, seqAvgTime);

        // 4. Xuất file kết quả CSV
        exportToCsv("results.csv", benchmarkResults);
    }

    private double calculateMaxCentroidDiff(double[][] c1, double[][] c2, int k, int dim) {
        double maxDist = 0.0;
        for (int i = 0; i < k; i++) {
            double distSq = 0.0;
            for (int d = 0; d < dim; d++) {
                double diff = c1[i][d] - c2[i][d];
                distSq += diff * diff;
            }
            double dist = Math.sqrt(distSq);
            if (dist > maxDist) {
                maxDist = dist;
            }
        }
        return maxDist;
    }

    private void printSummaryTable(List<Entry> results, double seqBaselineTime) {
        System.out.println("\n=========================================================================================");
        System.out.println("                         BẢNG TỔNG HỢP HIỆU NĂNG VÀ SPEEDUP                             ");
        System.out.println("=========================================================================================");
        System.out.printf("%-20s | %8s | %13s | %13s | %8s | %10s | %10s%n",
                "Cấu hình", "Số luồng", "T_trung_bình", "T_nhỏ_nhất", "Speedup", "Hiệu suất", "Kiểm tra");
        System.out.println("-----------------------------------------------------------------------------------------");

        for (Entry e : results) {
            String checkStr = e.verified ? "OK (Khớp)" : String.format("Lệch %.4f", e.maxCentroidDiff);
            System.out.printf("%-20s | %8d | %10.1f ms | %10.1f ms | %7.2fx | %9.1f%% | %10s%n",
                    e.configName, e.threads, e.avgTimeMs, e.minTimeMs, e.speedup, e.efficiency * 100.0, checkStr);
        }
        System.out.println("=========================================================================================\n");
    }

    private void exportToCsv(String filePath, List<Entry> results) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            bw.write("Configuration,Threads,AvgTimeMs,MinTimeMs,Speedup,EfficiencyPercent,SSE,Verified,MaxCentroidDiff\n");
            for (Entry e : results) {
                bw.write(String.format(java.util.Locale.US,
                        "\"%s\",%d,%.2f,%.2f,%.4f,%.2f,%.4f,%b,%.6f\n",
                        e.configName, e.threads, e.avgTimeMs, e.minTimeMs, e.speedup,
                        e.efficiency * 100.0, e.sse, e.verified, e.maxCentroidDiff));
            }
            System.out.printf(">>> Đã xuất kết quả đo kiểm ra tệp: %s%n", filePath);
        } catch (IOException e) {
            System.err.println("Lỗi khi ghi tệp kết quả CSV: " + e.getMessage());
        }
    }
}

