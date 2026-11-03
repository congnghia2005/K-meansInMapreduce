import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

/**
 * DataGenerator: Chương trình sinh tập dữ liệu lớn tổng hợp (Synthetic Dataset)
 * theo mô hình phân phối hỗn hợp Gaussian (Gaussian Mixture Model).
 *
 * Tính năng:
 * - Tham số: N điểm, D chiều, K cụm thật, độ lệch chuẩn (stddev), seed ngẫu nhiên.
 * - Ghi trực tiếp ra file CSV qua BufferedWriter dạng streaming, KHÔNG nạp vào RAM.
 * - Cho phép sinh từ hàng triệu đến chục triệu điểm (file hàng GB) mà bộ nhớ RAM chỉ tốn vài MB.
 * - Tự động xuất file <output>.centers.txt chứa tâm cụm thực tế (Ground Truth) để đối chứng.
 */
public class DataGenerator {

    public static void main(String[] args) {
        String outputPath = "points.csv";
        long numPoints = 1_000_000L;
        int dim = 10;
        int k = 5;
        double stddev = 1.5;
        long seed = 42L;
        boolean withLabels = false;

        // Phân tích tham số dòng lệnh
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--output":
                    outputPath = args[++i];
                    break;
                case "--n":
                    numPoints = Long.parseLong(args[++i]);
                    break;
                case "--dim":
                    dim = Integer.parseInt(args[++i]);
                    break;
                case "--k":
                    k = Integer.parseInt(args[++i]);
                    break;
                case "--stddev":
                    stddev = Double.parseDouble(args[++i]);
                    break;
                case "--seed":
                    seed = Long.parseLong(args[++i]);
                    break;
                case "--withLabels":
                    withLabels = true;
                    break;
                default:
                    System.err.println("Cảnh báo: Bỏ qua tham số không xác định: " + args[i]);
            }
        }

        System.out.println("================================================================================");
        System.out.println("                 TRÌNH SINH DỮ LIỆU TỔNG HỢP (DATA GENERATOR)                  ");
        System.out.println("================================================================================");
        System.out.printf("File xuất        : %s%n", outputPath);
        System.out.printf("Số lượng điểm (N): %,d%n", numPoints);
        System.out.printf("Số chiều (D)     : %d%n", dim);
        System.out.printf("Số cụm thật (K)  : %d%n", k);
        System.out.printf("Độ lệch chuẩn    : %.2f%n", stddev);
        System.out.printf("Seed ngẫu nhiên  : %d%n", seed);
        System.out.printf("Kèm cột nhãn     : %s%n", withLabels ? "Có (cột 0)" : "Không");
        System.out.println("--------------------------------------------------------------------------------");

        Random rng = new Random(seed);

        // Sinh K tâm cụm thực tế trong miền [-10.0, 10.0]^D
        double[][] trueCenters = new double[k][dim];
        for (int c = 0; c < k; c++) {
            for (int d = 0; d < dim; d++) {
                trueCenters[c][d] = -10.0 + rng.nextDouble() * 20.0;
            }
        }

        // Lưu tâm thật vào file đối chứng
        String centersPath = outputPath + ".centers.txt";
        saveTrueCenters(centersPath, trueCenters, dim);
        System.out.printf("Đã lưu tâm cụm thực tế vào: %s%n", centersPath);

        long startMs = System.currentTimeMillis();
        long writtenPoints = 0;
        int logStep = (int) Math.max(100_000, numPoints / 10);

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputPath), 128 * 1024)) {
            StringBuilder sb = new StringBuilder(dim * 16);

            for (long i = 0; i < numPoints; i++) {
                int cluster = rng.nextInt(k);
                double[] center = trueCenters[cluster];

                sb.setLength(0);
                if (withLabels) {
                    sb.append(cluster).append(",");
                }

                for (int d = 0; d < dim; d++) {
                    // Tọa độ điểm = Tâm cụm + Phân phối Gauss(0, stddev^2)
                    double val = center[d] + rng.nextGaussian() * stddev;
                    sb.append(String.format(java.util.Locale.US, "%.5f", val));
                    if (d < dim - 1) {
                        sb.append(",");
                    }
                }
                sb.append("\n");
                bw.write(sb.toString());

                writtenPoints++;
                if (writtenPoints % logStep == 0 || writtenPoints == numPoints) {
                    double progress = (writtenPoints * 100.0) / numPoints;
                    long elapsed = Math.max(1, System.currentTimeMillis() - startMs);
                    double rate = (writtenPoints * 1000.0) / elapsed;
                    System.out.printf("Tiến độ: %5.1f%% (%,d / %,d điểm) - Tốc độ: %,.0f điểm/giây%n",
                            progress, writtenPoints, numPoints, rate);
                }
            }
        } catch (IOException e) {
            System.err.println("Lỗi khi ghi tệp: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        long totalTime = System.currentTimeMillis() - startMs;
        File outFile = new File(outputPath);
        double sizeMB = outFile.length() / (1024.0 * 1024.0);

        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf("Sinh dữ liệu thành công!%n");
        System.out.printf("Kích thước tệp: %.2f MB (%,d bytes)%n", sizeMB, outFile.length());
        System.out.printf("Tổng thời gian: %d ms (%.2f giây)%n", totalTime, totalTime / 1000.0);
        System.out.println("================================================================================\n");
    }

    private static void saveTrueCenters(String path, double[][] centers, int dim) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(path))) {
            bw.write("# True Cluster Centers (Ground Truth)\n");
            bw.write("# ClusterId," + "dim_0 ... dim_" + (dim - 1) + "\n");
            for (int c = 0; c < centers.length; c++) {
                StringBuilder sb = new StringBuilder();
                sb.append("Cluster_").append(c).append(":");
                for (int d = 0; d < dim; d++) {
                    sb.append(String.format(java.util.Locale.US, " %.5f", centers[c][d]));
                }
                sb.append("\n");
                bw.write(sb.toString());
            }
        } catch (IOException e) {
            System.err.println("Không thể lưu tâm thật: " + e.getMessage());
        }
    }
}

