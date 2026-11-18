
public class Split {
    private final int splitIndex;
    private final long startByte;
    private final long endByte;

    public Split(int splitIndex, long startByte, long endByte) {
        this.splitIndex = splitIndex;
        this.startByte = startByte;
        this.endByte = endByte;
    }

    public int getSplitIndex() {
        return splitIndex;
    }

    public long getStartByte() {
        return startByte;
    }

    public long getEndByte() {
        return endByte;
    }

    public long getLength() {
        return endByte - startByte;
    }

    @Override
    public String toString() {
        return String.format("Split #%02d [%d - %d] (%.2f MB)",
                splitIndex, startByte, endByte, getLength() / (1024.0 * 1024.0));
    }
}
