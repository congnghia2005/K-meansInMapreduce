import java.util.Arrays;

/**
 * PartialResult: Kết quả cộng dồn cục bộ của bộ kết hợp (In-mapper Combiner).
 * Thay vì phát ra từng cặp (clusterId, point) gây nghẽn Shuffle và tràn bộ nhớ RAM,
 * mỗi mapper giữ một đối tượng PartialResult riêng biệt để tích lũy:
 * - Tổng vector tọa độ các điểm thuộc mỗi cụm (clusterSums).
 * - Số lượng điểm thuộc mỗi cụm (clusterCounts).
 * - Sai số bình phương Euclid cục bộ (clusterSSE).
 *
 * Nhờ việc mỗi luồng sở hữu một vùng nhớ độc lập (thread-confined), hoàn toàn KHÔNG
 * có tranh chấp tài nguyên (zero lock contention) và KHÔNG xảy ra hiện tượng False Sharing.
 */
public class PartialResult {
    private final int splitId;
    private final int k;
    private final int dim;

    private final double[][] clusterSums;
    private final long[] clusterCounts;
    private final double[] clusterSSE;
    private long totalPointsCount = 0;

    // Thông tin metadata phục vụ đo kiểm và phân tích luồng
    private String threadName;
    private long startEpochMs;
    private long endEpochMs;

    public PartialResult(int splitId, int k, int dim) {
        this.splitId = splitId;
        this.k = k;
        this.dim = dim;
        this.clusterSums = new double[k][dim];
        this.clusterCounts = new long[k];
        this.clusterSSE = new double[k];
    }

    /**
     * Tích lũy một điểm vào cụm gần nhất (In-mapper Combiner).
     */
    public void addPoint(int clusterId, double[] point, double distSq) {
        double[] sum = clusterSums[clusterId];
        for (int d = 0; d < dim; d++) {
            sum[d] += point[d];
        }
        clusterCounts[clusterId]++;
        clusterSSE[clusterId] += distSq;
        totalPointsCount++;
    }

    /**
     * Gộp kết quả từ một PartialResult khác vào kết quả này.
     */
    public void merge(PartialResult other) {
        for (int c = 0; c < k; c++) {
            this.clusterCounts[c] += other.clusterCounts[c];
            this.clusterSSE[c] += other.clusterSSE[c];
            double[] thisSum = this.clusterSums[c];
            double[] otherSum = other.clusterSums[c];
            for (int d = 0; d < dim; d++) {
                thisSum[d] += otherSum[d];
            }
        }
        this.totalPointsCount += other.totalPointsCount;
    }

    public void setExecutionMeta(String threadName, long startEpochMs, long endEpochMs) {
        this.threadName = threadName;
        this.startEpochMs = startEpochMs;
        this.endEpochMs = endEpochMs;
    }

    public int getSplitId() {
        return splitId;
    }

    public int getK() {
        return k;
    }

    public int getDim() {
        return dim;
    }

    public double[][] getClusterSums() {
        return clusterSums;
    }

    public long[] getClusterCounts() {
        return clusterCounts;
    }

    public double[] getClusterSSE() {
        return clusterSSE;
    }

    public double getTotalSSE() {
        double sum = 0.0;
        for (double s : clusterSSE) {
            sum += s;
        }
        return sum;
    }

    public long getTotalPointsCount() {
        return totalPointsCount;
    }

    public String getThreadName() {
        return threadName;
    }

    public long getStartEpochMs() {
        return startEpochMs;
    }

    public long getEndEpochMs() {
        return endEpochMs;
    }

    public long getElapsedMs() {
        return endEpochMs - startEpochMs;
    }
}

