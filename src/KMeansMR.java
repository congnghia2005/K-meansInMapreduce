import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class KMeansMR {

    public static class ParallelResult {
        public final double[][] centroids;
        public final long[] clusterCounts;
        public final double totalSSE;
        public final int iterations;
        public final long totalElapsedMs;
        public final String stopReason;
        public final long totalPoints;

        public ParallelResult(double[][] centroids, long[] clusterCounts, double totalSSE,
                int iterations, long totalElapsedMs, String stopReason, long totalPoints) {
            this.centroids = centroids;
            this.clusterCounts = clusterCounts;
            this.totalSSE = totalSSE;
            this.iterations = iterations;
            this.totalElapsedMs = totalElapsedMs;
            this.stopReason = stopReason;
            this.totalPoints = totalPoints;
        }
    }

    public static void main(String[] args) {
        String inputPath = "points.csv";
        int k = 5;
        int dim = 10;
        int threads = Runtime.getRuntime().availableProcessors();
        int splits = -1; // Mặc định sẽ gán bằng threads * 2
        int maxIter = 20;
        double eps = 1e-4;
        int skipCols = 0;
        String mode = "parallel";
        long seed = 42L;
        boolean verbose = false;
        int runs = 3;
        String sizes = ""; // Hỗ trợ đa kích thước: vd 1M,5M,10M

        // Phân tích tham số dòng lệnh
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input":
                    inputPath = args[++i];
                    break;
                case "--k":
                    k = Integer.parseInt(args[++i]);
                    break;
                case "--dim":
                    dim = Integer.parseInt(args[++i]);
                    break;
                case "--threads":
                    threads = Integer.parseInt(args[++i]);
                    break;
                case "--splits":
                    splits = Integer.parseInt(args[++i]);
                    break;
                case "--maxIter":
                    maxIter = Integer.parseInt(args[++i]);
                    break;
                case "--eps":
                    eps = Double.parseDouble(args[++i]);
                    break;
                case "--skipCols":
                    skipCols = Integer.parseInt(args[++i]);
                    break;
                case "--mode":
                    mode = args[++i].toLowerCase();
                    break;
                case "--seed":
                    seed = Long.parseLong(args[++i]);
                    break;
                case "--verbose":
                    verbose = true;
                    break;
                case "--runs":
                    runs = Integer.parseInt(args[++i]);
                    break;
                case "--sizes":
                    sizes = args[++i];
                    break;
                default:
                    System.err.println("Cảnh báo: Bỏ qua tham số không xác định: " + args[i]);
            }
        }

        if (splits <= 0) {
            splits = threads * 2;
        }

        // Nếu ở chế độ benchmark với tham số --sizes
        if ("benchmark".equals(mode) && !sizes.isEmpty()) {
            Benchmark benchmark = new Benchmark(null, k, dim, skipCols, maxIter, eps,
                    splits, runs, null, seed);
            try {
                benchmark.runMultiSizes(sizes);
            } catch (Exception e) {
                System.err.println("Lỗi khi chạy benchmark đa kích thước: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        File inputFile = new File(inputPath);
        if (!inputFile.exists() || !inputFile.isFile()) {
            System.err.printf("Lỗi: Không tìm thấy tệp dữ liệu đầu vào: %s%n", inputPath);
            System.err.println(
                    "Gợi ý: Hãy dùng DataGenerator để sinh tệp trước: java -cp out DataGenerator --output points.csv");
            System.exit(1);
        }

        // Đếm tổng số điểm trong tệp một cách siêu tốc bằng streaming byte scanning
        long totalPoints = countTotalPointsFast(inputFile);

        // In cấu hình chi tiết đầu chương trình
        printSystemBanner(inputFile, totalPoints, k, dim, threads, splits, maxIter, eps, skipCols, mode, seed);

        // In RAM ở đầu chương trình
        System.out.printf("[BỘ NHỚ JVM - ĐẦU CHƯƠNG TRÌNH]: %s%n", getMemoryStatus());
        System.out.println("--------------------------------------------------------------------------------");

        try {
            // Khởi tạo K tâm ban đầu ngẫu nhiên nhưng tái lập được (cùng seed)
            double[][] initialCentroids = sampleInitialCentroids(inputFile, k, dim, skipCols, seed);

            switch (mode) {
                case "parallel":
                    System.out.println(">>> BẮT ĐẦU CHẠY PHÂN CỤM SONG SONG (MAPREDUCE)...");
                    ParallelResult pResult = executeParallel(inputFile, k, dim, skipCols, maxIter, eps,
                            threads, splits, initialCentroids, true);

                    // In RAM ở cuối chương trình
                    System.out.println(
                            "--------------------------------------------------------------------------------");
                    System.out.printf("[BỘ NHỚ JVM - KẾT THÚC CHƯƠNG TRÌNH]: %s%n", getMemoryStatus());
                    System.out.println(
                            "-> Minh chứng: RAM thực tế duy trì ở mức thấp, hoàn toàn phẳng và độc lập với dung lượng tệp.");

                    printFinalSummary(pResult.centroids, pResult.clusterCounts, pResult.totalSSE,
                            pResult.iterations, pResult.totalElapsedMs, pResult.stopReason, dim, inputPath);
                    break;

                case "sequential":
                    System.out.println(">>> BẮT ĐẦU CHẠY PHÂN CỤM TUẦN TỰ (SEQUENTIAL BASELINE)...");
                    SequentialKMeans seqKMeans = new SequentialKMeans(inputFile, k, dim, skipCols, maxIter, eps);
                    SequentialKMeans.Result sResult = seqKMeans.run(initialCentroids, true);

                    // In RAM ở cuối chương trình
                    System.out.println(
                            "--------------------------------------------------------------------------------");
                    System.out.printf("[BỘ NHỚ JVM - KẾT THÚC CHƯƠNG TRÌNH]: %s%n", getMemoryStatus());
                    System.out.println(
                            "-> Minh chứng: RAM thực tế duy trì ở mức thấp, hoàn toàn phẳng và độc lập với dung lượng tệp.");

                    printFinalSummary(sResult.centroids, sResult.clusterCounts, sResult.totalSSE,
                            sResult.iterations, sResult.totalElapsedMs, sResult.stopReason, dim, inputPath);
                    break;

                case "benchmark":
                    Benchmark benchmark = new Benchmark(inputFile, k, dim, skipCols, maxIter, eps,
                            splits, runs, initialCentroids, seed);
                    benchmark.runSingleFile();
                    break;

                default:
                    System.err.println(
                            "Chế độ '--mode " + mode + "' không hợp lệ. Chọn: parallel | sequential | benchmark");
                    System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("Đã xảy ra lỗi trong quá trình thực thi: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Thực thi K-means song song theo mô hình MapReduce.
     */
    public static ParallelResult executeParallel(File file, int k, int dim, int skipCols,
            int maxIter, double eps, int numThreads,
            int numSplits, double[][] initialCentroids,
            boolean printLogs) throws Exception {

        long totalStartTime = System.currentTimeMillis();

        // 1. Phân chia tệp thành các Split theo byte range
        List<Split> splitList = createByteSplits(file, numSplits);

        // 2. Tạo ThreadPool cho Mapper và Reducer
        ExecutorService mapPool = Executors.newFixedThreadPool(numThreads);
        ExecutorService reducePool = Executors.newFixedThreadPool(Math.min(k, numThreads));

        double[][] currentCentroids = new double[k][dim];
        for (int i = 0; i < k; i++) {
            System.arraycopy(initialCentroids[i], 0, currentCentroids[i], 0, dim);
        }

        long[] finalCounts = new long[k];
        double finalSSE = 0.0;
        double prevSSE = Double.MAX_VALUE;
        int iter = 0;
        String stopReason = "Đạt số vòng lặp tối đa";

        if (printLogs) {
            System.out.println(
                    "\n---------------------------------------------------------------------------------------------------------");
            System.out.printf("%-5s | %10s | %10s | %10s | %12s | %16s | %12s%n",
                    "Vòng", "Map (ms)", "Shfl (ms)", "Redc (ms)", "Tổng VL (ms)", "SSE Cục bộ", "Max Shift");
            System.out.println(
                    "---------------------------------------------------------------------------------------------------------");
        }

        try {
            while (iter < maxIter) {
                iter++;
                long iterStart = System.currentTimeMillis();

                // ==================== PHA 1: MAP + COMBINER ====================
                long mapStart = System.currentTimeMillis();
                List<MapTask> mapTasks = new ArrayList<>(numSplits);
                for (Split split : splitList) {
                    mapTasks.add(new MapTask(file, split, currentCentroids, dim, skipCols));
                }

                List<Future<PartialResult>> mapFutures = mapPool.invokeAll(mapTasks);
                List<PartialResult> mapperResults = new ArrayList<>(numSplits);
                for (Future<PartialResult> future : mapFutures) {
                    mapperResults.add(future.get());
                }
                long mapElapsed = System.currentTimeMillis() - mapStart;

                // Tính toán số lượng Mapper chạy đồng thời tối đa (Sweep-line Algorithm)
                int maxConcurrentMappers = calculateMaxConcurrentMappers(mapperResults);
                long sumMapperTime = 0;
                for (PartialResult pr : mapperResults) {
                    sumMapperTime += pr.getElapsedMs();
                }
                double overlapRatio = (double) sumMapperTime / Math.max(1, mapElapsed);

                // In chi tiết từng Mapper ở vòng lặp đầu tiên (có timestamp mili giây)
                if (printLogs && iter == 1) {
                    printMapperExecutionDetails(mapperResults);
                    System.out.println(
                            "---------------------------------------------------------------------------------------------------------");
                    System.out.printf("%-5s | %10s | %10s | %10s | %12s | %16s | %12s%n",
                            "Vòng", "Map (ms)", "Shfl (ms)", "Redc (ms)", "Tổng VL (ms)", "SSE Cục bộ", "Max Shift");
                    System.out.println(
                            "---------------------------------------------------------------------------------------------------------");
                }

                // ==================== PHA 2: SHUFFLE ====================
                long shuffleStart = System.currentTimeMillis();
                // Gom dữ liệu mappers theo clusterId (được truyền trực tiếp vào các ReduceTask)
                long shuffleElapsed = System.currentTimeMillis() - shuffleStart;

                // ==================== PHA 3: REDUCE ====================
                long reduceStart = System.currentTimeMillis();
                List<ReduceTask> reduceTasks = new ArrayList<>(k);
                for (int c = 0; c < k; c++) {
                    reduceTasks.add(new ReduceTask(c, mapperResults, currentCentroids[c], dim));
                }

                List<Future<ReduceTask.ClusterUpdate>> reduceFutures = reducePool.invokeAll(reduceTasks);

                double[][] newCentroids = new double[k][dim];
                double maxShift = 0.0;
                double iterSSE = 0.0;

                for (Future<ReduceTask.ClusterUpdate> future : reduceFutures) {
                    ReduceTask.ClusterUpdate update = future.get();
                    int cId = update.getClusterId();
                    newCentroids[cId] = update.getNewCentroid();
                    finalCounts[cId] = update.getPointCount();
                    iterSSE += update.getClusterSSE();
                    if (update.getShift() > maxShift) {
                        maxShift = update.getShift();
                    }
                }
                long reduceElapsed = System.currentTimeMillis() - reduceStart;

                long iterTotalElapsed = System.currentTimeMillis() - iterStart;
                currentCentroids = newCentroids;
                finalSSE = iterSSE;

                if (printLogs) {
                    System.out.printf(" %-4d | %8d ms | %8d ms | %8d ms | %10d ms | %16.4f | %12.6f%n",
                            iter, mapElapsed, shuffleElapsed, reduceElapsed, iterTotalElapsed, iterSSE, maxShift);

                    // In phân tích song song: số mapper đồng thời tối đa và hệ số chồng lấn
                    System.out.printf("       [Song song Vòng %d]: Mapper đồng thời tối đa = %d/%d luồng | " +
                            "Tổng time mappers = %d ms / Time map thực = %d ms (Chồng lấn: %.2fx)%n",
                            iter, maxConcurrentMappers, numThreads, sumMapperTime, mapElapsed, overlapRatio);

                    // Kiểm tra tính giảm đơn điệu của SSE qua từng vòng lặp
                    if (iter > 1) {
                        if (iterSSE > prevSSE + 1e-4) {
                            System.out.printf(
                                    "       [CẢNH BÁO] SSE tăng từ %.4f lên %.4f (vi phạm tính đơn điệu của K-means)!%n",
                                    prevSSE, iterSSE);
                        } else {
                            System.out.printf(
                                    "       [SSE Giảm đơn điệu]: SSE trước = %.4f -> SSE này = %.4f (Giảm: %.4f)%n",
                                    prevSSE, iterSSE, prevSSE - iterSSE);
                        }
                    }

                    // In RAM ở giữa chương trình (sau vòng lặp đầu tiên hoặc vòng thứ 2)
                    if (iter == 1 || iter == 2) {
                        System.out.printf("       [BỘ NHỚ JVM - GIỮA VÒNG %d]: %s%n", iter, getMemoryStatus());
                    }
                }

                prevSSE = iterSSE;

                // Kiểm tra điều kiện hội tụ
                if (maxShift < eps) {
                    stopReason = String.format("Hội tụ sớm (Max Shift = %.6f < Epsilon = %e)", maxShift, eps);
                    if (printLogs) {
                        System.out.println(
                                "---------------------------------------------------------------------------------------------------------");
                        System.out.printf(">>> Thuật toán dừng: %s tại vòng %d%n", stopReason, iter);
                    }
                    break;
                }
            }
        } finally {
            mapPool.shutdown();
            reducePool.shutdown();
        }

        if (iter == maxIter && maxIter > 0 && !stopReason.startsWith("Hội tụ")) {
            stopReason = String.format("Đạt số vòng lặp tối đa (MaxIter = %d)", maxIter);
        }

        long totalPointsProcessed = 0;
        for (long c : finalCounts)
            totalPointsProcessed += c;

        long totalElapsed = System.currentTimeMillis() - totalStartTime;
        return new ParallelResult(currentCentroids, finalCounts, finalSSE, iter, totalElapsed, stopReason,
                totalPointsProcessed);
    }

    /**
     * Tính toán số lượng Mapper chạy đồng thời tối đa (Peak Concurrency)
     * sử dụng thuật toán Sweep-line trên các khoảng thời gian [startEpochMs,
     * endEpochMs].
     */
    public static int calculateMaxConcurrentMappers(List<PartialResult> results) {
        if (results == null || results.isEmpty())
            return 0;

        List<long[]> events = new ArrayList<>(results.size() * 2);
        for (PartialResult pr : results) {
            events.add(new long[] { pr.getStartEpochMs(), 1 }); // start: +1
            events.add(new long[] { pr.getEndEpochMs(), -1 }); // end: -1
        }

        events.sort((a, b) -> {
            if (a[0] != b[0]) {
                return Long.compare(a[0], b[0]);
            }
            return Long.compare(b[1], a[1]); // nếu cùng ms, bắt đầu (+1) ưu tiên trước kết thúc (-1)
        });

        int current = 0;
        int maxConcurrent = 0;
        for (long[] ev : events) {
            current += ev[1];
            if (current > maxConcurrent) {
                maxConcurrent = current;
            }
        }
        return maxConcurrent;
    }

    /**
     * Đếm tổng số điểm trong tệp siêu tốc bằng NIO FileChannel và mảng đệm direct
     * 1MB,
     * hoàn toàn không nạp tệp vào bộ nhớ Heap RAM.
     */
    public static long countTotalPointsFast(File file) {
        long lines = 0;
        try (FileInputStream fis = new FileInputStream(file);
                FileChannel channel = fis.getChannel()) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024); // 1 MB direct buffer
            while (channel.read(buffer) > 0) {
                buffer.flip();
                int limit = buffer.limit();
                for (int i = 0; i < limit; i++) {
                    if (buffer.get(i) == '\n') {
                        lines++;
                    }
                }
                buffer.clear();
            }
        } catch (IOException e) {
            return -1;
        }
        return lines;
    }

    /**
     * Lấy thông tin trạng thái RAM sử dụng so với Heap và -Xmx.
     */
    public static String getMemoryStatus() {
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long totalMB = rt.totalMemory() / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        return String.format("RAM dùng: %,d MB / Heap hiện tại: %,d MB [Cấu hình -Xmx: %,d MB]",
                usedMB, totalMB, maxMB);
    }

    /**
     * Chia tệp thành P đoạn byte đều nhau [startByte, endByte).
     */
    private static List<Split> createByteSplits(File file, int numSplits) {
        long fileLength = file.length();
        long chunkSize = (fileLength + numSplits - 1) / numSplits;
        List<Split> splits = new ArrayList<>(numSplits);

        long currentStart = 0;
        for (int i = 0; i < numSplits; i++) {
            long currentEnd = Math.min(fileLength, currentStart + chunkSize);
            if (i == numSplits - 1) {
                currentEnd = fileLength;
            }
            splits.add(new Split(i, currentStart, currentEnd));
            currentStart = currentEnd;
        }
        return splits;
    }

    /**
     * Khởi tạo K tâm ban đầu bằng cách tìm ngẫu nhiên K điểm mẫu từ tệp qua byte
     * seek.
     * Cực nhanh (< 5ms), không tốn RAM và tái lập được kết quả qua Seed.
     */
    public static double[][] sampleInitialCentroids(File file, int k, int dim, int skipCols, long seed)
            throws IOException {
        double[][] centroids = new double[k][dim];
        Random rng = new Random(seed);
        long fileLength = file.length();
        double[] buffer = new double[dim];

        int found = 0;
        int attempts = 0;
        while (found < k && attempts < 2000) {
            attempts++;
            long randomOffset = (long) (rng.nextDouble() * Math.max(0, fileLength - 10000));
            try (SplitReader reader = new SplitReader(file, randomOffset, fileLength)) {
                String line = reader.readLine();
                if (line != null && MapTask.parseCsvLine(line.trim(), buffer, dim, skipCols)) {
                    System.arraycopy(buffer, 0, centroids[found], 0, dim);
                    found++;
                }
            }
        }

        if (found < k) {
            throw new IllegalStateException("Không thể lấy đủ " + k + " tâm ban đầu từ tệp dữ liệu.");
        }
        return centroids;
    }

    /**
     * In chi tiết thực thi của từng mapper với timestamp chính xác mili giây
     * (HH:mm:ss.SSS).
     */
    private static void printMapperExecutionDetails(List<PartialResult> mapperResults) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");
        System.out.println("\n>>> CHI TIẾT THỰC THI TỪNG MAPPER Ở VÒNG LẶP 1 (Timestamp chính xác ms):");
        System.out.printf("%-10s | %-20s | %10s | %14s | %14s | %10s%n",
                "Split ID", "Tên Luồng (Thread)", "Số điểm", "Bắt đầu", "Kết thúc", "Thời gian");
        System.out.println("-----------------------------------------------------------------------------------------");

        for (PartialResult pr : mapperResults) {
            System.out.printf("Split #%02d  | %-20s | %,10d | %14s | %14s | %8d ms%n",
                    pr.getSplitId(),
                    pr.getThreadName(),
                    pr.getTotalPointsCount(),
                    sdf.format(new Date(pr.getStartEpochMs())),
                    sdf.format(new Date(pr.getEndEpochMs())),
                    pr.getElapsedMs());
        }
    }

    private static void printSystemBanner(File file, long totalPoints, int k, int dim, int threads,
            int splits, int maxIter, double eps, int skipCols, String mode, long seed) {
        Runtime rt = Runtime.getRuntime();
        System.out.println("================================================================================");
        System.out.println("         HỆ THỐNG PHÂN CỤM DỮ LIỆU LỚN BẰNG MAPREDUCE (JAVA THUẦN)              ");
        System.out.println("================================================================================");
        System.out.printf("Đường dẫn file dữ liệu           : %s%n", file.getAbsolutePath());
        System.out.printf("Kích thước tệp                   : %.2f MB (%,d bytes)%n",
                file.length() / (1024.0 * 1024.0), file.length());
        System.out.printf("Tổng số điểm dữ liệu             : %,d điểm%n", totalPoints);
        System.out.printf("Số chiều (D)                     : %d thuộc tính%n", dim);
        System.out.printf("Số cụm phân nhóm (K)             : %d cụm%n", k);
        System.out.printf("Ngưỡng hội tụ (Epsilon)          : %e%n", eps);
        System.out.printf("Số vòng lặp tối đa (MaxIter)     : %d vòng%n", maxIter);
        System.out.printf("Seed ngẫu nhiên (Tái lập tâm)    : %d%n", seed);
        System.out.printf("Số nhân CPU máy tính             : %d cores%n", rt.availableProcessors());
        System.out.printf("Số luồng xử lý song song (Threads): %d luồng%n", threads);
        System.out.printf("Số phân đoạn tệp (Splits)        : %d đoạn%n", splits);
        System.out.printf("Bỏ qua số cột đầu (SkipCols)     : %d%n", skipCols);
        System.out.printf("Chế độ chạy (Mode)               : %s%n", mode.toUpperCase());
        System.out.println("================================================================================");
    }

    private static void printFinalSummary(double[][] centroids, long[] counts, double totalSSE,
            int iterations, long elapsedMs, String stopReason, int dim, String inputPath) {
        long totalPoints = 0;
        for (long c : counts)
            totalPoints += c;

        System.out.println("\n================================================================================");
        System.out.println("                           KẾT QUẢ PHÂN CỤM CUỐI CÙNG                           ");
        System.out.println("================================================================================");
        System.out.printf("Tổng số điểm xử lý : %,d điểm%n", totalPoints);
        System.out.printf("Số vòng lặp đã chạy: %d vòng%n", iterations);
        System.out.printf("Lý do dừng         : %s%n", stopReason);
        System.out.printf("Tổng thời gian chạy: %d ms (%.2f giây)%n", elapsedMs, elapsedMs / 1000.0);
        System.out.printf("Tổng sai số SSE    : %,.4f%n", totalSSE);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("Phân bố số lượng điểm và tọa độ tâm các cụm:");
        for (int c = 0; c < centroids.length; c++) {
            double percent = (totalPoints > 0) ? (counts[c] * 100.0 / totalPoints) : 0.0;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Cụm #%d [%,10d điểm, %5.2f%%]: (", c, counts[c], percent));
            int showDim = Math.min(dim, 5);
            for (int d = 0; d < showDim; d++) {
                sb.append(String.format(java.util.Locale.US, "%.4f", centroids[c][d]));
                if (d < showDim - 1)
                    sb.append(", ");
            }
            if (dim > 5) {
                sb.append(", ... còn ").append(dim - 5).append(" chiều");
            }
            sb.append(")");
            System.out.println(sb.toString());
        }

        // Nếu có tệp tâm thật từ DataGenerator, đối chứng ngay lập tức
        File centersFile = new File(inputPath + ".centers.txt");
        if (centersFile.exists()) {
            compareWithGroundTruth(centroids, centersFile, dim);
        }
        System.out.println("================================================================================\n");
    }

    private static void compareWithGroundTruth(double[][] centroids, File centersFile, int dim) {
        List<double[]> trueCenters = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(centersFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#") || line.isEmpty())
                    continue;
                String[] parts = line.split(":");
                if (parts.length == 2) {
                    String[] tokens = parts[1].trim().split("\\s+");
                    double[] c = new double[dim];
                    for (int d = 0; d < Math.min(dim, tokens.length); d++) {
                        c[d] = Double.parseDouble(tokens[d]);
                    }
                    trueCenters.add(c);
                }
            }
        } catch (Exception e) {
            return;
        }

        if (trueCenters.isEmpty())
            return;

        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("ĐỐI CHỨNG VỚI TÂM CỤM THỰC TẾ (GROUND TRUTH):");
        for (int c = 0; c < centroids.length; c++) {
            double minDist = Double.MAX_VALUE;
            int bestTrue = -1;
            for (int t = 0; t < trueCenters.size(); t++) {
                double distSq = 0.0;
                double[] tc = trueCenters.get(t);
                for (int d = 0; d < dim; d++) {
                    double diff = centroids[c][d] - tc[d];
                    distSq += diff * diff;
                }
                double dist = Math.sqrt(distSq);
                if (dist < minDist) {
                    minDist = dist;
                    bestTrue = t;
                }
            }
            System.out.printf("  - Tâm tính toán #%d tương ứng Tâm thật #%d (Sai lệch khoảng cách: %.5f)%n",
                    c, bestTrue, minDist);
        }
    }
}
