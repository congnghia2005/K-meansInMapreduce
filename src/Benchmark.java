import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Benchmark {
    private File file;
    private final int k;
    private final int dim;
    private final int skipCols;
    private final int maxIter;
    private final double eps;
    private final int splits;
    private final int repetitions;
    private double[][] initialCentroids;
    private final long seed;

    public static class Entry {
        public final String datasetLabel;
        public final long points;
        public final String configName;
        public final int threads;
        public final double avgTimeMs;
        public final double stdDevMs;
        public final double minTimeMs;
        public final double speedup;
        public final double efficiency;
        public final double throughputMPointsSec;
        public final double throughputMBSec;
        public final double sse;
        public final boolean verified;
        public final double maxCentroidDiff;

        public Entry(String datasetLabel, long points, String configName, int threads,
                double avgTimeMs, double stdDevMs, double minTimeMs,
                double speedup, double efficiency, double throughputMPointsSec,
                double throughputMBSec, double sse, boolean verified, double maxCentroidDiff) {
            this.datasetLabel = datasetLabel;
            this.points = points;
            this.configName = configName;
            this.threads = threads;
            this.avgTimeMs = avgTimeMs;
            this.stdDevMs = stdDevMs;
            this.minTimeMs = minTimeMs;
            this.speedup = speedup;
            this.efficiency = efficiency;
            this.throughputMPointsSec = throughputMPointsSec;
            this.throughputMBSec = throughputMBSec;
            this.sse = sse;
            this.verified = verified;
            this.maxCentroidDiff = maxCentroidDiff;
        }
    }

    public Benchmark(File file, int k, int dim, int skipCols, int maxIter, double eps,
            int splits, int repetitions, double[][] initialCentroids, long seed) {
        this.file = file;
        this.k = k;
        this.dim = dim;
        this.skipCols = skipCols;
        this.maxIter = maxIter;
        this.eps = eps;
        this.splits = splits;
        this.repetitions = repetitions;
        this.initialCentroids = initialCentroids;
        this.seed = seed;
    }

    /**
     * Chạy đo kiểm trên một tệp đơn lẻ.
     */
    public List<Entry> runSingleFile() throws Exception {
        long totalPoints = KMeansMR.countTotalPointsFast(file);
        String label = formatSizeLabel(totalPoints);
        return runBenchmarkForFile(file, totalPoints, label);
    }

    /**
     * Chạy đo kiểm đa kích thước dữ liệu (ví dụ: --sizes 1M,5M,10M).
     */
    public void runMultiSizes(String sizesParam) throws Exception {
        System.out.println("================================================================================");
        System.out.println("            BẮT ĐẦU CHẾ ĐỘ BENCHMARK ĐA KÍCH THƯỚC DỮ LIỆU (--sizes)            ");
        System.out.println("================================================================================");
        System.out.printf("Danh sách kích thước cần đo : %s%n", sizesParam);
        System.out.printf("Số lần lặp đo mỗi cấu hình  : %d lần%n", repetitions);
        System.out.println("--------------------------------------------------------------------------------");

        String[] sizeTokens = sizesParam.split("[,;]");
        List<Entry> allResults = new ArrayList<>();

        for (String token : sizeTokens) {
            token = token.trim();
            if (token.isEmpty())
                continue;

            long points = parsePointsCount(token);
            String sizeLabel = token.toUpperCase();
            String targetFileName = "points_" + sizeLabel + ".csv";
            File targetFile = new File(targetFileName);

            // Tự động sinh dữ liệu nếu chưa tồn tại
            if (!targetFile.exists() || targetFile.length() == 0) {
                System.out.printf("%n>>> Tệp '%s' chưa tồn tại. Đang tự động sinh %,d điểm...%n", targetFileName,
                        points);
                DataGenerator.main(new String[] {
                        "--output", targetFileName,
                        "--n", String.valueOf(points),
                        "--dim", String.valueOf(dim),
                        "--k", String.valueOf(k),
                        "--stddev", "1.5",
                        "--seed", String.valueOf(seed)
                });
            }

            long actualPoints = KMeansMR.countTotalPointsFast(targetFile);
            System.out.printf("%n################################################################################%n");
            System.out.printf("       TIẾN HÀNH ĐO KIỂM VỚI TẬP DỮ LIỆU: %s (%,d điểm, %.2f MB)%n",
                    targetFileName, actualPoints, targetFile.length() / (1024.0 * 1024.0));
            System.out.printf("################################################################################%n");

            // Khởi tạo tâm ban đầu cho file này
            double[][] centroids = KMeansMR.sampleInitialCentroids(targetFile, k, dim, skipCols, seed);
            this.file = targetFile;
            this.initialCentroids = centroids;

            List<Entry> resultsForSize = runBenchmarkForFile(targetFile, actualPoints, sizeLabel);
            allResults.addAll(resultsForSize);
        }

        // Xuất toàn bộ kết quả vào results.csv
        exportToCsv("results.csv", allResults);
    }

    private List<Entry> runBenchmarkForFile(File targetFile, long totalPoints, String sizeLabel) throws Exception {
        double fileSizeMB = targetFile.length() / (1024.0 * 1024.0);
        List<Entry> entries = new ArrayList<>();

        // 1. Chạy phiên bản Tuần tự (Sequential Baseline)
        System.out.printf("%n>>> [1/2] Đang đo phiên bản TUẦN TỰ (Sequential Baseline) trên %s...%n",
                targetFile.getName());
        List<Long> seqTimes = new ArrayList<>();
        double seqMinTime = Double.MAX_VALUE;
        SequentialKMeans.Result lastSeqResult = null;

        for (int r = 1; r <= repetitions; r++) {
            System.out.printf("  - Lần chạy %d/%d: ", r, repetitions);
            SequentialKMeans seqKMeans = new SequentialKMeans(targetFile, k, dim, skipCols, maxIter, eps);
            lastSeqResult = seqKMeans.run(initialCentroids, false);
            seqTimes.add(lastSeqResult.totalElapsedMs);
            if (lastSeqResult.totalElapsedMs < seqMinTime) {
                seqMinTime = lastSeqResult.totalElapsedMs;
            }
            System.out.printf("%d ms (%s, SSE = %.4f)%n",
                    lastSeqResult.totalElapsedMs, lastSeqResult.stopReason, lastSeqResult.totalSSE);
        }

        double seqAvgTime = calculateAverage(seqTimes);
        double seqStdDev = calculateStdDev(seqTimes, seqAvgTime);
        double seqThroughputPoints = (totalPoints / (seqAvgTime / 1000.0)) / 1_000_000.0;
        double seqThroughputMB = fileSizeMB / (seqAvgTime / 1000.0);

        entries.add(new Entry(sizeLabel, totalPoints, "Tuần tự (Baseline)", 1, seqAvgTime,
                seqStdDev, seqMinTime, 1.0, 1.0, seqThroughputPoints, seqThroughputMB,
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
            List<Long> parTimes = new ArrayList<>();
            double parMinTime = Double.MAX_VALUE;
            KMeansMR.ParallelResult lastParResult = null;

            for (int r = 1; r <= repetitions; r++) {
                System.out.printf("  - Lần chạy %d/%d: ", r, repetitions);
                lastParResult = KMeansMR.executeParallel(targetFile, k, dim, skipCols, maxIter, eps,
                        numThreads, splits, initialCentroids, false);
                parTimes.add(lastParResult.totalElapsedMs);
                if (lastParResult.totalElapsedMs < parMinTime) {
                    parMinTime = lastParResult.totalElapsedMs;
                }
                System.out.printf("%d ms (%s, SSE = %.4f)%n",
                        lastParResult.totalElapsedMs, lastParResult.stopReason, lastParResult.totalSSE);
            }

            double parAvgTime = calculateAverage(parTimes);
            double parStdDev = calculateStdDev(parTimes, parAvgTime);
            double speedup = seqAvgTime / parAvgTime;
            double efficiency = speedup / numThreads;
            double parThroughputPoints = (totalPoints / (parAvgTime / 1000.0)) / 1_000_000.0;
            double parThroughputMB = fileSizeMB / (parAvgTime / 1000.0);

            // Kiểm tra tính đúng đắn so với bản tuần tự
            double maxDiff = calculateMaxCentroidDiff(lastSeqResult.centroids, lastParResult.centroids, k, dim);
            boolean verified = maxDiff < 1e-3;

            // In rõ kết quả kiểm tra tính đúng đắn theo yêu cầu
            System.out.printf("  -> Kiểm tra song song vs tuần tự: %s (sai lệch tối đa = %.6f)%n",
                    verified ? "PASS" : "FAIL", maxDiff);

            entries.add(new Entry(sizeLabel, totalPoints, "Song song (" + numThreads + " luồng)", numThreads,
                    parAvgTime, parStdDev, parMinTime, speedup, efficiency, parThroughputPoints, parThroughputMB,
                    lastParResult.totalSSE, verified, maxDiff));
        }

        // In bảng tổng kết của tệp này
        printSummaryTable(entries, seqAvgTime);

        // Xuất ra results.csv
        exportToCsv("results.csv", entries);
        return entries;
    }

    private double calculateAverage(List<Long> values) {
        if (values.isEmpty())
            return 0.0;
        double sum = 0.0;
        for (long v : values)
            sum += v;
        return sum / values.size();
    }

    private double calculateStdDev(List<Long> values, double avg) {
        if (values.size() <= 1)
            return 0.0;
        double sumSq = 0.0;
        for (long v : values) {
            double diff = v - avg;
            sumSq += diff * diff;
        }
        return Math.sqrt(sumSq / (values.size() - 1));
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
        System.out.println(
                "\n========================================================================================================================");
        System.out.println(
                "                                       BẢNG TỔNG HỢP HIỆU NĂNG VÀ THÔNG LƯỢNG                                           ");
        System.out.println(
                "========================================================================================================================");
        System.out.printf("%-18s | %6s | %11s | %10s | %7s | %9s | %14s | %11s | %10s%n",
                "Cấu hình", "Luồng", "Thời gian", "±Độ lệch", "Speedup", "Hiệu suất", "M điểm/giây", "Băng thông",
                "Kiểm tra");
        System.out.println(
                "------------------------------------------------------------------------------------------------------------------------");

        for (Entry e : results) {
            String checkStr = e.verified ? "PASS" : "FAIL";
            System.out.printf("%-18s | %6d | %8.1f ms | %7.1f ms | %6.2fx | %8.1f%% | %10.2f M/s | %8.1f MB/s | %10s%n",
                    e.configName, e.threads, e.avgTimeMs, e.stdDevMs, e.speedup, e.efficiency * 100.0,
                    e.throughputMPointsSec, e.throughputMBSec, checkStr);
        }
        System.out.println(
                "========================================================================================================================\n");
    }

    private void exportToCsv(String filePath, List<Entry> results) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            bw.write(
                    "DatasetSize,Points,Configuration,Threads,AvgTimeMs,StdDevMs,MinTimeMs,Speedup,EfficiencyPercent,ThroughputMPointsSec,ThroughputMBSec,SSE,Verified,MaxCentroidDiff\n");
            for (Entry e : results) {
                bw.write(String.format(java.util.Locale.US,
                        "\"%s\",%d,\"%s\",%d,%.2f,%.2f,%.2f,%.4f,%.2f,%.4f,%.2f,%.4f,%b,%.6f\n",
                        e.datasetLabel, e.points, e.configName, e.threads, e.avgTimeMs, e.stdDevMs,
                        e.minTimeMs, e.speedup, e.efficiency * 100.0, e.throughputMPointsSec,
                        e.throughputMBSec, e.sse, e.verified, e.maxCentroidDiff));
            }
            System.out.printf(">>> Đã lưu kết quả đo kiểm ra tệp: %s%n", filePath);
        } catch (IOException e) {
            System.err.println("Lỗi khi ghi tệp kết quả CSV: " + e.getMessage());
        }
    }

    private long parsePointsCount(String token) {
        token = token.trim().toUpperCase();
        if (token.endsWith("M")) {
            double val = Double.parseDouble(token.substring(0, token.length() - 1));
            return (long) (val * 1_000_000L);
        } else if (token.endsWith("K")) {
            double val = Double.parseDouble(token.substring(0, token.length() - 1));
            return (long) (val * 1_000L);
        }
        return Long.parseLong(token);
    }

    private String formatSizeLabel(long points) {
        if (points >= 1_000_000) {
            return String.format(java.util.Locale.US, "%.1fM", points / 1_000_000.0);
        } else if (points >= 1_000) {
            return String.format(java.util.Locale.US, "%.0fK", points / 1_000.0);
        }
        return String.valueOf(points);
    }
}
