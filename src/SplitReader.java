import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * SplitReader: Bộ đọc luồng (streaming) cho từng phân đoạn tệp theo byte offset.
 * Đảm bảo:
 * 1. Không nạp toàn bộ tệp vào RAM, dùng bộ đệm 64KB.
 * 2. Căn chỉnh chính xác ranh giới dòng (line boundary):
 *    - Nếu startByte > 0, bỏ qua phần còn lại của dòng đang dở dang (do split trước đã đọc).
 *    - Nếu dòng bắt đầu trước endByte, đọc hết toàn bộ dòng đó dù nó tràn qua endByte.
 *    - Dừng đọc khi bắt đầu một dòng mới có vị trí byte >= endByte.
 * 3. Hỗ trợ cả định dạng kết thúc dòng chuẩn Unix (\n) và Windows (\r\n).
 */
public class SplitReader implements Closeable {
    private final RandomAccessFile raf;
    private final long endByte;
    private final byte[] buffer = new byte[65536];
    private int bufferPos = 0;
    private int bufferLimit = 0;
    private long currentByteOffset;
    private boolean reachedEnd = false;

    public SplitReader(File file, long startByte, long endByte) throws IOException {
        this.raf = new RandomAccessFile(file, "r");
        this.endByte = endByte;
        this.currentByteOffset = startByte;
        this.raf.seek(startByte);

        // Căn chỉnh ranh giới dòng ban đầu nếu không phải bắt đầu từ đầu tệp (byte 0)
        alignStartBoundary(startByte);
    }

    private void alignStartBoundary(long startByte) throws IOException {
        if (startByte == 0) {
            return;
        }

        // Kiểm tra byte ngay trước startByte
        raf.seek(startByte - 1);
        int prevByte = raf.read();
        raf.seek(startByte); // quay lại vị trí startByte

        if (prevByte == '\n') {
            // startByte rơi đúng vào đầu dòng mới -> Không cần bỏ qua dòng nào
            return;
        } else if (prevByte == '\r') {
            // Có thể startByte là '\n' trong cặp '\r\n'
            int currentByte = readByteRaw();
            if (currentByte == '\n') {
                // '\n' thuộc về dòng trước, dòng mới bắt đầu ngay sau byte này
                return;
            } else if (currentByte == -1) {
                reachedEnd = true;
                return;
            } else {
                // Không phải '\n', nghĩa là ký tự đầu của dòng mới hoặc dòng dở dang
                skipRemainingOfCurrentLine();
            }
        } else {
            // startByte nằm ở giữa một dòng -> bỏ qua phần còn lại của dòng này
            skipRemainingOfCurrentLine();
        }
    }

    private void skipRemainingOfCurrentLine() throws IOException {
        while (true) {
            int b = readByteRaw();
            if (b == -1) {
                reachedEnd = true;
                break;
            }
            if (b == '\n') {
                break;
            }
        }
    }

    private int readByteRaw() throws IOException {
        if (bufferPos >= bufferLimit) {
            bufferLimit = raf.read(buffer);
            bufferPos = 0;
            if (bufferLimit <= 0) {
                return -1;
            }
        }
        currentByteOffset++;
        return buffer[bufferPos++] & 0xFF;
    }

    /**
     * Đọc dòng tiếp theo thuộc split này.
     * Trả về null khi hết dòng trong split hoặc chạm EOF.
     */
    public String readLine() throws IOException {
        if (reachedEnd) {
            return null;
        }

        // Vị trí bắt đầu của dòng chuẩn bị đọc
        long lineStartOffset = currentByteOffset;
        if (lineStartOffset >= endByte) {
            reachedEnd = true;
            return null;
        }

        StringBuilder sb = new StringBuilder(128);
        while (true) {
            int b = readByteRaw();
            if (b == -1) {
                reachedEnd = true;
                return sb.length() > 0 ? sb.toString() : null;
            }
            if (b == '\n') {
                // Gặp kết thúc dòng, trả về nội dung dòng
                break;
            }
            if (b != '\r') {
                sb.append((char) b);
            }
        }
        return sb.toString();
    }

    @Override
    public void close() throws IOException {
        raf.close();
    }
}

